package app.leftover.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class GoalPaceTest {
    private val payday = LocalDate.of(2026, 10, 28)

    @Test fun cyclesRoundUp() {
        assertEquals(5, GoalPace.cyclesNeeded(rupees(30000), rupees(6000)))
        assertEquals(6, GoalPace.cyclesNeeded(rupees(30001), rupees(6000)))
        assertEquals(0, GoalPace.cyclesNeeded(0, rupees(6000)))
        assertNull(GoalPace.cyclesNeeded(rupees(100), 0))
    }

    @Test fun firstDepositIsThisPayday() {
        assertEquals(LocalDate.of(2026, 10, 28), GoalPace.finishDate(payday, 1))
        assertEquals(LocalDate.of(2027, 2, 28), GoalPace.finishDate(payday, 5))
    }

    @Test fun halfwayGoalIsOnTrackWithoutADate() {
        val p = GoalPace.progress(rupees(30000), rupees(60000), rupees(6000), null, payday)
        assertEquals(GoalStatus.STEADY, p.status)
        assertEquals(50, p.percent)
        assertEquals(LocalDate.of(2027, 2, 28), p.finishDate)
    }

    @Test fun targetDateMissedShowsTheShortfall() {
        val p = GoalPace.progress(rupees(3600), rupees(12000), rupees(1000), LocalDate.of(2027, 1, 28), payday)
        assertEquals(GoalStatus.BEHIND, p.status)
        assertEquals(rupees(1100), p.shortfallPerCycle)
    }

    @Test fun targetDateMetIsOnTrack() {
        // ₹6,300 to go and two paydays (28 Oct, 28 Nov) before 18 Dec: ₹3,200 each covers it.
        val p = GoalPace.progress(rupees(11700), rupees(18000), rupees(3200), LocalDate.of(2026, 12, 18), payday)
        assertEquals(GoalStatus.ON_TRACK, p.status)
        assertEquals(LocalDate.of(2026, 11, 28), p.finishDate)
        assertEquals(0L, p.shortfallPerCycle)
    }

    @Test fun targetDateNeedsMoreThanTwoThousandWhenOnlyTwoPaydaysRemain() {
        val p = GoalPace.progress(rupees(11700), rupees(18000), rupees(2000), LocalDate.of(2026, 12, 18), payday)
        assertEquals(GoalStatus.BEHIND, p.status)
        assertEquals(rupees(1150), p.shortfallPerCycle)
    }

    @Test fun finishedGoalIsDone() {
        assertEquals(GoalStatus.DONE, GoalPace.progress(rupees(60000), rupees(60000), rupees(6000), null, payday).status)
    }
}
