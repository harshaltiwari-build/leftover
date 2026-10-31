package app.leftover.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TripDatesTest {
    private val start = LocalDate.of(2026, 12, 18)
    private val end = LocalDate.of(2026, 12, 22)

    @Test fun `before, during and after a trip`() {
        assertEquals("18 – 22 Dec · in 54 days", TripDates.describe(start, end, LocalDate.of(2026, 10, 25)))
        assertEquals("18 – 22 Dec · tomorrow", TripDates.describe(start, end, start.minusDays(1)))
        assertEquals("18 – 22 Dec · day 2 of 5", TripDates.describe(start, end, start.plusDays(1)))
        assertEquals("18 – 22 Dec · ended 3 days ago", TripDates.describe(start, end, end.plusDays(3)))
    }

    @Test fun `ranges across months and single days`() {
        assertEquals("30 Dec – 2 Jan", TripDates.range(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 2)))
        assertEquals("5 Nov · today", TripDates.describe(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 5)))
    }

    @Test fun `budget share splits evenly`() {
        assertEquals(rupees(10_000), TripDates.budgetShare(rupees(50_000), 5))
    }
}
