package app.leftover.notify

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
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.leftover.LeftoverApp
import app.leftover.MainActivity
import app.leftover.R
import app.leftover.domain.Reminder
import app.leftover.domain.ReminderPlanner
import app.leftover.domain.ReminderPrefs
import app.leftover.ui.DeepLinks
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Runs once a day, around nine in the morning, while at least one reminder is switched on. */
object ReminderScheduler {
    private const val WORK = "leftover-reminders"

    fun sync(context: Context, prefs: ReminderPrefs) {
        val manager = WorkManager.getInstance(context)
        if (!prefs.any) {
            manager.cancelUniqueWork(WORK)
            return
        }
        val now = LocalDateTime.now()
        var next = now.with(LocalTime.of(9, 0))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
            .build()
        manager.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

/** The evening "anything to log?" nudge, around nine at night, only while it's switched on. */
object DailyLogScheduler {
    private const val WORK = "leftover-daily-log"

    fun sync(context: Context, on: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!on) {
            manager.cancelUniqueWork(WORK)
            return
        }
        val now = LocalDateTime.now()
        var next = now.with(LocalTime.of(21, 0))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<DailyLogWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
            .build()
        manager.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class DailyLogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as LeftoverApp
        if (!app.settings.settings.first().dailyLog || !ReminderNotifier.allowed(applicationContext)) return Result.success()
        val snapshot = app.repository.currentSnapshot()
        val sent = ReminderPlanner.prune(app.settings.reminderSent(), snapshot.today)
        val nudge = ReminderPlanner.dailyNudge(snapshot.ledger, sent.map { it.substringBeforeLast('|') }.toSet()) ?: return Result.success()
        ReminderNotifier.show(applicationContext, nudge)
        app.settings.setReminderSent(sent + "${nudge.key}|${snapshot.today.toEpochDay()}")
        return Result.success()
    }
}

/** A single reminder set by the user for a particular moment, such as "think again about these earbuds on payday". */
object OneShotReminders {
    private const val KEY = "key"
    private const val TITLE = "title"
    private const val BODY = "body"
    private const val ROUTE = "route"

    fun schedule(context: Context, key: String, title: String, body: String, route: String, atMillis: Long) {
        val delay = (atMillis - System.currentTimeMillis()).coerceAtLeast(60_000L)
        val data = androidx.work.Data.Builder().putString(KEY, key).putString(TITLE, title).putString(BODY, body).putString(ROUTE, route).build()
        val request = androidx.work.OneTimeWorkRequestBuilder<OneShotWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).setInputData(data).build()
        WorkManager.getInstance(context).enqueueUniqueWork("oneshot-$key", androidx.work.ExistingWorkPolicy.REPLACE, request)
    }

    /** Nine in the morning on [date], or a minute from now if that has already passed. */
    fun nineAm(date: java.time.LocalDate): Long = date.atTime(9, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    internal fun fire(context: Context, data: androidx.work.Data) {
        if (!ReminderNotifier.allowed(context)) return
        ReminderNotifier.show(context, Reminder(data.getString(KEY).orEmpty(), app.leftover.domain.ReminderKind.PAYDAY, data.getString(TITLE).orEmpty(), data.getString(BODY).orEmpty(), data.getString(ROUTE) ?: "home"))
    }
}

class OneShotWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        OneShotReminders.fire(applicationContext, inputData)
        return Result.success()
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as LeftoverApp
        val settings = app.settings.settings.first()
        val prefs = ReminderPrefs(settings.billReminders, settings.paydayReminder, settings.budgetAlerts, settings.weeklyCheckIn, settings.backupReminder)
        if (!prefs.any || !ReminderNotifier.allowed(applicationContext)) return Result.success()

        val snapshot = app.repository.currentSnapshot()
        val today = snapshot.today
        val sent = ReminderPlanner.prune(app.settings.reminderSent(), today)
        val sentKeys = sent.map { it.substringBeforeLast('|') }.toSet()
        val due = ReminderPlanner.plan(snapshot.ledger, prefs, settings.hideBalances, sentKeys, settings.lastBackupAt)
        due.forEach { ReminderNotifier.show(applicationContext, it) }
        app.settings.setReminderSent(sent + due.map { "${it.key}|${today.toEpochDay()}" })
        return Result.success()
    }
}

object ReminderNotifier {
    private const val CHANNEL = "reminders"

    fun allowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    @SuppressLint("MissingPermission")
    fun show(context: Context, reminder: Reminder) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Bills coming due, payday, budgets, backups, the weekly check-in and the evening log nudge"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            })
        }
        val id = 90_000 + (reminder.key.hashCode() and 0xffff)
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse(DeepLinks.uri(reminder.route)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // The lock screen only shows that something is waiting, never the details.
        val lock = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_leftover).setContentTitle("Leftover").setContentText("A reminder").build()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_leftover)
            .setContentTitle(reminder.title)
            .setContentText(reminder.body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(lock)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
