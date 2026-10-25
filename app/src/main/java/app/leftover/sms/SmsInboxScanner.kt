package app.leftover.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import app.leftover.domain.SmsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScanResult(val saved: Int, val queued: Int)

/**
 * Looks back through the inbox once, when the user asks. It works in two passes so that texts from people are never
 * opened: the first pass reads only the sender and date, and the second reads the body of the bank texts alone.
 */
object SmsInboxScanner {
    private const val MAX_TEXTS = 400

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    /** Returns null when the SMS permission hasn't been granted. */
    suspend fun scan(context: Context, ingestor: SmsIngestor, sinceMillis: Long): ScanResult? {
        if (!hasPermission(context)) return null
        val candidates = withContext(Dispatchers.IO) { bankTexts(context, sinceMillis) }
        var saved = 0
        var queued = 0
        for ((id, address, at) in candidates) {
            val body = withContext(Dispatchers.IO) { body(context, id) } ?: continue
            when (ingestor.handle(address, body, at, quiet = true)) {
                IngestResult.SAVED -> saved++
                IngestResult.QUEUED -> queued++
                else -> Unit
            }
        }
        return ScanResult(saved, queued)
    }

    private data class Candidate(val id: Long, val address: String, val at: Long)

    private fun bankTexts(context: Context, since: Long): List<Candidate> {
        val found = mutableListOf<Candidate>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ?", arrayOf(since.toString()), "${Telephony.Sms.DATE} ASC",
        )?.use { cursor ->
            while (cursor.moveToNext() && found.size < MAX_TEXTS) {
                val address = cursor.getString(1).orEmpty()
                if (SmsParser.isTransactionalSender(address)) found += Candidate(cursor.getLong(0), address, cursor.getLong(2))
            }
        }
        return found
    }

    private fun body(context: Context, id: Long): String? =
        context.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI, arrayOf(Telephony.Sms.BODY), "${Telephony.Sms._ID} = ?", arrayOf(id.toString()), null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
}
