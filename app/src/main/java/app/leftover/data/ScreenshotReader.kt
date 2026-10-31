package app.leftover.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

/** The text found in a screenshot, with Tesseract's own 0–100 confidence in it. */
data class ScreenshotText(val text: String, val confidence: Int)

/**
 * Reads the text in a payment screenshot on the phone with Tesseract (Apache-2.0), using the English model that ships
 * inside the app. Nothing is uploaded: Leftover has no internet permission. Runs in a second or two on a typical
 * screenshot; call it off the main thread.
 */
object ScreenshotReader {
    private const val MODEL = "eng.traineddata"

    /** Tesseract wants the model on disk under a `tessdata` folder; copy it out of the APK once. */
    private fun dataPath(context: Context): String {
        val dir = File(context.noBackupFilesDir, "ocr")
        val model = File(dir, "tessdata/$MODEL")
        if (!model.exists() || model.length() == 0L) {
            model.parentFile?.mkdirs()
            val tmp = File(model.path + ".part")
            context.assets.open("tessdata/$MODEL").use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(model)
        }
        return dir.path
    }

    fun read(context: Context, file: File): ScreenshotText? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        // Long scrolling screenshots can be huge; there's no point decoding more than about 3000 px on the long side.
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 3000) sample *= 2
        val source = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val prepared = prepare(source)
        if (prepared !== source) source.recycle()
        val api = TessBaseAPI()
        return try {
            if (!api.init(dataPath(context), "eng", TessBaseAPI.OEM_LSTM_ONLY)) return null
            // A receipt is a column of separate lines: "sparse text" finds them in any order without guessing paragraphs.
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT
            api.setVariable("user_defined_dpi", "300")
            api.setImage(prepared)
            val text = api.utF8Text.orEmpty()
            ScreenshotText(text, api.meanConfidence())
        } catch (_: Throwable) {
            null
        } finally {
            api.recycle()
            prepared.recycle()
        }
    }

    /**
     * Greyscale, and scaled so text is big enough to read but small enough to be quick. Dark-mode screenshots are
     * inverted, since Tesseract reads dark text on light far better.
     */
    private fun prepare(source: Bitmap): Bitmap {
        val longSide = maxOf(source.width, source.height)
        val scale = when {
            longSide < 1400 -> 1400f / longSide
            longSide > 2600 -> 2600f / longSide
            else -> 1f
        }
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        val dark = averageLuma(source) < 110
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val grey = ColorMatrix().apply { setSaturation(0f) }
        if (dark) grey.postConcat(ColorMatrix(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f)))
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(grey) }
        Canvas(out).apply {
            scale(w.toFloat() / source.width, h.toFloat() / source.height)
            drawColor(android.graphics.Color.WHITE)
            drawBitmap(source, 0f, 0f, paint)
        }
        return out
    }

    /** Rough brightness from a sparse grid of pixels, to tell a dark-mode screenshot from a light one. */
    private fun averageLuma(b: Bitmap): Int {
        var sum = 0L; var n = 0
        val stepX = (b.width / 24).coerceAtLeast(1); val stepY = (b.height / 48).coerceAtLeast(1)
        var y = 0
        while (y < b.height) {
            var x = 0
            while (x < b.width) {
                val p = b.getPixel(x, y)
                sum += (((p shr 16) and 0xff) * 299 + ((p shr 8) and 0xff) * 587 + (p and 0xff) * 114) / 1000
                n++; x += stepX
            }
            y += stepY
        }
        return if (n == 0) 255 else (sum / n).toInt()
    }
}
