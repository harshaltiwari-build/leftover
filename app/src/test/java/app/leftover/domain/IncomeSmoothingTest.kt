package app.leftover.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomeSmoothingTest {
    @Test fun theLeanestMonthIsTheFloor() {
        val plan = IncomeSmoothing.plan(listOf(rupees(30000), rupees(12000), rupees(45000)), rupees(20000))!!
        assertEquals(rupees(12000), plan.floor)
        assertEquals(rupees(29000), plan.average)
        assertEquals(rupees(8000), plan.extra)
        assertEquals(3, plan.cyclesUsed)
    }

    @Test fun anEmptyCycleIsNotTreatedAsALeanOne() {
        val plan = IncomeSmoothing.plan(listOf(0, rupees(20000), 0, rupees(30000)), rupees(5000))!!
        assertEquals(rupees(20000), plan.floor)
        assertEquals(0L, plan.extra)
    }

    @Test fun oneMonthOfHistoryIsNotEnough() {
        assertNull(IncomeSmoothing.plan(listOf(rupees(20000)), rupees(1000)))
        assertNull(IncomeSmoothing.plan(emptyList(), 0))
    }
}
