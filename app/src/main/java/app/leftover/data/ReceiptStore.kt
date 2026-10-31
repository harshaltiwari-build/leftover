package app.leftover.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import android.media.ExifInterface
import java.io.File

/**
 * Receipt photos live in the app's private `receipts` folder, one JPEG per transaction, shrunk to at most 1600 px.
 * They never leave the phone and aren't part of backup files. Files no transaction points to are swept on start.
 */
object ReceiptStore {
    private const val MAX_SIDE = 1600

    fun dir(context: Context): File = File(context.filesDir, "receipts").apply { mkdirs() }

    fun file(context: Context, name: String): File = File(dir(context), name)

    /** A fresh file for the camera to write into, and the content URI to hand it. */
    fun cameraTarget(context: Context): Pair<String, Uri> {
        val name = "camera-${System.currentTimeMillis()}.jpg"
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file(context, name))
        return name to uri
    }

    /** Copies a picked image in, shrunk and upright. Returns the stored file name, or null when the image can't be read. */
    fun importFrom(context: Context, uri: Uri): String? = runCatching {
        val name = "receipt-${System.currentTimeMillis()}.jpg"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        writeShrunk(bytes, file(context, name))
        name
    }.getOrNull()

    /** Shrinks a photo the camera wrote straight into [name]. Returns false when there's nothing usable there. */
    fun finishCamera(context: Context, name: String): Boolean = runCatching {
        val target = file(context, name)
        if (!target.exists() || target.length() == 0L) { target.delete(); return false }
        writeShrunk(target.readBytes(), target)
        true
    }.getOrDefault(false)

    private fun writeShrunk(bytes: ByteArray, out: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("not an image")
        val rotation = runCatching {
            when (ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply { postScale(scale, scale); postRotate(rotation) }
        val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        out.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    }

    /** A small preview for lists and the detail screen. */
    fun thumbnail(context: Context, name: String, side: Int = 480): Bitmap? = runCatching {
        val f = file(context, name)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= side) sample *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    /** Removes photos no transaction points to. Files younger than an hour are left alone, in case they're mid-attach. */
    fun sweep(context: Context, inUse: Set<String>) {
        val cutoff = System.currentTimeMillis() - 60 * 60 * 1000L
        dir(context).listFiles()?.forEach { if (it.name !in inUse && it.lastModified() < cutoff) it.delete() }
    }

    fun deleteAll(context: Context) {
        dir(context).deleteRecursively()
    }
}
