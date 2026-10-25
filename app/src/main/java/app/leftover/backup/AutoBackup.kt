package app.leftover.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.leftover.LeftoverApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Saves a fresh backup file into the folder the user picked, on a schedule, and keeps only the newest few. */
object AutoBackup {
    private const val WORK = "leftover-auto-backup"
    const val KEEP = 5
    private const val PREFIX = "leftover-backup-"

    fun schedule(context: Context, everyDays: Int) {
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(everyDays.toLong(), TimeUnit.DAYS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }

    /** Writes [text] as a new dated file under [tree] and deletes older backups beyond [KEEP]. */
    suspend fun write(context: Context, tree: Uri, text: String, encrypted: Boolean) = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val name = PREFIX + LocalDate.now() + if (encrypted) ".leftover" else ".json"
        val file = DocumentsContract.createDocument(resolver, parent, if (encrypted) "application/octet-stream" else "application/json", name)
            ?: error("Couldn't create the backup file in that folder.")
        resolver.openOutputStream(file, "wt")?.use { it.write(text.toByteArray()) } ?: error("Couldn't write the backup file.")
        prune(context, tree)
    }

    private fun prune(context: Context, tree: Uri) {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val found = mutableListOf<Triple<String, String, Long>>()
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_LAST_MODIFIED),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1).orEmpty()
                if (name.startsWith(PREFIX)) found += Triple(cursor.getString(0), name, cursor.getLong(2))
            }
        }
        found.sortedByDescending { it.third }.drop(KEEP).forEach { (id, _, _) ->
            runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
        }
    }
}

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as LeftoverApp
        val settings = app.settings.settings.first()
        val folder = settings.backupFolder?.let(Uri::parse)
        if (!settings.autoBackup || folder == null) return Result.success()
        return try {
            val made = app.repository.createBackupText()
            AutoBackup.write(applicationContext, folder, made.text, made.encrypted)
            app.settings.recordBackup(System.currentTimeMillis(), null)
            Result.success()
        } catch (e: Exception) {
            app.settings.recordBackup(null, e.message ?: "The last automatic backup didn't work.")
            Result.success()
        }
    }
}
