package app.leftover.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.leftover.data.OnboardingAnswers
import app.leftover.domain.rupees
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** Spent past what's left: Home says so calmly, shows by how much, and "See options" opens the day-by-day view. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class OverUntilPaydayJourneyTest : JourneyBase() {
    @Test fun overUntilPaydayPointsToOptions() {
        runBlocking {
            app.repository.deleteEverything()
            app.repository.completeOnboarding(OnboardingAnswers(null, null, "Salary", rupees(25_000), 28, rupees(200), emptyList()))
        }
        seedExpense("Myntra", 228)
        launchApp()
        waitForText("This would come out of bill or goal money. Adding money that came in covers it.")
        compose.onAllNodesWithText("₹28").onFirst().assertExists()
        navigate { compose.onNodeWithText("See options").performClick() }
        waitForText("Until payday")
    }
}
