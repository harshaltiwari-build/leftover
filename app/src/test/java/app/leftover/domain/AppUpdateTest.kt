package app.leftover.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Deciding whether a release is newer, reading what GitHub says about it, and which downloads are trusted. */
class AppUpdateTest {
    private val sha = "a".repeat(64)
    private fun release(
        tag: String = "v0.9.0", asset: String = "leftover-0.9.0.apk", url: String = "https://github.com/harshaltiwari-build/leftover/releases/download/v0.9.0/leftover-0.9.0.apk",
        body: String = "Notes\n\nSHA-256 of `leftover-0.9.0.apk`: `$sha`\n", size: Long = 20_000_000, draft: Boolean = false, pre: Boolean = false,
    ) = """{"tag_name":"$tag","draft":$draft,"prerelease":$pre,"body":${org.json.JSONObject.quote(body)},"assets":[{"name":"$asset","size":$size,"browser_download_url":"$url"}]}"""

    @Test fun `versions compare by number, not text`() {
        assertTrue(AppUpdate.isNewer("0.8.2", "v0.9.0"))
        assertTrue(AppUpdate.isNewer("0.8.2", "0.8.10"))
        assertTrue(AppUpdate.isNewer("0.8.2-qa", "0.8.3"))
        assertFalse(AppUpdate.isNewer("0.8.2", "v0.8.2"))
        assertFalse(AppUpdate.isNewer("0.9.0", "v0.8.9"))
        assertFalse(AppUpdate.isNewer("0.9.0", "garbage"))
        assertFalse(AppUpdate.isNewer("0.9.0", ""))
        assertTrue(AppUpdate.isNewer("0.9.0", "1.0"))
        assertFalse(AppUpdate.isNewer("1.0.0", "1.0"))
    }

    @Test fun `a good release is read in full`() {
        val r = AppUpdate.parse(release())!!
        assertEquals("0.9.0", r.version)
        assertEquals("leftover-0.9.0.apk", r.apkName)
        assertEquals(20_000_000L, r.sizeBytes)
        assertEquals(sha, r.sha256)
        assertTrue(r.notes.startsWith("Notes"))
    }

    @Test fun `drafts and pre-releases are never offered`() {
        assertNull(AppUpdate.parse(release(draft = true)))
        assertNull(AppUpdate.parse(release(pre = true)))
    }

    @Test fun `a release with no checksum in its notes is refused`() {
        assertNull(AppUpdate.parse(release(body = "No checksum here")))
        assertNull(AppUpdate.parse(release(body = "SHA-256 of `leftover-0.9.0.apk`: `${"z".repeat(64)}`")))
        assertNull(AppUpdate.parse(release(body = "SHA-256 of `leftover-0.9.0.apk`: `${"a".repeat(63)}`")))
        // A checksum for some other file doesn't count.
        assertNull(AppUpdate.parse(release(body = "SHA-256 of `other.apk`: `$sha`")))
    }

    @Test fun `only the app's own file name is accepted`() {
        assertNull(AppUpdate.parse(release(asset = "evil.apk")))
        assertNull(AppUpdate.parse(release(asset = "../../leftover-0.9.0.apk")))
        assertNull(AppUpdate.parse(release(asset = "leftover-0.9.0.apk.exe")))
        assertNull(AppUpdate.parse(release(asset = "leftover-0.9.1.apk")))
        assertNull(AppUpdate.parse(release(tag = "v0.9.0; rm -rf", asset = "leftover-0.9.0; rm -rf.apk")))
    }

    @Test fun `only trusted https links are followed`() {
        val ok = listOf(
            "https://github.com/harshaltiwari-build/leftover/releases/download/v0.9.0/leftover-0.9.0.apk",
            "https://objects.githubusercontent.com/github-production-release-asset-2e65be/1/2?x=y",
            "https://release-assets.githubusercontent.com/github-production-release-asset/1/2?x=y",
        )
        ok.forEach { assertTrue(it, UpdatePolicy.Strict.allows(it)) }
        val bad = listOf(
            "http://github.com/harshaltiwari-build/leftover/releases/download/v0.9.0/leftover-0.9.0.apk",
            "https://github.com.evil.com/leftover.apk",
            "https://evilgithub.com/leftover.apk",
            "https://github.com@evil.com/leftover.apk",
            "https://evil.com/github.com/leftover.apk",
            "https://github.com:8443/leftover.apk",
            "https://githubusercontent.com.evil.com/x",
            "ftp://github.com/x", "file:///sdcard/x.apk", "javascript:alert(1)", "", "github.com/x", "https://",
            "https://objects.githubusercontent.com.evil.io/x",
        )
        bad.forEach { assertFalse(it, UpdatePolicy.Strict.allows(it)) }
    }

    @Test fun `a link in a release that points anywhere else makes the whole release unusable`() {
        assertNull(AppUpdate.parse(release(url = "https://evil.com/leftover-0.9.0.apk")))
        assertNull(AppUpdate.parse(release(url = "http://github.com/x/leftover-0.9.0.apk")))
    }

    @Test fun `absurd sizes and hostile text are contained`() {
        assertNull(AppUpdate.parse(release(size = 500_000_000)))
        assertNull(AppUpdate.parse(release(size = 0)))
        assertNull(AppUpdate.parse(release(size = -5)))
        val big = AppUpdate.parse(release(body = "x".repeat(1_000_000) + "\nSHA-256 of `leftover-0.9.0.apk`: `$sha`"))
        // The notes shown are capped; the checksum line is still found.
        assertTrue(big == null || big.notes.length <= AppUpdate.MAX_NOTES)
    }

    @Test fun `broken or surprising json never throws`() {
        listOf("", "{", "null", "[]", "{\"tag_name\":5}", "{\"assets\":\"no\"}", "{\"tag_name\":\"v1\",\"assets\":[{}]}", "<html>rate limited</html>", "{\"message\":\"API rate limit exceeded\"}")
            .forEach { assertNull(it, AppUpdate.parse(it)) }
    }

    @Test fun `the notes shown are plain text without markup`() {
        val r = AppUpdate.parse(release(body = "<script>alert(1)</script>**Hi**\n\nSHA-256 of `leftover-0.9.0.apk`: `$sha`\n"))!!
        assertFalse(r.notes.contains("<"))
    }
}
