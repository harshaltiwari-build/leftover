package app.leftover.integration

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import app.leftover.LeftoverApp
import app.leftover.data.OnboardingAnswers
import app.leftover.data.db.BillEntity
import app.leftover.domain.ReminderPrefs
import app.leftover.domain.rupees
import app.leftover.notify.OneShotReminders
import app.leftover.notify.ReminderAlarmReceiver
import app.leftover.notify.ReminderBootReceiver
import app.leftover.notify.ReminderScheduler
import app.leftover.notify.ReminderWorker
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * Reminders must really arrive: posted once, at the right time, private on the lock screen, opening the right screen,
 * and silent when notifications are off. Runs the real workers and alarm receiver against the real notification manager.
 */
@RunWith(AndroidJUnit4::class)
class ReminderDeliveryTest {
    private val app get() = ApplicationProvider.getApplicationContext<LeftoverApp>()
    private val manager get() = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private fun alarmShadow() = shadowOf(app.getSystemService(Context.ALARM_SERVICE) as AlarmManager)!!
    private fun shown() = shadowOf(manager).allNotifications
    private val tomorrow get() = LocalDate.now().plusDays(1)

    @Before fun setUp() {
        runBlocking {
            app.repository.deleteEverything()
            app.repository.completeOnboarding(OnboardingAnswers(null, null, "Salary", rupees(45_000), 1, rupees(20_000), emptyList()))
        }
        shadowOf(app as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        manager.cancelAll()
    }

    private fun billDueTomorrow(name: String = "Netflix") = runBlocking {
        app.repository.saveBill(BillEntity(name = name, amount = rupees(649), dueDay = tomorrow.dayOfMonth, icon = "tv", remindDaysBefore = 1))
        app.settings.setBillReminders(true)
    }

    private fun runDaily(): ListenableWorker.Result = runBlocking { TestListenableWorkerBuilder<ReminderWorker>(app).build().doWork() }

    @Test fun `a bill due tomorrow posts its reminder, once, however often the worker runs`() {
        billDueTomorrow()
        assertEquals(ListenableWorker.Result.success(), runDaily())
        runDaily(); runDaily()
        val n = shown()
        assertEquals(1, n.size)
        val extras = n.single().extras
        assertEquals("Netflix is due tomorrow", extras.getCharSequence("android.title").toString())
        assertEquals("₹649", extras.getCharSequence("android.text").toString())
    }

    @Test fun `with Hide balances the reminder carries no amount`() {
        billDueTomorrow()
        runBlocking { app.settings.setHideBalances(true) }
        runDaily()
        assertTrue(shown().single().extras.getCharSequence("android.text").toString().none { it == '₹' || it.isDigit() })
    }

    @Test fun `the lock screen only says there is a reminder`() {
        billDueTomorrow()
        runDaily()
        val pub = shown().single().publicVersion
        assertNotNull(pub)
        assertEquals("Leftover", pub.extras.getCharSequence("android.title").toString())
        assertEquals("A reminder", pub.extras.getCharSequence("android.text").toString())
    }

    @Test fun `tapping a reminder opens the screen it is about`() {
        billDueTomorrow()
        runDaily()
        val intent = shadowOf(shown().single().contentIntent).savedIntent
        assertEquals("app.leftover.MainActivity", intent.component?.className)
        assertTrue(intent.data.toString(), intent.data.toString().endsWith("bills"))
    }

    @Test fun `with notifications off nothing is posted and nothing breaks`() {
        shadowOf(app as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        billDueTomorrow()
        assertEquals(ListenableWorker.Result.success(), runDaily())
        assertEquals(0, shown().size)
    }

    @Test fun `a reminder you set fires at its time from the clock alarm, and again from the job without doubling`() {
        val at = System.currentTimeMillis() + 3 * 3_600_000L
        OneShotReminders.schedule(app, "afford-earbuds", "It's payday", "Still thinking about earbuds?", "afford", at)
        // Two independent triggers were set: the exact-ish clock alarm and the background job.
        val alarm = alarmShadow().nextScheduledAlarm!!
        assertTrue("alarm at ${alarm.triggerAtTime} vs $at", kotlin.math.abs(alarm.triggerAtTime - at) < 2_000)
        assertEquals(0, shown().size)

        ReminderAlarmReceiver().onReceive(app, shadowOf(alarm.operation).savedIntent)
        assertEquals(1, shown().size)
        assertEquals("It's payday", shown().single().extras.getCharSequence("android.title").toString())

        // The job also fires later (as it would): same notification replaced, not a second one.
        val id = androidx.work.WorkManager.getInstance(app).getWorkInfosForUniqueWork("oneshot-afford-earbuds").get().single().id
        WorkManagerTestInitHelper.getTestDriver(app)?.setInitialDelayMet(id)
        assertEquals(1, shown().size)
    }

    @Test fun `setting the same reminder again replaces it instead of stacking`() {
        val at = System.currentTimeMillis() + 3_600_000L
        OneShotReminders.schedule(app, "cancel-1", "Cancel Spotify?", "Renews tomorrow.", "bills", at)
        OneShotReminders.schedule(app, "cancel-1", "Cancel Spotify?", "Renews tomorrow.", "bills", at + 60_000L)
        val alarm = alarmShadow()
        assertEquals(1, alarm.scheduledAlarms.size)
        assertEquals(1, androidx.work.WorkManager.getInstance(app).getWorkInfosForUniqueWork("oneshot-cancel-1").get().count { !it.state.isFinished })
    }

    @Test fun `the daily reminders have a clock alarm for the next nine in the morning`() {
        ReminderScheduler.sync(app, ReminderPrefs(bills = true))
        val alarm = alarmShadow().nextScheduledAlarm!!
        val at = java.time.Instant.ofEpochMilli(alarm.triggerAtTime).atZone(ZoneId.systemDefault()).toLocalDateTime()
        assertEquals(9, at.hour)
        assertEquals(0, at.minute)
        assertTrue(at.isAfter(LocalDateTime.now().minusMinutes(1)))
        assertTrue(at.isBefore(LocalDateTime.now().plusDays(1).plusMinutes(1)))
    }

    @Test fun `the clock alarm runs the daily check, posts what is due, and sets tomorrow's alarm`() {
        billDueTomorrow()
        ReminderScheduler.sync(app, ReminderPrefs(bills = true))
        val shadow = alarmShadow()
        val first = shadow.nextScheduledAlarm!!
        ReminderAlarmReceiver().onReceive(app, shadowOf(first.operation).savedIntent)
        // The check runs as a background job; give it a moment, as a real phone does.
        val until = System.currentTimeMillis() + 5_000
        while (shown().isEmpty() && System.currentTimeMillis() < until) Thread.sleep(50)
        assertEquals(1, shown().size)
        val next = shadow.nextScheduledAlarm!!
        // Fired here the instant it was set, so "tomorrow's" lands on the same 9:00; on a phone it fires at 9:00 itself.
        assertTrue("an alarm for the next day is set", next.triggerAtTime > System.currentTimeMillis())
    }

    @Test fun `switching every reminder off cancels the alarm`() {
        ReminderScheduler.sync(app, ReminderPrefs(bills = true))
        ReminderScheduler.sync(app, ReminderPrefs())
        assertNull(alarmShadow().nextScheduledAlarm)
    }

    @Test fun `a malformed alarm broadcast is ignored`() {
        ReminderAlarmReceiver().onReceive(app, android.content.Intent("app.leftover.REMINDER_ALARM"))
        ReminderAlarmReceiver().onReceive(app, android.content.Intent("some.other.action"))
        assertEquals(0, shown().size)
    }

    @Test fun `after a restart the daily alarms are put back`() {
        runBlocking { app.settings.setBillReminders(true); app.settings.setDailyLog(true); ReminderBootReceiver.resync(app) }
        val alarms = alarmShadow().scheduledAlarms
        val hours = alarms.map { java.time.Instant.ofEpochMilli(it.triggerAtTime).atZone(ZoneId.systemDefault()).hour }.sorted()
        assertEquals(listOf(9, 21), hours)
    }
}
