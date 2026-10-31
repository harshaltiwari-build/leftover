package app.leftover.ui.add

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import app.leftover.data.ReceiptStore
import app.leftover.data.ScreenshotReader
import app.leftover.domain.Paise
import app.leftover.domain.PaymentScreenshotParser
import app.leftover.domain.ScreenshotStatus
import app.leftover.domain.SharedPaymentParser
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/**
 * An entry read from a payment screenshot, waiting for the person to check it in Quick add. Nothing in it is saved
 * until they tap Save.
 */
data class EntryDraft(
    val income: Boolean,
    val amount: Paise?,
    val merchant: String,
    val date: LocalDate?,
    val time: LocalTime?,
    val note: String,
    /** The screenshot, kept as the entry's receipt so the numbers can be checked against it later. */
    val receipt: String?,
    val status: ScreenshotStatus,
    /** False when the image couldn't be read at all (not a screenshot, blurry, or unreadable). */
    val read: Boolean,
    val app: String,
)

/** Turns a shared or picked screenshot into an [EntryDraft]: keep a copy, read its text on the phone, pick out the payment. */
object ScreenshotIntake {
    /** Call off the main thread: reading takes a second or two. */
    fun read(context: Context, image: Uri?, sharedText: String?, today: LocalDate = LocalDate.now()): EntryDraft {
        val fromText = SharedPaymentParser.parse(sharedText)
        val receipt = image?.let { ReceiptStore.importFrom(context, it) }
        // Read the original, not the shrunk receipt copy: small text survives better at full size.
        val original = image?.let { copyForReading(context, it) }
        val ocr = original?.let { ScreenshotReader.read(context, it) }
        original?.delete()
        val payment = ocr?.let { PaymentScreenshotParser.parse(it.text, today) }
        val readSomething = payment?.foundAnything == true
        return EntryDraft(
            income = payment?.received == true,
            // Text the payment app shared alongside the image is exact, so it wins over what was read off the picture.
            amount = fromText.amount ?: payment?.amount,
            merchant = fromText.payee.ifBlank { payment?.payee.orEmpty() },
            date = payment?.date,
            time = payment?.time,
            note = payment?.upiRef?.takeIf { it.isNotBlank() }?.let { "UPI ref $it" }.orEmpty(),
            receipt = receipt,
            status = payment?.status ?: ScreenshotStatus.UNKNOWN,
            read = readSomething || fromText.amount != null,
            app = payment?.app.orEmpty(),
        )
    }

    /** A temporary full-size copy, decoded once to make sure it really is an image. */
    private fun copyForReading(context: Context, uri: Uri): File? = runCatching {
        val out = File(context.cacheDir, "ocr-${System.nanoTime()}.img")
        context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(out.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) { out.delete(); return null }
        out
    }.getOrNull()
}
