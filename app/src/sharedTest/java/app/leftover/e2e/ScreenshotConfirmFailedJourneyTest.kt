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

/** See aFailedPaymentGoesStraightToTheFullForm: a shared payment screenshot waits for Add before anything is saved. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ScreenshotConfirmFailedJourneyTest : ConfirmJourneyBase() {
    @Test fun aFailedPaymentGoesStraightToTheFullForm() {
        launchHome()
        share(read(status = ScreenshotStatus.FAILED))
        waitForText("This screenshot says the payment failed", substring = true)
        compose.onNodeWithText("Add this payment?").assertDoesNotExist()
        assertEquals(0, saved().size)
    }
}
