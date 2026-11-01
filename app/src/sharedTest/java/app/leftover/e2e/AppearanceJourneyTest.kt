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
class AppearanceJourneyTest : JourneyBase() {
    @Test fun accentAndFontCanBeChanged() {
        launchHome()
        navigate { compose.onNodeWithContentDescription("Your profile").performClick() }
        navigate { compose.onNodeWithContentDescription("Settings").performClick() }
        navigate { compose.onNodeWithText("Appearance and language").performScrollTo().performClick() }
        compose.onNodeWithText("Peach").performScrollTo().performClick()
        compose.onNodeWithText("Jakarta").performScrollTo().performClick()

        // Selected only once the choice is saved and read back from settings.
        compose.waitUntilAtLeastOneExists(hasText("Peach") and isSelected(), 10_000)
        compose.waitUntilAtLeastOneExists(hasText("Jakarta") and isSelected(), 10_000)
        navigate { compose.onNodeWithContentDescription("Back").performClick() }
        compose.waitUntilAtLeastOneExists(hasText("Peach · Jakarta"), 10_000)
    }
}
