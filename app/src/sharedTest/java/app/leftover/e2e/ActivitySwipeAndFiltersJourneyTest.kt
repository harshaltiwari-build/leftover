package app.leftover.e2e

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Swipe an entry right to move it to another category (with Undo), and narrow the list with More filters. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ActivitySwipeAndFiltersJourneyTest : JourneyBase() {
    @Test fun swipeRightRecategorisesAndFiltersNarrowTheList() {
        seedExpense("Swiggy", 450)
        seedExpense("Blue Tokai", 160)
        launchHome()
        navigate { compose.onNodeWithText("Activity").performClick() }
        waitForText("Swiggy")

        val row = SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions) and hasAnyDescendant(hasText("Swiggy"))
        compose.onNode(row).performTouchInput { swipeRight() }
        waitForText("Move Swiggy to")
        compose.onNodeWithText("Groceries").performClick()
        waitForText("Moved to Groceries")
        val swiggy = runBlocking { app.repository.snapshot.first() }.let { s -> s.transactions.single { it.merchant == "Swiggy" } to s }
        assertEquals("Groceries", swiggy.second.categories.single { it.id == swiggy.first.categoryId }.name)

        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(5_000) {
            runBlocking { app.repository.snapshot.first() }.let { s -> s.categories.single { c -> c.id == s.transactions.single { it.merchant == "Swiggy" }.categoryId }.name == "Eating out" }
        }

        compose.onNodeWithText("More filters").performScrollTo().performClick()
        waitForText("Account")
        compose.onNodeWithText("Groceries").assertExists()
        // Both entries are Eating out, so narrow by amount: from ₹300 keeps Swiggy (₹450), drops Blue Tokai (₹160).
        compose.onNode(hasSetTextAction() and hasContentDescription("From")).performScrollTo().performTextInput("300")
        compose.onNodeWithText("Show results").performScrollTo().performClick()
        waitForText("Filters · 1")
        compose.onNodeWithText("Blue Tokai").assertDoesNotExist()
        compose.onNodeWithText("Swiggy").assertExists()
    }
}
