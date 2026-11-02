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

/** With the daily check switched on and a release waiting, Home says so and the banner leads to About. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class UpdateBannerJourneyTest : UpdateJourneyBase() {
    @Test fun homeShowsABannerForAWaitingUpdate() {
        useSource()
        runBlocking { app.settings.setAutoCheckUpdates(true) }
        launchHome()
        settle()
        compose.onNodeWithText("Leftover 9.9.9 is available").assertExists()
        assertEquals(1, checks.get())
        navigate { compose.onNodeWithText("Update").performClick() }
        settle()
        compose.onNodeWithText("Download and install").assertExists()
    }
}
