package app.leftover.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Attacks on the update links and the screenshot reader, each stating what was tried. */
class AttackTest {
    // Tried: look-alike letters, trailing dots, header injection, bare and doubled domains, odd ports, encoded dots.
    @Test fun `look-alike and smuggled addresses are all refused`() {
        listOf(
            "https://gіthub.com/x",                                  // Cyrillic і
            "https://github.com./x",                                 // trailing dot
            "https://github.com/x\r\nHost: evil.com",                // header injection
            "https://github.com\\@evil.com/x",                       // backslash trick
            "https://github.com%2eevil.com/x",
            "https://githubusercontent.com/x",                       // bare, no subdomain
            "https://.githubusercontent.com/x",                      // empty subdomain
            "https://x.githubusercontent.com.evil.com/x",
            "https://github.com:444/x", "https://github.com:0/x", "https://github.com:80/x",
            "https://user:pass@github.com/x",
            "https://[::1]/x", "https://127.0.0.1/x", "https://localhost/x", "https://192.168.0.1/x",
            "https://github.com /x", " https://github.com/x", "https://github.com/x ",
            "HTTPS://evil.com/github.com", "//github.com/x", "data:text/html,github.com",
            "https://" + "a".repeat(5_000) + ".githubusercontent.com/x",
        ).forEach { assertFalse("accepted: ${it.take(60)}", UpdatePolicy.Strict.allows(it)) }
        // Normal variants still work.
        assertTrue(UpdatePolicy.Strict.allows("https://GITHUB.com/x"))
        assertTrue(UpdatePolicy.Strict.allows("https://github.com:443/x"))
    }

    // Tried: a version tag crafted to break out of the file name or the URL.
    @Test fun `a hostile tag can't shape the file name or link`() {
        val sha = "a".repeat(64)
        listOf("v0.9.0/../../x", "v0.9.0%00", "v0.9.0\n", "v0.9", "v 0.9.0", "v0.9.0-evil", "v${"9".repeat(500)}.0.0").forEach { tag ->
            val body = org.json.JSONObject.quote("SHA-256 of `leftover-${tag.removePrefix("v")}.apk`: `$sha`")
            val json = """{"tag_name":${org.json.JSONObject.quote(tag)},"body":$body,"assets":[{"name":${org.json.JSONObject.quote("leftover-${tag.removePrefix("v")}.apk")},"size":10,"browser_download_url":"https://github.com/harshaltiwari-build/leftover/releases/download/$tag/leftover-${tag.removePrefix("v")}.apk"}]}"""
            val r = AppUpdate.parse(json)
            // v0.9 is a valid two-part version; everything else must be refused.
            if (tag == "v0.9") assertEquals("0.9", r?.version) else assertNull(tag, r)
        }
    }

    // Tried: a release that lists the right file twice, the second pointing elsewhere.
    @Test fun `a duplicate asset can't swap the link`() {
        val sha = "a".repeat(64)
        val body = org.json.JSONObject.quote("SHA-256 of `leftover-0.9.0.apk`: `$sha`")
        val good = """{"name":"leftover-0.9.0.apk","size":10,"browser_download_url":"https://github.com/harshaltiwari-build/leftover/releases/download/v0.9.0/leftover-0.9.0.apk"}"""
        val evil = """{"name":"leftover-0.9.0.apk","size":10,"browser_download_url":"https://evil.com/leftover-0.9.0.apk"}"""
        // The first match is judged; an evil first entry means the whole release is refused, never "skip to the next one".
        assertNull(AppUpdate.parse("""{"tag_name":"v0.9.0","body":$body,"assets":[$evil,$good]}"""))
        assertEquals("0.9.0", AppUpdate.parse("""{"tag_name":"v0.9.0","body":$body,"assets":[$good,$evil]}""")?.version)
    }

    // Tried: enormous or pathological receipt text, to make the reader hang.
    @Test fun `hostile screenshot text is read in bounded time`() {
        val today = LocalDate.of(2026, 11, 2)
        val nasty = listOf(
            "₹" + "9".repeat(200_000), "1," .repeat(100_000), ("To " + "a".repeat(50) + "\n").repeat(20_000), "\n".repeat(500_000),
            ("31 Oct 2026, 10:" + "5".repeat(10) + " pm\n").repeat(20_000), "UPI transaction ID\n" + "1".repeat(300_000), "*" + "1,".repeat(100_000),
            (("+91\n" + "1234\n").repeat(20_000)), "Rs." + " ".repeat(300_000) + "5",
        )
        nasty.forEach { text ->
            val start = System.nanoTime()
            runCatching { PaymentScreenshotParser.parse(text, today) }
            val ms = (System.nanoTime() - start) / 1_000_000
            assertTrue("took $ms ms on ${text.take(20)}", ms < 3_000)
        }
    }

    // Tried: absurd amounts in a receipt must never become an entry for billions.
    @Test fun `absurd amounts are refused not saved`() {
        assertNull(PaymentScreenshotParser.parse("To Rohan\n₹99,99,99,99,99,999\nCompleted", LocalDate.of(2026, 11, 2)).amount)
        assertNull(PaymentScreenshotParser.parse("To Rohan\n₹0\nCompleted", LocalDate.of(2026, 11, 2)).amount)
        assertNull(PaymentScreenshotParser.parse("To Rohan\n₹-500\nCompleted", LocalDate.of(2026, 11, 2)).amount)
    }
}
