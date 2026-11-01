package app.leftover.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Found on the phone: with icons added, "Wishlist" ran off the right edge of Plan. All four sections must fit. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class PlanTabsJourneyTest : JourneyBase() {
    @Test fun allFourPlanSectionsFitOnScreen() {
        launchHome()
        navigate { compose.onNodeWithText("Plan").performClick() }
        compose.waitUntilAtLeastOneExists(hasText("Wishlist"), 10_000)
        val screen = compose.onAllNodes(isRoot()).fetchSemanticsNodes().first().boundsInWindow
        listOf("Budgets", "Goals", "Bills", "Wishlist").forEach { label ->
            val b = compose.onNodeWithText(label).fetchSemanticsNode().boundsInWindow
            assertTrue("$label at $b is cut off (screen $screen)", b.left >= screen.left && b.right <= screen.right && b.width > 0)
        }
        navigate { compose.onNodeWithText("Wishlist").performClick() }
        compose.waitUntilAtLeastOneExists(hasText("Add something you want"), 10_000)
    }
}
