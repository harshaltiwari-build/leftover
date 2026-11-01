package app.leftover.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.leftover.domain.AffordAnswer
import app.leftover.domain.SafeToday
import app.leftover.domain.rupees
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** "Can I afford this?" answers in today's terms and shows each number it changes. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AffordJourneyTest : JourneyBase() {
    @Test fun affordAnswersInTodaysTerms() {
        seedExpense("Swiggy", 200)
        launchHome()
        navigate { compose.onNodeWithText("Afford?").performClick() }
        waitForText("Can I afford this?")
        compose.onNodeWithText("₹5,999").performScrollTo().performClick()

        // Whatever today's date, the screen must say what the rules say for this ledger.
        val s = runBlocking { app.repository.snapshot.first() }
        val shopping = s.categories.first { it.name == "Shopping" }
        val expected = AffordAnswer.of(SafeToday.of(s.ledger), rupees(5_999), s.cycle.nextPayday, shopping.name, s.ledger.spent(shopping.id), shopping.budgetLimit, hidden = false)
        waitForText(expected.title)
        compose.onNodeWithText(expected.body).assertExists()
        expected.rows.forEach { row ->
            compose.onNodeWithText(row.name).performScrollTo().assertExists()
            compose.onNodeWithText(row.to, substring = true).assertExists()
        }

        // Attack: the slider stops at ₹8,000; bigger buys must still be checkable by typing the price.
        compose.onNode(hasSetTextAction() and hasContentDescription("Type a price")).performScrollTo().performTextInput("25000")
        val big = AffordAnswer.of(SafeToday.of(s.ledger), rupees(25_000), s.cycle.nextPayday, shopping.name, s.ledger.spent(shopping.id), shopping.budgetLimit, hidden = false)
        waitForText(big.title)
        compose.onNodeWithText(big.body).assertExists()
    }
}
