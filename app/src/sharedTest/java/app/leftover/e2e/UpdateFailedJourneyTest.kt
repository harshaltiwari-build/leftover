package app.leftover.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.leftover.data.UpdateException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** No connection: say why, in plain words, and let the person try again. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class UpdateFailedJourneyTest : UpdateJourneyBase() {
    @Test fun explainsAndOffersAnotherTry() {
        useSource(failWith = "Couldn't connect. Check your internet and try again.")
        launchHome()
        openAbout()
        compose.onNodeWithText("Check for updates").performScrollTo().performClick()
        settle()
        compose.onNodeWithText("Couldn't connect. Check your internet and try again.").assertExists()
        compose.onNodeWithText("Try again").performScrollTo().performClick()
        settle()
        assertEquals(2, checks.get())
    }
}
