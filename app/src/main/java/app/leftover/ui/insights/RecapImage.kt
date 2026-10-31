package app.leftover.ui.insights

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import app.leftover.R
import java.io.File

/** One row on the shared recap image: a small label, a big value and a line under it. */
data class RecapStat(val label: String, val value: String, val line: String)

/**
 * Draws the recap as a 1080 × 1350 image for stories and chats. It's built from [RecapStat]s the screen has already
 * stripped of amounts when "Hide amounts when sharing" is on, so the picture never shows more than the text would.
 */
object RecapImage {
    private const val W = 1080
    private const val H = 1350
    private val ink = 0xFF20251B.toInt()
    private val ivory = 0xFFF8F8F2.toInt()
    private val lime = 0xFFD6F478.toInt()
    private val soft = 0xFFB9BDB0.toInt()

    fun write(context: Context, month: String, period: String, stats: List<RecapStat>): File {
        val font = runCatching { ResourcesCompat.getFont(context, R.font.manrope) }.getOrNull() ?: Typeface.SANS_SERIF
        val bold = Typeface.create(font, Typeface.BOLD)
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(ink)

        fun text(s: String, x: Float, y: Float, size: Float, color: Int, typeface: Typeface = bold, maxWidth: Float = W - 2 * 80f) {
            paint.typeface = typeface; paint.textSize = size; paint.color = color
            var shown = s
            while (shown.length > 1 && paint.measureText(shown) > maxWidth) shown = shown.dropLast(2) + "…"
            canvas.drawText(shown, x, y, paint)
        }

        paint.color = lime
        canvas.drawRoundRect(RectF(80f, 80f, 128f, 128f), 14f, 14f, paint)
        text("Leftover", 148f, 118f, 40f, ivory)
        text(period, 80f, 210f, 34f, soft, font)
        text("$month.", 80f, 330f, 120f, lime)

        var y = 430f
        stats.take(4).forEach { s ->
            paint.color = 0x14FFFFFF
            canvas.drawRoundRect(RectF(80f, y, W - 80f, y + 190f), 36f, 36f, paint)
            text(s.label.uppercase(), 120f, y + 54f, 26f, soft)
            text(s.value, 120f, y + 124f, 64f, ivory)
            text(s.line, 120f, y + 166f, 28f, soft, font)
            y += 210f
        }
        text("Tracked on my phone. No bank login, no ads.", 80f, H - 70f, 28f, soft, font)

        val out = File(context.cacheDir, "leftover-recap.png")
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return out
    }
}
