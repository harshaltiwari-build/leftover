package app.leftover.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.leftover.ui.add.ScreenshotIntake
import java.io.File
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Attack on screenshot reading. Any app can share an image into Leftover, so its size can't be trusted: a picture
 * just under 6000 px a side used to be decoded whole (about 144 MB), and a huge file was copied whole into the cache.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotLimitsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `screenshots are never decoded past the pixel limit`() {
        listOf(5_999 to 5_999, 100_000 to 100_000, 1_080 to 30_000, 40_000 to 900).forEach { (w, h) ->
            val s = ScreenshotReader.sampleFor(w, h)
            assertTrue("$w x $h at 1/$s", (w / s).toLong() * (h / s) <= ScreenshotReader.MAX_DECODE_PIXELS)
        }
        // A normal phone screenshot is read at full size, for the sharpest text.
        assertTrue(ScreenshotReader.sampleFor(1_080, 2_400) == 1)
    }

    @Test fun `a shared file over the size limit is not copied`() {
        val f = File(context.cacheDir, "huge.png")
        f.outputStream().use { out -> val block = ByteArray(1024 * 1024); repeat(31) { out.write(block) } }
        assertNull(ScreenshotIntake.copyForReading(context, Uri.fromFile(f)))
        f.delete()
        assertTrue(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("ocr-") })
    }
}
