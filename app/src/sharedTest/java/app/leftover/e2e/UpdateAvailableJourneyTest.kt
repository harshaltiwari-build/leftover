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

/** A newer release is found, downloaded and made ready to install; Leftover then waits for the person to tap Install. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class UpdateAvailableJourneyTest : UpdateJourneyBase() {
    @Test fun checkDownloadAndGetReady() {
        useSource()
        launchHome()
        openAbout()
        compose.onNodeWithText("Check for updates").performScrollTo().performClick()
        settle()
        compose.onNodeWithText("Leftover 9.9.9 is available").assertExists()
        compose.onNodeWithText("A new look for the timeline.").assertExists()
        compose.onNodeWithText("Download and install").performScrollTo().performClick()
        settle()
        compose.onNodeWithText("Ready to install").assertExists()
        compose.onNodeWithText("Install now").assertExists()
        assertEquals(1, downloads.get())
    }
}
