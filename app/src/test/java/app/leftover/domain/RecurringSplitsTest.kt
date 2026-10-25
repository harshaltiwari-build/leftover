package app.leftover.domain

import app.leftover.data.db.RecurringSplitEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringSplitsTest {
    private fun rule(dueDay: Int, last: LocalDate?, people: String = "1,2", includesMe: Boolean = true) =
        RecurringSplitEntity(title = "Rent", total = 30_000_00, paidBy = ME, participants = people, includesMe = includesMe, dueDay = dueDay, lastCreatedEpochDay = last?.toEpochDay())

    @Test fun `nothing is due in the month it was created`() =
        assertTrue(RecurringSplits.due(rule(10, LocalDate.of(2026, 10, 10)), LocalDate.of(2026, 10, 25)).isEmpty())

    @Test fun `next month's due day creates one`() =
        assertEquals(listOf(LocalDate.of(2026, 11, 10)), RecurringSplits.due(rule(10, LocalDate.of(2026, 10, 10)), LocalDate.of(2026, 11, 12)))

    @Test fun `not before the due day`() =
        assertTrue(RecurringSplits.due(rule(10, LocalDate.of(2026, 10, 10)), LocalDate.of(2026, 11, 9)).isEmpty())

    @Test fun `catch up is capped at three months`() {
        val due = RecurringSplits.due(rule(5, LocalDate.of(2026, 1, 5)), LocalDate.of(2026, 10, 25))
        assertEquals(listOf(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 9, 5), LocalDate.of(2026, 10, 5)), due)
    }
    @Test fun `short months use their last day`() =
        assertEquals(listOf(LocalDate.of(2026, 2, 28)), RecurringSplits.due(rule(31, LocalDate.of(2026, 1, 31)), LocalDate.of(2026, 3, 1)))

    @Test fun `next date`() {
        assertEquals(LocalDate.of(2026, 11, 10), RecurringSplits.next(rule(10, LocalDate.of(2026, 10, 10)), LocalDate.of(2026, 10, 25)))
        assertEquals(LocalDate.of(2026, 10, 25), RecurringSplits.next(rule(5, LocalDate.of(2026, 9, 5)), LocalDate.of(2026, 10, 25)))
    }
    @Test fun `shares add up and include you`() {
        val shares = RecurringSplits.shares(rule(10, null, "1,2,3"))
        assertEquals(30_000_00L, shares.values.sum()); assertEquals(setOf(ME, 1L, 2L, 3L), shares.keys)
        assertEquals(setOf(1L, 2L), RecurringSplits.shares(rule(10, null, "1,2", includesMe = false)).keys)
    }
}
