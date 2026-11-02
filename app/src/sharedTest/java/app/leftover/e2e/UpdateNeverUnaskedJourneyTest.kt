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

/** Leftover only goes online when asked: by default, opening the app and browsing About makes no request at all. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class UpdateNeverUnaskedJourneyTest : UpdateJourneyBase() {
    @Test fun nothingIsRequestedUntilYouTap() {
        useSource()
        launchHome()
        settle()
        openAbout()
        settle()
        assertEquals(0, checks.get())
        compose.onNodeWithText("Check automatically once a day").assertExists()
    }
}
