package app.leftover.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.leftover.data.db.TxSource
import app.leftover.domain.ScreenshotStatus
import app.leftover.domain.rupees
import app.leftover.ui.add.EntryDraft
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A shared payment screenshot, once read: a clear one saves itself with Undo; the same one again opens Quick add to
 * check instead of saving twice. Reading the picture is covered by the parser tests; this starts from what was read.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ScreenshotAutoSaveJourneyTest : JourneyBase() {
    private val read = EntryDraft(
        income = false, amount = rupees(250), merchant = "Blue Tokai", date = LocalDate.now(), time = LocalTime.of(10, 30),
        note = "UPI ref 412345678901", receipt = null, status = ScreenshotStatus.SUCCESS, read = true, app = "GPay",
    )

    @Test fun aClearScreenshotSavesItselfAndADuplicateAsks() {
        runBlocking { app.settings.setAutoSaveScreenshots(true) }
        launchHome()
        withViewModel { it.handleScreenshot(read) }
        waitForText("Saved ₹250 · Blue Tokai")
        val saved = runBlocking { app.repository.snapshot.first().transactions }.single()
        assertEquals(TxSource.SCAN, saved.source)
        assertEquals(rupees(250), saved.amount)

        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(5_000) { runBlocking { app.repository.snapshot.first().transactions.isEmpty() } }

        withViewModel { it.handleScreenshot(read) }
        waitForText("Saved ₹250 · Blue Tokai")
        withViewModel { it.handleScreenshot(read) }
        waitForText("Read from your GPay screenshot")
        assertTrue(runBlocking { app.repository.snapshot.first().transactions }.size == 1)
    }
}
