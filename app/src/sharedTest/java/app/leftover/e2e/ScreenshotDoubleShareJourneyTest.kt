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

/** Attack: the same screenshot shared twice in quick succession must be saved once, not twice. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ScreenshotDoubleShareJourneyTest : JourneyBase() {
    @Test fun aDoubleShareSavesOnce() {
        val read = EntryDraft(false, rupees(480), "Zomato", LocalDate.now(), LocalTime.of(13, 5), "UPI ref 512345678901", null, ScreenshotStatus.SUCCESS, true, "PhonePe")
        runBlocking { app.settings.setAutoSaveScreenshots(true) }
        launchHome()
        withViewModel { it.handleScreenshot(read); it.handleScreenshot(read) }
        waitForText("Saved ₹480 · Zomato")
        compose.waitForIdle()
        Thread.sleep(500)
        compose.waitForIdle()
        assertEquals(1, runBlocking { app.repository.snapshot.first().transactions.size })
    }
}
