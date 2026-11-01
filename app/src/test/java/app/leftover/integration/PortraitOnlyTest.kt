package app.leftover.integration

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Leftover runs upright only. Found on a phone turned sideways: the welcome screen's buttons ran off the bottom. */
@RunWith(AndroidJUnit4::class)
class PortraitOnlyTest {
    @Test fun `every Leftover screen is portrait only`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val activities = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES).activities.orEmpty()
            .filter { it.name.startsWith("app.leftover") }
        assertTrue(activities.isNotEmpty())
        activities.forEach { assertEquals(it.name, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, it.screenOrientation) }
    }
}
