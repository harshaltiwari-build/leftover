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
import kotlinx.coroutines.launch
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
            ReminderAlarms.cancelDaily(context, ReminderAlarms.DAILY)
            return
        }
        ReminderAlarms.setDaily(context, ReminderAlarms.DAILY, 9)
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
            ReminderAlarms.cancelDaily(context, ReminderAlarms.DAILY_LOG)
            return
        }
        ReminderAlarms.setDaily(context, ReminderAlarms.DAILY_LOG, 21)
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
        // The background job can run late when the phone is idle; a clock alarm at the same moment makes it arrive on time.
        // Both post the same notification (same id), so whichever runs second replaces the first rather than doubling it.
        ReminderAlarms.setOneShot(context, key, title, body, route, System.currentTimeMillis() + delay)
    }

    /** Nine in the morning on [date], or a minute from now if that has already passed. */
    fun nineAm(date: java.time.LocalDate): Long = date.atTime(9, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    internal fun data(key: String, title: String, body: String, route: String): androidx.work.Data =
        androidx.work.Data.Builder().putString(KEY, key).putString(TITLE, title).putString(BODY, body).putString(ROUTE, route).build()

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
            // A reminder delivered by both the job and the clock alarm should alert once.
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(lock)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}

/**
 * Clock alarms behind the reminders. The background job alone is approximate and can run hours late when the phone is
 * idle or saving battery; an alarm that is allowed to fire while idle is delivered within minutes. No special permission
 * is needed for this kind of alarm.
 */
object ReminderAlarms {
    const val ACTION = "app.leftover.REMINDER_ALARM"
    const val DAILY = "daily"
    const val DAILY_LOG = "dailylog"
    const val ONESHOT = "oneshot"
    internal const val EXTRA_KIND = "kind"
    internal const val EXTRA_KEY = "key"
    internal const val EXTRA_TITLE = "title"
    internal const val EXTRA_BODY = "body"
    internal const val EXTRA_ROUTE = "route"

    private fun pending(context: Context, kind: String, key: String, extras: Intent.() -> Unit = {}): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).setAction(ACTION)
            // A different address per reminder, so setting one never replaces another.
            .setData(Uri.parse("leftover-reminder://$kind/${Uri.encode(key)}")).putExtra(EXTRA_KIND, kind).apply(extras)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun manager(context: Context) = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager

    fun setOneShot(context: Context, key: String, title: String, body: String, route: String, atMillis: Long) {
        val op = pending(context, ONESHOT, key) { putExtra(EXTRA_KEY, key); putExtra(EXTRA_TITLE, title); putExtra(EXTRA_BODY, body); putExtra(EXTRA_ROUTE, route) }
        runCatching { manager(context).setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, atMillis, op) }
    }

    /** The next [hour]:00 that is at least a minute away. */
    fun setDaily(context: Context, kind: String, hour: Int) {
        val now = LocalDateTime.now().plusMinutes(1)
        var next = now.with(LocalTime.of(hour, 0))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val at = next.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        runCatching { manager(context).setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, at, pending(context, kind, kind)) }
    }

    fun cancelDaily(context: Context, kind: String) {
        runCatching { manager(context).cancel(pending(context, kind, kind)) }
    }
}

/** Receives the clock alarms: posts a one-off reminder, or runs the daily check and sets tomorrow's alarm. */
class ReminderAlarmReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderAlarms.ACTION) return
        when (intent.getStringExtra(ReminderAlarms.EXTRA_KIND)) {
            ReminderAlarms.ONESHOT -> {
                val key = intent.getStringExtra(ReminderAlarms.EXTRA_KEY) ?: return
                val title = intent.getStringExtra(ReminderAlarms.EXTRA_TITLE) ?: return
                OneShotReminders.fire(context, OneShotReminders.data(key, title, intent.getStringExtra(ReminderAlarms.EXTRA_BODY).orEmpty(), intent.getStringExtra(ReminderAlarms.EXTRA_ROUTE) ?: "home"))
            }
            ReminderAlarms.DAILY -> {
                WorkManager.getInstance(context).enqueue(androidx.work.OneTimeWorkRequestBuilder<ReminderWorker>().build())
                ReminderAlarms.setDaily(context, ReminderAlarms.DAILY, 9)
            }
            ReminderAlarms.DAILY_LOG -> {
                WorkManager.getInstance(context).enqueue(androidx.work.OneTimeWorkRequestBuilder<DailyLogWorker>().build())
                ReminderAlarms.setDaily(context, ReminderAlarms.DAILY_LOG, 21)
            }
        }
    }
}

/** Puts the daily alarms back after the phone restarts or the app is updated: alarms don't survive either. */
class ReminderBootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try { resync(context) } finally { pending.finish() }
        }
    }

    companion object {
        suspend fun resync(context: Context) {
            val app = context.applicationContext as LeftoverApp
            val st = app.settings.settings.first()
            ReminderScheduler.sync(context, ReminderPrefs(st.billReminders, st.paydayReminder, st.budgetAlerts, st.weeklyCheckIn, st.backupReminder))
            DailyLogScheduler.sync(context, st.dailyLog)
        }
    }
}
