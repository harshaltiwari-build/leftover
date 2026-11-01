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
class TabsJourneyTest : JourneyBase() {
    @Test fun everyTabOpens() {
        launchHome()
        navigate { compose.onNodeWithText("Activity").performClick() }
        compose.waitUntilAtLeastOneExists(hasText("Search merchant, note or amount"), 10_000)
        navigate { compose.onNodeWithText("Plan").performClick() }
        compose.waitUntilAtLeastOneExists(hasText("Shared with friends"), 10_000)
        navigate { compose.onNodeWithText("Insights").performClick() }
        compose.waitUntilAtLeastOneExists(hasText("Daily average"), 10_000)
        navigate { compose.onNodeWithText("Home").performClick() }
        compose.waitUntilAtLeastOneExists(androidx.compose.ui.test.hasContentDescription("Your profile"), 10_000)
    }
}
