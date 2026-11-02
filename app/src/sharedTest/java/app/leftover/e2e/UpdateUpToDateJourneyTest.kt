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

/** The latest release is the one you have: say so, and offer nothing. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class UpdateUpToDateJourneyTest : UpdateJourneyBase() {
    @Test fun saysYouAreUpToDate() {
        useSource(latest = release.copy(version = "0.0.1", apkName = "leftover-0.0.1.apk"))
        launchHome()
        openAbout()
        compose.onNodeWithText("Check for updates").performScrollTo().performClick()
        settle()
        compose.onNodeWithText("You have the latest version").assertExists()
        compose.onNodeWithText("Download and install").assertDoesNotExist()
    }
}
