package app.leftover.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Your profile photo, kept in the app's private `profile` folder as one small square JPEG. It never leaves the phone
 * and isn't part of backup files. Only names this store made are ever read, so a tampered setting can't point it at
 * another file.
 */
object ProfilePhotoStore {
    /** Stored photos are at most this many pixels a side. */
    const val SIDE = 512
    /** Pictures bigger than this are refused rather than read into memory. */
    private const val MAX_BYTES = 30L * 1024 * 1024
    /** Never decode more than this many pixels, whatever size a picture claims to be (16 MB of memory). */
    const val MAX_DECODE_PIXELS = 4_000_000L
    private val NAME = Regex("^photo-\\d{1,19}\\.jpg$")

    fun isSafeName(name: String): Boolean = NAME.matches(name)

    fun dir(context: Context): File = File(context.filesDir, "profile").apply { mkdirs() }

    fun file(context: Context, name: String): File {
        require(isSafeName(name)) { "not a profile photo name" }
        return File(dir(context), name)
    }

    private fun freshName(context: Context): String {
        var stamp = System.currentTimeMillis()
        while (File(dir(context), "photo-$stamp.jpg").exists()) stamp++
        return "photo-$stamp.jpg"
    }

    /** Copies a picked picture in, cropped square from the middle, upright and small. Null when it isn't a usable picture. */
    fun importFrom(context: Context, uri: Uri): String? {
        val name = freshName(context)
        val out = file(context, name)
        return runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = input.readNBytes((MAX_BYTES + 1).toInt())
                if (buffer.size > MAX_BYTES) return null
                buffer
            } ?: return null
            writeSquare(bytes, out)
            name
        }.getOrElse { out.delete(); null }
    }

    /** A fresh file for the camera to write into, and the content URI to hand it. */
    fun cameraTarget(context: Context): Pair<String, Uri> {
        val name = freshName(context)
        return name to FileProvider.getUriForFile(context, "${context.packageName}.files", file(context, name))
    }

    /** Crops and shrinks what the camera wrote into [name]. False, and nothing left behind, when there's no picture. */
    fun finishCamera(context: Context, name: String): Boolean {
        if (!isSafeName(name)) return false
        val target = file(context, name)
        return runCatching {
            if (!target.exists() || target.length() == 0L || target.length() > MAX_BYTES) error("no picture")
            writeSquare(target.readBytes(), target)
            true
        }.getOrElse { target.delete(); false }
    }

    /**
     * How much to shrink while decoding: enough that the short side still reaches [SIDE], and always enough that the
     * whole picture fits in [MAX_DECODE_PIXELS], so a tiny file claiming to be 100,000 px wide can't use up memory.
     */
    fun sampleFor(width: Int, height: Int): Int {
        var sample = 1
        while (minOf(width, height) / (sample * 2) >= SIDE) sample *= 2
        while ((width / sample).toLong() * (height / sample) > MAX_DECODE_PIXELS) sample *= 2
        return sample
    }

    private fun writeSquare(bytes: ByteArray, out: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "not an image" }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight) })
            ?: error("not an image")
        val rotation = runCatching {
            when (ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        val side = minOf(decoded.width, decoded.height)
        val scale = minOf(1f, SIDE.toFloat() / side)
        val matrix = Matrix().apply { postScale(scale, scale); postRotate(rotation) }
        val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side, matrix, true)
        val temp = File(out.parentFile, out.name + ".tmp")
        temp.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        if (!temp.renameTo(out)) { temp.copyTo(out, overwrite = true); temp.delete() }
    }

    /** The photo for showing, or null when there's none or the name isn't one of ours. */
    fun load(context: Context, name: String?, side: Int = 256): Bitmap? {
        if (name == null || !isSafeName(name)) return null
        return runCatching {
            val f = file(context, name)
            if (!f.exists()) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= side) sample *= 2
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }

    /** Removes every stored photo except [name], so changing or removing the photo leaves nothing behind. */
    fun keepOnly(context: Context, name: String?) {
        dir(context).listFiles()?.forEach { if (it.name != name) it.delete() }
    }

    fun deleteAll(context: Context) {
        dir(context).deleteRecursively()
    }
}
