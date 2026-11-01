package app.leftover.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Attack found in Part 1: a 9-digit budget showed "Set ₹99,99,99,999" and quietly saved ₹1 crore. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class BudgetLimitJourneyTest : JourneyBase() {
    @Test fun aBudgetOverTheLimitIsRefusedNotChanged() {
        launchHome()
        navigate { compose.onNodeWithText("Plan").performClick() }
        compose.waitUntilAtLeastOneExists(hasText("How much do you want to spend this cycle?"), 10_000)
        compose.onNode(hasSetTextAction() and hasContentDescription("Spending budget")).performScrollTo().performTextInput("999999999")
        compose.onNodeWithText("Up to ₹1,00,00,000 a cycle").assertExists()
        compose.onNodeWithText("Enter an amount").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Set ₹99,99,99,999").assertDoesNotExist()
        assertEquals(0L, runBlocking { app.settings.settings.first().spendingBudget })
    }
}
