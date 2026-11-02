package app.leftover

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.leftover.notify.OneShotReminders
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A reminder set 65 seconds ahead really shows up on a phone: through the real alarm manager, job scheduler and
 * notification shade, not stand-ins. Phone-only (it takes over a minute of real time).
 */
@RunWith(AndroidJUnit4::class)
class ReminderOnPhoneTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val manager get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Before fun grant() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        manager.cancelAll()
    }

    @After fun clean() { manager.cancelAll() }

    @Test fun aReminderSetNowArrivesOnTime() {
        val setAt = System.currentTimeMillis()
        OneShotReminders.schedule(context, "phone-test", "Phone test reminder", "If you can see this, reminders work.", "home", setAt + 65_000)
        var arrivedAt = 0L
        val until = setAt + 150_000
        while (System.currentTimeMillis() < until && arrivedAt == 0L) {
            if (manager.activeNotifications.any { it.notification.extras.getCharSequence("android.title") == "Phone test reminder" }) arrivedAt = System.currentTimeMillis()
            else Thread.sleep(1_000)
        }
        assertNotNull("the reminder never arrived", arrivedAt.takeIf { it > 0 })
        val seconds = (arrivedAt - setAt) / 1000
        assertTrue("arrived after $seconds s, expected about 65", seconds in 60..110)
        // Only one notification, however many triggers fired.
        assertTrue(manager.activeNotifications.count { it.notification.extras.getCharSequence("android.title") == "Phone test reminder" } == 1)
    }
}
