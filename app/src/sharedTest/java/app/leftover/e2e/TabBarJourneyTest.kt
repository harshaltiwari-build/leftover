package app.leftover.e2e

import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Found on the phone: the raised Add button was cut flat by the bar, and the tabs sat unevenly around it. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class TabBarJourneyTest : JourneyBase() {
    @Test fun addButtonIsWholeAndTabsAreEven() {
        launchHome()
        val add = compose.onNodeWithContentDescription("Add an expense").fetchSemanticsNode()
        val drawn = add.layoutInfo.coordinates.boundsInWindow()
        // Nothing clips it: what's drawn is the whole button.
        assertEquals("Add button is clipped: $drawn vs ${add.size}", add.size.height.toFloat(), drawn.height, 1f)

        val tabs = listOf("Home", "Activity", "Plan", "Insights").map { label ->
            compose.onAllNodes(hasText(label) and isSelectable(), useUnmergedTree = false).fetchSemanticsNodes().single().boundsInWindow
        }
        val widths = tabs.map { it.width }
        assertTrue("tab widths differ: $widths", widths.max() - widths.min() < 2f)
        // The gaps either side of the Add button match.
        val leftGap = drawn.left - tabs[1].right
        val rightGap = tabs[2].left - drawn.right
        assertTrue("gaps $leftGap vs $rightGap", abs(leftGap - rightGap) < 2f)
        assertTrue(compose.onAllNodes(hasContentDescription("Add an expense")).fetchSemanticsNodes().size == 1)
    }
}
