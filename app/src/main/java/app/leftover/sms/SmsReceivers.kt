package app.leftover.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.app.NotificationManagerCompat
import app.leftover.LeftoverApp
import app.leftover.data.db.TxType
import app.leftover.domain.SmsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * Hears every incoming text, but only looks at the body of those from a named sender such as `AX-HDFCBK`.
 * Texts from phone numbers are dropped before their body is read.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val named = parts.filter { SmsParser.isTransactionalSender(it.originatingAddress.orEmpty()) }.groupBy { it.originatingAddress.orEmpty() }
        if (named.isEmpty()) return
        val app = context.applicationContext as LeftoverApp
        val result = goAsync()
        receiverScope.launch {
            try {
                // With tracking off, the body is never even read.
                if (!app.settings.settings.first().smsTracking) return@launch
                val ingestor = app.ingestor
                for ((address, pieces) in named) {
                    ingestor.handle(address, pieces.joinToString("") { it.messageBody.orEmpty() }, pieces.first().timestampMillis)
                }
            } finally {
                result.finish()
            }
        }
    }
}

object SmsActions {
    const val SAVE = "app.leftover.SMS_SAVE"
    const val SKIP = "app.leftover.SMS_SKIP"
    const val UNDO = "app.leftover.SMS_UNDO"
    const val EXTRA_PENDING = "pending"
    const val EXTRA_TRANSACTION = "transaction"
    const val EXTRA_TYPE = "type"
    const val EXTRA_CATEGORY = "category"
    const val EXTRA_NOTIFICATION = "notification"
}

/** Handles the buttons on a notification: save with a category, skip, or undo. */
class SmsActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repository = (context.applicationContext as LeftoverApp).repository
        val notification = intent.getIntExtra(SmsActions.EXTRA_NOTIFICATION, -1)
        val result = goAsync()
        receiverScope.launch {
            try {
                when (intent.action) {
                    SmsActions.SAVE -> {
                        val pending = repository.pendingSms(intent.getLongExtra(SmsActions.EXTRA_PENDING, -1))
                        val account = pending?.accountId
                        val type = runCatching { TxType.valueOf(intent.getStringExtra(SmsActions.EXTRA_TYPE).orEmpty()) }.getOrNull()
                        if (pending != null && account != null && type != null) {
                            repository.approvePending(pending, type, intent.getLongExtra(SmsActions.EXTRA_CATEGORY, -1).takeIf { it >= 0 }, account)
                        }
                    }
                    SmsActions.SKIP -> repository.dismissPending(intent.getLongExtra(SmsActions.EXTRA_PENDING, -1))
                    SmsActions.UNDO -> repository.deleteTransaction(intent.getLongExtra(SmsActions.EXTRA_TRANSACTION, -1))
                }
                if (notification >= 0) NotificationManagerCompat.from(context).cancel(notification)
            } finally {
                result.finish()
            }
        }
    }
}
