package app.leftover.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.leftover.data.db.TxSource
import app.leftover.domain.ScreenshotStatus
import app.leftover.domain.rupees
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** See nothingIsAddedUntilAddIsTapped: a shared payment screenshot waits for Add before anything is saved. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ScreenshotConfirmAddJourneyTest : ConfirmJourneyBase() {
    @Test fun nothingIsAddedUntilAddIsTapped() {
        launchHome()
        share(read())
        waitForText("Add this payment?")
        // The payee and amount show twice: in the heading and in the details list.
        compose.onAllNodesWithText("Rohan Mehta").assertCountEquals(2)
        compose.onAllNodesWithText("₹1,000").assertCountEquals(2)
        compose.onNodeWithText("123456789012").assertExists()
        compose.onNodeWithText("Read from your Google Pay screenshot").assertExists()
        assertEquals(0, saved().size)

        navigate { compose.onNodeWithText("Add").performClick() }
        settle()
        compose.onNodeWithText("Saved ₹1,000 · Rohan Mehta").assertExists()
        val tx = saved().single()
        assertEquals(TxSource.SCAN, tx.source)
        assertEquals(rupees(1_000), tx.amount)

        compose.onNodeWithText("Undo").performClick()
        settle()
        compose.waitUntil(5_000) { saved().isEmpty() }
    }
}
