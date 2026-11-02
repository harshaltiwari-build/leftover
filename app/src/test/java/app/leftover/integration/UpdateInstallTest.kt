package app.leftover.integration

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.leftover.data.PackageFacts
import app.leftover.data.UpdateInstaller
import app.leftover.data.UpdateVerifier
import app.leftover.domain.ReleaseInfo
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A downloaded file must be Leftover, newer, and signed by the same key before Android is even asked to install it. */
@RunWith(AndroidJUnit4::class)
class UpdateInstallTest {
    private val info = ReleaseInfo("0.9.0", "", "leftover-0.9.0.apk", "https://github.com/x", 1, "a".repeat(64))
    private val mine = PackageFacts("io.github.yourorg.leftover", "0.8.2", 11, setOf("AA11"))
    private fun check(archive: PackageFacts?, installed: PackageFacts = mine) = UpdateVerifier.check(archive, installed, info)

    @Test fun `the right app, newer, same key passes`() =
        assertNull(check(PackageFacts("io.github.yourorg.leftover", "0.9.0", 12, setOf("AA11"))))

    @Test fun `every way a file can be wrong is named`() {
        assertTrue(check(null)!!.contains("isn't a valid app"))
        assertTrue(check(PackageFacts("com.evil.app", "0.9.0", 12, setOf("AA11")))!!.contains("isn't Leftover"))
        assertTrue(check(PackageFacts("io.github.yourorg.leftover", "0.9.5", 12, setOf("AA11")))!!.contains("doesn't match"))
        assertTrue(check(PackageFacts("io.github.yourorg.leftover", "0.9.0", 11, setOf("AA11")))!!.contains("not newer"))
        assertTrue(check(PackageFacts("io.github.yourorg.leftover", "0.9.0", 3, setOf("AA11")))!!.contains("not newer"))
    }

    @Test fun `a different signing key is explained, with what to do`() {
        val msg = check(PackageFacts("io.github.yourorg.leftover", "0.9.0", 12, setOf("BB22")))!!
        assertTrue(msg, msg.contains("signed differently"))
        assertTrue(msg, msg.contains("back up", true))
        assertNotNull(check(PackageFacts("io.github.yourorg.leftover", "0.9.0", 12, emptySet())))
    }

    // Attack: a file signed by several keys, one of them ours, must still be rejected unless every signer matches.
    @Test fun `any extra signer is rejected`() {
        assertNotNull(check(PackageFacts("io.github.yourorg.leftover", "0.9.0", 12, setOf("AA11", "EVIL"))))
    }

    @Test fun `installing asks Android's installer with a content link, never a file path`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "updates/leftover-0.9.0.apk").apply { parentFile!!.mkdirs(); writeText("apk") }
        val intent = UpdateInstaller.intent(context, file, canInstall = true)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("application/vnd.android.package-archive", intent.type)
        assertEquals("content", intent.data!!.scheme)
        assertEquals("${context.packageName}.files", intent.data!!.authority)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION == 0)
    }

    @Test fun `without permission to install, the person is sent to allow it for Leftover only`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = UpdateInstaller.intent(context, File(context.cacheDir, "updates/x.apk"), canInstall = false)
        assertEquals(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, intent.action)
        assertEquals("package:${context.packageName}", intent.data.toString())
    }
}
