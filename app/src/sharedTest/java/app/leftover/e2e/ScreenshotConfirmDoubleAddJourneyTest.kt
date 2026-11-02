package app.leftover.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Attack: tapping Add several times, as fast as a thumb can, must add the payment once. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ScreenshotConfirmDoubleAddJourneyTest : ConfirmJourneyBase() {
    @Test fun rapidAddsSaveOnce() {
        launchHome()
        share(read())
        waitForText("Add this payment?")
        withViewModel { vm -> repeat(5) { vm.addProposal(null) } }
        settle()
        assertEquals(1, runBlocking { app.repository.snapshot.first().transactions.size })
    }
}
