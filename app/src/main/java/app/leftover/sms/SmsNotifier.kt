package app.leftover.sms

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.leftover.MainActivity
import app.leftover.R
import app.leftover.data.db.PendingSmsEntity
import app.leftover.domain.Money
import app.leftover.domain.SmsKind
import app.leftover.ui.DeepLinks
import app.leftover.ui.Routes

/** Shows a notification for each tracked or waiting transaction. Amounts are left out when Hide balances is on. */
class SmsNotifier(private val context: Context) : SmsAlerts {

    override fun saved(transactionId: Long, amount: Long, merchant: String, categoryName: String?, hideAmounts: Boolean) {
        val id = SAVED_BASE + (transactionId % 10_000).toInt()
        val title = if (hideAmounts) "Transaction saved" else "Saved ${Money.format(amount, showPaise = true)}${merchant.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}"
        val builder = base(id, title, categoryName ?: "Added from your bank text")
        builder.addAction(0, "Undo", broadcast(SmsActions.UNDO, id, requestCode = id * 10) { putExtra(SmsActions.EXTRA_TRANSACTION, transactionId) })
        post(id, builder)
    }

    override fun review(pending: PendingSmsEntity, actions: List<ReviewAction>, hideAmounts: Boolean) {
        val id = REVIEW_BASE + (pending.id % 10_000).toInt()
        val what = if (pending.kind == SmsKind.DEBIT) "Spent" else "Received"
        val title = when {
            hideAmounts -> "New transaction to review"
            pending.merchant.isNotBlank() -> "$what ${Money.format(pending.amount, showPaise = true)} · ${pending.merchant}"
            else -> "$what ${Money.format(pending.amount, showPaise = true)}"
        }
        val text = if (actions.isEmpty()) "Open Leftover to finish this one" else "Pick one to save it"
        val builder = base(id, title, text)
        actions.forEachIndexed { index, action ->
            builder.addAction(
                0, action.label,
                broadcast(SmsActions.SAVE, id, requestCode = id * 10 + index + 1) {
                    putExtra(SmsActions.EXTRA_PENDING, pending.id)
                    putExtra(SmsActions.EXTRA_TYPE, action.type.name)
                    putExtra(SmsActions.EXTRA_CATEGORY, action.categoryId ?: -1L)
                },
            )
        }
        builder.addAction(0, "Skip", broadcast(SmsActions.SKIP, id, requestCode = id * 10 + 9) { putExtra(SmsActions.EXTRA_PENDING, pending.id) })
        post(id, builder)
    }

    override fun mandate(merchant: String, amount: Long, hideAmounts: Boolean) {
        val id = MANDATE_BASE + (merchant.hashCode() and 0x7fff)
        val title = if (hideAmounts) "New recurring debit" else "New recurring debit: $merchant ${Money.format(amount)}"
        post(id, base(id, title, "Open your AutoPay list to track or dismiss it", route = Routes.MANDATES))
    }

    private fun base(id: Int, title: String, text: String, route: String = Routes.REVIEW): NotificationCompat.Builder {
        ensureChannel()
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse(DeepLinks.uri(if (route == Routes.MANDATES) "mandates" else "review")))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // On the lock screen only a generic line shows, whatever the amount setting is.
        val lockScreen = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_leftover).setContentTitle("Leftover").setContentText("New transaction").build()
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_leftover)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(lockScreen)
            .setOnlyAlertOnce(true)
    }

    private fun broadcast(action: String, notificationId: Int, requestCode: Int, extras: Intent.() -> Unit): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, SmsActionReceiver::class.java).setAction(action).putExtra(SmsActions.EXTRA_NOTIFICATION, notificationId).apply(extras),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    @SuppressLint("MissingPermission")
    private fun post(id: Int, builder: NotificationCompat.Builder) {
        val allowed = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Tracked transactions", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A note when a bank text is added or needs your help"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            })
        }
    }

    private companion object {
        const val CHANNEL = "tracked_transactions"
        const val SAVED_BASE = 30_000
        const val REVIEW_BASE = 50_000
        const val MANDATE_BASE = 70_000
    }
}
