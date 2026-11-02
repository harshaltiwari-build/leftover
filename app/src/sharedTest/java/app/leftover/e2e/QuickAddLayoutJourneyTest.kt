package app.leftover.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Found on a real phone: Quick add stacked categories onto four lines and left only a sliver to scroll. Everything is still
 * there, but categories now share one swipeable line and the account, date and note all fit on screen with the keypad.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class QuickAddLayoutJourneyTest : JourneyBase() {
    @Test fun everythingFitsAndLinesUp() {
        launchHome()
        navigate { compose.onNodeWithContentDescription("Add an expense").performClickNode() }
        waitForText("Paid from")

        // Categories sit on one line.
        // Position, not visible area: the last chips are along the line, out of sight until you swipe.
        val first = compose.onNodeWithText("Eating out").fetchSemanticsNode().positionInRoot.y
        val second = compose.onNodeWithText("Coffee").fetchSemanticsNode().positionInRoot.y
        val last = compose.onNodeWithText("Other").fetchSemanticsNode().positionInRoot.y
        assertTrue("categories wrap: $first $second $last", abs(first - second) < 2f && abs(first - last) < 2f)

        // Account, date and note are on screen without scrolling, with the Save button below the keypad.
        compose.onNodeWithText("Paid from").assertIsDisplayed()
        compose.onNodeWithContentDescription("Today").assertIsDisplayed()
        compose.onNodeWithText("Add a note (optional)").assertIsDisplayed()
        compose.onNodeWithText("Enter an amount").assertIsDisplayed()

        // The first category chip and the Save button share a left margin, and the keypad reaches the right edge.
        val chipLeft = compose.onNodeWithText("Eating out").fetchSemanticsNode().boundsInWindow.left
        val saveLeft = compose.onNodeWithText("Enter an amount").fetchSemanticsNode().boundsInWindow.left
        val keyLeft = compose.onNodeWithContentDescription("Delete digit").fetchSemanticsNode().boundsInWindow.right
        assertTrue("edges differ: chip=$chipLeft save=$saveLeft key=$keyLeft", abs(chipLeft - saveLeft) < 2f && keyLeft > 900f)
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.performClickNode() = this.performClick()
