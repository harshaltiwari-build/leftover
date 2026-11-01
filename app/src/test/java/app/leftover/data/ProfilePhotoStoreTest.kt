package app.leftover.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The profile photo is kept on the phone, small and square; anything that isn't a photo is refused. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfilePhotoStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After fun tearDown() = ProfilePhotoStore.deleteAll(context)

    private fun picture(w: Int, h: Int): Uri {
        val f = File(context.cacheDir, "pick-$w-$h.png")
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 120, 40)) }
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(f)
    }

    @Test fun `a picked photo is stored square and small`() {
        val name = ProfilePhotoStore.importFrom(context, picture(3000, 2000))
        assertNotNull(name)
        assertTrue(ProfilePhotoStore.isSafeName(name!!))
        val stored = BitmapFactory.decodeFile(ProfilePhotoStore.file(context, name).path)
        assertEquals(stored.width, stored.height)
        assertEquals(ProfilePhotoStore.SIDE, stored.width)
    }

    @Test fun `a small photo is not blown up`() {
        val name = ProfilePhotoStore.importFrom(context, picture(200, 300))!!
        val stored = BitmapFactory.decodeFile(ProfilePhotoStore.file(context, name).path)
        assertEquals(200, stored.width)
        assertEquals(200, stored.height)
    }

    @Test fun `something that isn't an image is refused and leaves nothing behind`() {
        val f = File(context.cacheDir, "notes.png").apply { writeText("not a picture") }
        assertNull(ProfilePhotoStore.importFrom(context, Uri.fromFile(f)))
        assertTrue(ProfilePhotoStore.dir(context).listFiles().orEmpty().isEmpty())
    }

    @Test fun `only its own file names are read, so a tampered setting can't reach other files`() {
        assertFalse(ProfilePhotoStore.isSafeName("../databases/leftover.db"))
        assertFalse(ProfilePhotoStore.isSafeName("photo-1.jpg/../../x"))
        assertFalse(ProfilePhotoStore.isSafeName(""))
        assertTrue(ProfilePhotoStore.isSafeName("photo-1730000000000.jpg"))
        assertNull(ProfilePhotoStore.load(context, "../databases/leftover.db"))
    }

    @Test fun `changing the photo removes the old one`() {
        val first = ProfilePhotoStore.importFrom(context, picture(400, 400))!!
        Thread.sleep(2)
        val second = ProfilePhotoStore.importFrom(context, picture(500, 500))!!
        ProfilePhotoStore.keepOnly(context, second)
        assertFalse(ProfilePhotoStore.file(context, first).exists())
        assertTrue(ProfilePhotoStore.file(context, second).exists())
        ProfilePhotoStore.keepOnly(context, null)
        assertTrue(ProfilePhotoStore.dir(context).listFiles().orEmpty().isEmpty())
    }

    // Attack: a picture can claim huge dimensions while being a tiny file. Decoding it whole would run out of memory.
    @Test fun `huge or very long pictures are decoded small`() {
        listOf(100_000 to 100_000, 60_000 to 600, 600 to 60_000, 12_000 to 9_000).forEach { (w, h) ->
            val s = ProfilePhotoStore.sampleFor(w, h)
            assertTrue("$w x $h at 1/$s is ${(w / s).toLong() * (h / s)} px", (w / s).toLong() * (h / s) <= ProfilePhotoStore.MAX_DECODE_PIXELS)
        }
        // An ordinary photo is still decoded sharp enough for 512 px.
        assertEquals(2, ProfilePhotoStore.sampleFor(3000, 2000))
        assertEquals(1, ProfilePhotoStore.sampleFor(800, 600))
    }

    @Test fun `a file over the size limit is refused without being read whole`() {
        val f = File(context.cacheDir, "big.jpg")
        f.outputStream().use { out -> val block = ByteArray(1024 * 1024); repeat(31) { out.write(block) } }
        assertNull(ProfilePhotoStore.importFrom(context, Uri.fromFile(f)))
        assertTrue(ProfilePhotoStore.dir(context).listFiles().orEmpty().isEmpty())
        f.delete()
    }

    @Test fun `a cancelled camera leaves no file`() {
        val (name, _) = ProfilePhotoStore.cameraTarget(context)
        assertFalse(ProfilePhotoStore.finishCamera(context, name))
        assertFalse(ProfilePhotoStore.finishCamera(context, "../../shared_prefs/x.xml"))
        assertTrue(ProfilePhotoStore.dir(context).listFiles().orEmpty().isEmpty())
    }
}
