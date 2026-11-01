package app.leftover.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.leftover.domain.rupees
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class BudgetJourneyTest : JourneyBase() {
    @Test fun settingOneMonthlyBudgetFromPlan() {
        seedExpense("Swiggy", 1_500)
        launchHome()
        navigate { compose.onNodeWithText("Plan").performClick() }
        compose.waitUntilAtLeastOneExists(hasText("How much do you want to spend this cycle?"), 10_000)
        compose.onNode(hasSetTextAction() and hasContentDescription("Spending budget")).performScrollTo().performTextInput("12000")
        compose.onNodeWithText("Set ₹12,000").performScrollTo().performClick()

        compose.waitUntilAtLeastOneExists(hasText("left of ₹12,000"), 10_000)
        compose.onNodeWithText("₹10,500").assertExists()
        assertEquals(rupees(12_000), runBlocking { app.settings.settings.first().spendingBudget })
    }
}
