package app.leftover.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.leftover.data.OnboardingAnswers
import app.leftover.domain.rupees
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** The state found on a real phone: ₹200 in the account, ₹200 spent, weeks to payday. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class NothingLeftJourneyTest : JourneyBase() {
    @Test fun homeSaysNothingIsLeftInsteadOfAConfusingTarget() {
        runBlocking {
            app.repository.deleteEverything()
            app.repository.completeOnboarding(OnboardingAnswers(null, null, "Salary", rupees(25_000), 28, rupees(200), emptyList()))
        }
        seedExpense("Blue Tokai", 200)
        launchHome()
        compose.waitUntilAtLeastOneExists(hasText("Nothing left to spend"), 10_000)
        compose.onNodeWithText("Essentials only until payday. Add money that came in to update this.").assertExists()
        compose.onNodeWithText("Not logged yet").assertExists()
        compose.onNodeWithText("of ₹7", substring = true).assertDoesNotExist()
    }
}
