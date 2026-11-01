package app.leftover.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** In the first cycle there is nothing to recap and no trend to draw, so neither should be offered. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class NewUserInsightsJourneyTest : JourneyBase() {
    @Test fun firstCycleHidesTheEmptyRecapAndTrend() {
        seedExpense("Swiggy", 450)
        launchHome()
        navigate { compose.onNodeWithText("Insights").performClick() }
        compose.waitUntilAtLeastOneExists(hasText("Your trend appears here after your second cycle."), 10_000)
        compose.onNodeWithText("recap", substring = true).assertDoesNotExist()
    }
}
