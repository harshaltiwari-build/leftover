package app.leftover.e2e

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.leftover.LeftoverApp
import app.leftover.MainActivity
import app.leftover.data.OnboardingAnswers
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxType
import app.leftover.domain.rupees
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AddExpenseJourneyTest : JourneyBase() {
    @Test fun addingAnExpenseUpdatesHome() {
        launchHome()
        compose.onNodeWithText("₹10,000").assertExists()
        navigate { compose.onNodeWithContentDescription("Add an expense").performClick() }
        compose.waitUntilAtLeastOneExists(hasContentDescription("3"), 10_000)
        listOf("3", "5", "0").forEach { compose.onNodeWithContentDescription(it).performClick() }
        compose.onNodeWithText("Eating out").performScrollTo().performClick()
        compose.onNodeWithText("Save ₹350 · Eating out").performClick()

        compose.waitUntilAtLeastOneExists(hasText("₹9,650"), 10_000)
        val saved = runBlocking { app.repository.snapshot.first().transactions }
        assertEquals(1, saved.size)
        assertEquals(rupees(350), saved.single().amount)
    }
}
