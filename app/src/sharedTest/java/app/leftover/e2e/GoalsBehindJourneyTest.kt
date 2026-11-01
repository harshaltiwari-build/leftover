package app.leftover.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.leftover.data.db.GoalEntity
import app.leftover.domain.rupees
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** A goal that won't make its date gets a strip on Goals with a one-tap fix. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class GoalsBehindJourneyTest : JourneyBase() {
    @Test fun aBehindGoalCanBeCaughtUpInOneTap() {
        val s0 = runBlocking { app.repository.snapshot.first() }
        // Three paydays to go at ₹1,000 each can't reach ₹30,000.
        val date = s0.cycle.nextPayday.plusMonths(2)
        runBlocking { app.repository.addGoal(GoalEntity(name = "Goa trip", target = rupees(30_000), perCycle = rupees(1_000), cover = "beach", targetEpochDay = date.toEpochDay())) }
        launchHome()
        navigate { compose.onNodeWithText("Plan").performClick() }
        navigate { compose.onNodeWithText("Goals").performClick() }
        waitForText("Goa trip is behind")
        compose.onNodeWithText("Save ₹10,000 a payday").performClick()
        compose.waitUntil(5_000) { runBlocking { app.repository.snapshot.first().goals.single().perCycle } == rupees(10_000) }
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("Goa trip is behind")).fetchSemanticsNodes().isEmpty() }
        assertEquals(date.toEpochDay(), runBlocking { app.repository.snapshot.first().goals.single().targetEpochDay })
    }
}
