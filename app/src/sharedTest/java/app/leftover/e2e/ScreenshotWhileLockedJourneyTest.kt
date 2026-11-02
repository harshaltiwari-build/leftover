package app.leftover.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.leftover.domain.ScreenshotStatus
import app.leftover.domain.rupees
import app.leftover.ui.add.EntryDraft
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Attack: a screenshot shared into a locked Leftover, even at a cold start, must not save without the owner. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ScreenshotWhileLockedJourneyTest : JourneyBase() {
    @Test fun aLockedAppDoesNotSaveByItself() {
        runBlocking { app.settings.setPin("hash", "salt"); app.settings.setAppLock(true); app.settings.setAutoSaveScreenshots(true) }
        val read = EntryDraft(false, rupees(480), "Zomato", LocalDate.now(), LocalTime.of(13, 5), "UPI ref 512345678901", null, ScreenshotStatus.SUCCESS, true, "PhonePe")
        launchApp()
        withViewModel { it.handleScreenshot(read) }
        compose.waitForIdle()
        Thread.sleep(500)
        compose.waitForIdle()
        assertEquals(0, runBlocking { app.repository.snapshot.first().transactions.size })
    }
}
