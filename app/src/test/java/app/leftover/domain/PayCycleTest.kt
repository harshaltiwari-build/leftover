package app.leftover.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PayCycleTest {
    @Test fun cycleBeforePaydayStartsLastMonth() {
        val cycle = PayCycle.forPayday(28, LocalDate.of(2026, 10, 25))
        assertEquals(LocalDate.of(2026, 9, 28), cycle.start)
        assertEquals(LocalDate.of(2026, 10, 27), cycle.end)
        assertEquals(3, cycle.daysUntilPayday(LocalDate.of(2026, 10, 25)))
        assertEquals(28, cycle.dayOfCycle(LocalDate.of(2026, 10, 25)))
    }

    @Test fun paydayItselfStartsANewCycle() {
        val cycle = PayCycle.forPayday(28, LocalDate.of(2026, 10, 28))
        assertEquals(LocalDate.of(2026, 10, 28), cycle.start)
        assertEquals(LocalDate.of(2026, 11, 28), cycle.nextPayday)
    }

    @Test fun shortMonthsUseTheirLastDay() {
        val cycle = PayCycle.forPayday(31, LocalDate.of(2026, 2, 15))
        assertEquals(LocalDate.of(2026, 1, 31), cycle.start)
        assertEquals(LocalDate.of(2026, 2, 28), cycle.nextPayday)
    }

    @Test fun nextUnpaidBillSkipsPaidOccurrences() {
        val today = LocalDate.of(2026, 10, 25)
        assertEquals(LocalDate.of(2026, 10, 27), DueDates.nextUnpaid(27, null, today))
        assertEquals(LocalDate.of(2026, 11, 20), DueDates.nextUnpaid(20, null, today))
        assertEquals(LocalDate.of(2026, 11, 27), DueDates.nextUnpaid(27, LocalDate.of(2026, 10, 27), today))
        assertEquals(LocalDate.of(2026, 10, 20), DueDates.nextUnpaid(20, LocalDate.of(2026, 9, 20), today))
    }
}
