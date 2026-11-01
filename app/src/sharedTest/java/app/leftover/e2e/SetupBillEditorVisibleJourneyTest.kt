package app.leftover.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** Found on a fresh install: tapping Edit opened the bill editor below the fold, hidden behind the summary. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SetupBillEditorVisibleJourneyTest : JourneyBase() {
    @Test fun theBillEditorScrollsIntoView() {
        runBlocking { app.repository.deleteEverything() }
        launchApp()
        waitForText("Get started")
        navigate { compose.onNodeWithText("Get started").performClick() }
        waitForText("What did you last spend on?")
        navigate { compose.onNodeWithText("Skip").performClick() }
        waitForText("What's your money rhythm?")
        compose.onNodeWithText("5th").performScrollTo().performClick()
        navigate { compose.onNodeWithText("Continue").performClick() }
        waitForText("Two quick numbers")
        compose.onNodeWithText("Recharge ₹349").performClick()
        compose.onNodeWithText("Edit Recharge").performScrollTo().performClick()
        compose.waitForIdle()
        // No scrolling by the test: the editor must already be in view.
        compose.onNode(hasSetTextAction() and hasContentDescription("Due on")).assertIsDisplayed()
        compose.onNodeWithText("Save bill").assertIsDisplayed()
    }
}
