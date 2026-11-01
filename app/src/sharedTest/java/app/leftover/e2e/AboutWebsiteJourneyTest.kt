package app.leftover.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** Settings → About Leftover shows the website, and the row can be tapped to open it in the browser. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AboutWebsiteJourneyTest : JourneyBase() {
    @Test fun aboutShowsTheWebsite() {
        launchHome()
        navigate { compose.onNodeWithContentDescription("Your profile").performClick() }
        navigate { compose.onNodeWithContentDescription("Settings").performClick() }
        navigate { compose.onNodeWithText("About Leftover").performScrollTo().performClick() }
        compose.waitUntilAtLeastOneExists(hasText("Website"), 10_000)
        compose.onNodeWithText("harshaltiwari-build.github.io/leftover", substring = true).assertExists()
        compose.onNodeWithText("Website").performScrollTo().assertHasClickAction()
    }
}
