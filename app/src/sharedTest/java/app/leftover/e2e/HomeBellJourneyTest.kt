package app.leftover.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Reported on the phone: the bell on Home opened the Plan page. A bell is for notifications, so it opens the reminder settings. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class HomeBellJourneyTest : JourneyBase() {
    @Test fun bellOpensRemindersNotPlan() {
        launchHome()
        navigate { compose.onAllNodesWithContentDescription("Reminders", substring = true).onFirst().performClick() }
        waitForText("Bills due")
        assertTrue("landed on Plan", compose.onAllNodes(androidx.compose.ui.test.hasText("Budgets")).fetchSemanticsNodes().isEmpty())
    }
}
