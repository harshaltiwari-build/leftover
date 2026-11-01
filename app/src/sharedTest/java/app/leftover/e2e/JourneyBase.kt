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

/**
 * Shared set-up for whole journeys through the real app, as a person would take them. Each journey is its own class
 * so it starts in a fresh process on the JVM, like a phone after a cold start. The same classes run on a phone, where
 * they use the separate "qa" app so real data is never touched.
 */
@OptIn(ExperimentalTestApi::class)
abstract class JourneyBase {
    @get:Rule val compose = createEmptyComposeRule()
    protected val app get() = ApplicationProvider.getApplicationContext<LeftoverApp>()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun freshUser() {
        runBlocking {
            app.repository.deleteEverything()
            app.repository.completeOnboarding(OnboardingAnswers(null, null, "Salary", rupees(45_000), 1, rupees(10_000), emptyList()))
            app.settings.setName("Asha")
        }
    }

    @After fun close() {
        scenario?.close()
        runBlocking { app.repository.deleteEverything() }
    }

    protected fun launchHome() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        // A cold start on a phone can take a moment before the first frame exists; until then there's nothing to query.
        compose.waitUntil(20_000) {
            runCatching { compose.onAllNodes(hasText("Left until payday")).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
    }

    /** A tap that changes screen. Screen changes animate, so the test clock is moved past the transition. */
    protected fun navigate(tap: () -> Unit) {
        tap()
        compose.mainClock.advanceTimeBy(1_000)
    }

    protected fun seedExpense(merchant: String, rupees: Long): Unit = runBlocking {
        val s = app.repository.snapshot.first()
        app.repository.addTransaction(
            TransactionEntity(
                type = TxType.EXPENSE, amount = rupees(rupees), accountId = s.accounts.first().id,
                categoryId = s.categories.first { it.name == "Eating out" }.id, merchant = merchant, occurredAt = System.currentTimeMillis(),
            ),
        )
        Unit
    }
}
