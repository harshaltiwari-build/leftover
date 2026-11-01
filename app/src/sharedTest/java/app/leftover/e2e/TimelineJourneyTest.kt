package app.leftover.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.leftover.data.db.BillEntity
import app.leftover.domain.rupees
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** From Home's "How?" to the day-by-day view: each bill on its date and what's left after it, ending at payday. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class TimelineJourneyTest : JourneyBase() {
    @Test fun leftToSpendCanBeFollowedDayByDay() {
        // Due tomorrow, unless tomorrow is payday's month change; payday is the 1st, so pick a day safely inside the cycle.
        val due = LocalDate.now().plusDays(1).let { if (it.dayOfMonth == 1) it.plusDays(1) else it }.dayOfMonth
        runBlocking { app.repository.saveBill(BillEntity(name = "Netflix", amount = rupees(649), dueDay = due, icon = "tv")) }
        launchHome()
        compose.onNodeWithText("How?").performClick()
        navigate { compose.onNodeWithText("Day by day until payday →").performScrollTo().performClick() }
        waitForText("Until payday")
        compose.onNodeWithText("In your accounts now").assertExists()
        compose.onNodeWithText("Netflix").assertExists()
        compose.onNodeWithText("₹9,351 after").assertExists()
        compose.onNodeWithText("Payday").performScrollTo().assertExists()
    }
}
