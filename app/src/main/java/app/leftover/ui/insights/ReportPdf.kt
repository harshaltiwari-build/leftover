package app.leftover.ui.insights

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.res.ResourcesCompat
import app.leftover.R
import app.leftover.data.db.TxFeeling
import app.leftover.domain.Ledger
import app.leftover.domain.Money
import app.leftover.domain.RangeReport
import app.leftover.domain.toLocalDate
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Writes a report as an A4 PDF with Android's own [PdfDocument]: no library, nothing leaves the phone until the user
 * shares the file. Notes are left out unless [includeNotes] is set, since they're often the most personal part.
 */
object ReportPdf {
    private const val W = 595f
    private const val H = 842f
    private const val M = 40f
    private val ink = 0xFF20251B.toInt()
    private val muted = 0xFF5E6357.toInt()
    private val hairline = 0xFFE3E3D8.toInt()
    private val lime = 0xFFD6F478.toInt()
    private val track = 0xFFF0F0E8.toInt()

    fun write(context: Context, ledger: Ledger, report: RangeReport, includeNotes: Boolean): File {
        val font = runCatching { ResourcesCompat.getFont(context, R.font.manrope) }.getOrNull() ?: Typeface.SANS_SERIF
        val doc = PdfDocument()
        val writer = Writer(doc, font)

        writer.title("Leftover report", report.span.label())
        writer.summary(report)
        writer.section("Where it went")
        if (report.categories.isEmpty()) writer.line("No spending in this period.", muted)
        val top = report.categories.maxOfOrNull { it.spent }?.coerceAtLeast(1) ?: 1
        report.categories.forEach { c ->
            val share = if (report.spent > 0) c.spent * 100 / report.spent else 0
            writer.barRow(c.category.name, "${Money.format(c.spent)} · $share%", c.spent.toFloat() / top)
        }

        writer.section("Money in and out")
        writer.tableHeader(listOf("Period", "In", "Out", "Net"), listOf(0f, 0.45f, 0.65f, 0.85f))
        report.buckets.forEach { b -> writer.tableRow(listOf(b.label, Money.format(b.income), Money.format(b.spent), Money.format(b.income - b.spent)), listOf(0f, 0.45f, 0.65f, 0.85f)) }

        if (report.marked > 0) {
            writer.section("Worth it?")
            TxFeeling.entries.forEach { f -> report.feelings[f]?.let { writer.tableRow(listOf(f.label, Money.format(it)), listOf(0f, 0.65f)) } }
            report.feelings[null]?.let { writer.tableRow(listOf("Not marked", Money.format(it)), listOf(0f, 0.65f)) }
        }

        if (report.merchants.isNotEmpty()) {
            writer.section("Top places")
            report.merchants.forEach { m -> writer.tableRow(listOf(m.name, "${m.count}×", Money.format(m.spent)), listOf(0f, 0.5f, 0.65f)) }
        }

        writer.section("Every spend")
        val cols = if (includeNotes) listOf(0f, 0.16f, 0.5f, 0.82f) else listOf(0f, 0.16f, 0.6f)
        writer.tableHeader(if (includeNotes) listOf("Date", "What", "Note", "Amount") else listOf("Date", "What", "Amount"), cols)
        val day = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
        report.entries.forEach { tx ->
            val what = tx.merchant.ifBlank { ledger.category(tx.categoryId)?.name ?: "Expense" } + if (tx.type == app.leftover.data.db.TxType.REFUND) " (refund)" else ""
            val amount = Money.format(RangeReport.ownShare(tx))
            val cells = if (includeNotes) listOf(day.format(tx.occurredAt.toLocalDate()), what, tx.note, amount) else listOf(day.format(tx.occurredAt.toLocalDate()), what, amount)
            writer.tableRow(cells, cols)
        }
        writer.finish()

        val out = File(context.cacheDir, "leftover-report.pdf")
        out.outputStream().use { doc.writeTo(it) }
        doc.close()
        return out
    }

    private class Writer(private val doc: PdfDocument, font: Typeface) {
        private val regular = Typeface.create(font, Typeface.NORMAL)
        private val bold = Typeface.create(font, Typeface.BOLD)
        private var pageNo = 0
        private lateinit var page: PdfDocument.Page
        private lateinit var canvas: Canvas
        private var y = 0f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        init { newPage() }

        private fun newPage() {
            if (pageNo > 0) footerAndClose()
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), pageNo).create())
            canvas = page.canvas
            y = M
        }

        private fun footerAndClose() {
            text("Made on this phone with Leftover · ${DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).format(LocalDate.now())}", M, H - 24f, 8f, muted)
            text("Page $pageNo", W - M - measure("Page $pageNo", 8f), H - 24f, 8f, muted)
            doc.finishPage(page)
        }

        fun finish() = footerAndClose()

        private fun ensure(space: Float) { if (y + space > H - 50f) newPage() }

        private fun measure(s: String, size: Float, isBold: Boolean = false): Float {
            paint.textSize = size; paint.typeface = if (isBold) bold else regular
            return paint.measureText(s)
        }

        private fun text(s: String, x: Float, baseline: Float, size: Float, color: Int, isBold: Boolean = false, maxWidth: Float = W) {
            paint.textSize = size; paint.typeface = if (isBold) bold else regular; paint.color = color; paint.style = Paint.Style.FILL
            var shown = s
            if (paint.measureText(shown) > maxWidth) {
                while (shown.isNotEmpty() && paint.measureText("$shown…") > maxWidth) shown = shown.dropLast(1)
                shown += "…"
            }
            canvas.drawText(shown, x, baseline, paint)
        }

        fun title(title: String, subtitle: String) {
            paint.color = lime; canvas.drawRoundRect(RectF(M, y, M + 26f, y + 26f), 8f, 8f, paint)
            text(title, M + 36f, y + 19f, 20f, ink, isBold = true)
            y += 44f
            text(subtitle, M, y, 12f, muted)
            y += 24f
        }

        fun summary(r: RangeReport) {
            val tiles = listOf(
                "Spent" to Money.format(r.spent),
                "Income" to Money.format(r.income),
                "Net" to Money.format(r.net),
                "Saved" to (r.savingsRate?.let { "$it%" } ?: "—"),
                "Per day" to Money.format(r.perDay),
                "Period before" to Money.format(r.previousSpent),
            )
            val w = (W - 2 * M - 2 * 10f) / 3
            tiles.chunked(3).forEach { row ->
                ensure(56f)
                row.forEachIndexed { i, (label, value) ->
                    val x = M + i * (w + 10f)
                    paint.color = track; paint.style = Paint.Style.FILL
                    canvas.drawRoundRect(RectF(x, y, x + w, y + 48f), 10f, 10f, paint)
                    text(label, x + 10f, y + 17f, 9f, muted, isBold = true)
                    text(value, x + 10f, y + 37f, 14f, ink, isBold = true, maxWidth = w - 20f)
                }
                y += 58f
            }
        }

        fun section(name: String) {
            ensure(40f)
            y += 14f
            text(name, M, y, 13f, ink, isBold = true)
            y += 8f
            paint.color = hairline; canvas.drawRect(M, y, W - M, y + 1f, paint)
            y += 14f
        }

        fun line(s: String, color: Int) { ensure(18f); text(s, M, y, 10f, color); y += 16f }

        fun barRow(label: String, value: String, fraction: Float) {
            ensure(30f)
            text(label, M, y, 10f, ink, isBold = true, maxWidth = 200f)
            text(value, W - M - measure(value, 10f, true), y, 10f, ink, isBold = true)
            y += 6f
            paint.color = track; canvas.drawRoundRect(RectF(M, y, W - M, y + 6f), 3f, 3f, paint)
            paint.color = ink; canvas.drawRoundRect(RectF(M, y, M + (W - 2 * M) * fraction.coerceIn(0.02f, 1f), y + 6f), 3f, 3f, paint)
            y += 20f
        }

        fun tableHeader(cells: List<String>, at: List<Float>) {
            ensure(20f)
            cells.forEachIndexed { i, s -> text(s, cellX(at, i, cells.size, s, 9f, true), y, 9f, muted, isBold = true) }
            y += 14f
        }

        fun tableRow(cells: List<String>, at: List<Float>) {
            ensure(16f)
            cells.forEachIndexed { i, s ->
                val width = (if (i + 1 < at.size) at[i + 1] - at[i] else 1f - at[i]) * (W - 2 * M) - 8f
                text(s, cellX(at, i, cells.size, s, 10f, false), y, 10f, ink, maxWidth = width)
            }
            y += 15f
        }

        /** The last column is right-aligned so amounts line up. */
        private fun cellX(at: List<Float>, i: Int, count: Int, s: String, size: Float, isBold: Boolean): Float =
            if (i == count - 1) W - M - minOf(measure(s, size, isBold), (1f - at[i]) * (W - 2 * M)) else M + at[i] * (W - 2 * M)
    }
}
