package app.leftover

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.leftover.data.HttpUpdateSource
import app.leftover.data.UpdateVerifier
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The real thing, on a phone: GitHub's live latest release is read, its file is downloaded and its checksum verified. */
@RunWith(AndroidJUnit4::class)
class RealUpdateTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun liveReleaseDownloadsAndVerifies(): Unit = runBlocking {
        val source = HttpUpdateSource()
        val info = source.latest()
        assertNotNull("GitHub's latest release wasn't usable (no checksum line, wrong file name or link?)", info)
        info!!
        assertTrue(info.version, info.version.matches(Regex("""\d+\.\d+\.\d+""")))
        assertEquals("leftover-${info.version}.apk", info.apkName)
        val dir = File(context.cacheDir, "real-update-test").apply { deleteRecursively(); mkdirs() }
        var last = 0L
        val file = source.download(info, dir) { done, _ -> last = done }
        // Reaching here means the size and the SHA-256 from the release notes both matched.
        assertEquals(info.sizeBytes, file.length())
        assertEquals(info.sizeBytes, last)
        // The package checker can read a real app file and its signing key (it's a different app id here, so it says so).
        val verdict = UpdateVerifier.check(context, file, info)
        assertNotNull(verdict)
        assertTrue(verdict!!, verdict.contains("isn't Leftover"))
        dir.deleteRecursively()
    }
}
