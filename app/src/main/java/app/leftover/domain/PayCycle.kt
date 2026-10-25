package app.leftover.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * A budgeting cycle that runs from one payday to the day before the next.
 * Budgets, goals and "Left to spend" are all measured against the current cycle instead of calendar months.
 */
data class PayCycle(val start: LocalDate, val end: LocalDate, val nextPayday: LocalDate) {

    val totalDays: Int get() = (ChronoUnit.DAYS.between(start, end) + 1).toInt()

    fun dayOfCycle(today: LocalDate): Int = (ChronoUnit.DAYS.between(start, today) + 1).toInt()

    /** Days left to make the money last, counting today. Never less than 1. */
    fun daysUntilPayday(today: LocalDate): Int = ChronoUnit.DAYS.between(today, nextPayday).toInt().coerceAtLeast(1)

    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    /** The same span of days in the previous cycle, used for fair "by day N" comparisons. */
    fun previous(): PayCycle = forPayday(start.dayOfMonth.coerceAtLeast(1), start.minusDays(1))

    companion object {
        /** [paydayDay] is the day of month the user is paid (1–31); short months use their last day. */
        fun forPayday(paydayDay: Int, today: LocalDate): PayCycle {
            fun paydayIn(month: YearMonth): LocalDate = month.atDay(paydayDay.coerceIn(1, month.lengthOfMonth()))
            val month = YearMonth.from(today)
            val thisMonthsPayday = paydayIn(month)
            val start = if (today.isBefore(thisMonthsPayday)) paydayIn(month.minusMonths(1)) else thisMonthsPayday
            val next = paydayIn(YearMonth.from(start).plusMonths(1))
            return PayCycle(start = start, end = next.minusDays(1), nextPayday = next)
        }
    }
}

/** Monthly due dates for bills and card statements. */
object DueDates {
    fun occurrence(dueDay: Int, month: YearMonth): LocalDate = month.atDay(dueDay.coerceIn(1, month.lengthOfMonth()))

    /**
     * The next due date that has not been paid yet.
     * [paidThrough] is the due date of the last occurrence the user paid; null means no payments recorded,
     * in which case an occurrence earlier this month is assumed to be already handled.
     */
    fun nextUnpaid(dueDay: Int, paidThrough: LocalDate?, today: LocalDate): LocalDate {
        if (paidThrough == null) {
            val thisMonth = occurrence(dueDay, YearMonth.from(today))
            return if (thisMonth.isBefore(today)) occurrence(dueDay, YearMonth.from(today).plusMonths(1)) else thisMonth
        }
        var month = YearMonth.from(paidThrough)
        var candidate = occurrence(dueDay, month)
        while (!candidate.isAfter(paidThrough)) {
            month = month.plusMonths(1)
            candidate = occurrence(dueDay, month)
        }
        return candidate
    }
}
