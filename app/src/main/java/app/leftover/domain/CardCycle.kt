package app.leftover.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Where a credit card stands in its billing cycle.
 * [billed] is on the last statement and due on [billDue]; [unbilled] is spent since and lands on the next statement.
 */
data class CardCycleInfo(
    val lastStatement: LocalDate,
    val nextStatement: LocalDate,
    val billed: Paise,
    val billDue: LocalDate,
    val unbilled: Paise,
    /** Days from today to when a purchase made today has to be paid. */
    val interestFreeDays: Int,
    val nextBillDue: LocalDate,
)

object CardCycle {
    private fun occurrence(day: Int, month: YearMonth): LocalDate = month.atDay(day.coerceIn(1, month.lengthOfMonth()))

    /** The latest statement date on or before [today]. */
    fun lastStatement(statementDay: Int, today: LocalDate): LocalDate {
        val thisMonth = occurrence(statementDay, YearMonth.from(today))
        return if (thisMonth.isAfter(today)) occurrence(statementDay, YearMonth.from(today).minusMonths(1)) else thisMonth
    }

    /** The first due date strictly after [statement]. */
    fun dueAfter(dueDay: Int, statement: LocalDate): LocalDate {
        val sameMonth = occurrence(dueDay, YearMonth.from(statement))
        return if (sameMonth.isAfter(statement)) sameMonth else occurrence(dueDay, YearMonth.from(statement).plusMonths(1))
    }

    /**
     * [owed] is everything owed on the card now and [unbilled] the part spent since the last statement.
     * A card with a statement day but a due day that falls before it just means the bill is due the next month.
     */
    fun info(statementDay: Int, dueDay: Int, owed: Paise, unbilled: Paise, today: LocalDate): CardCycleInfo {
        val last = lastStatement(statementDay, today)
        val next = occurrence(statementDay, YearMonth.from(last).plusMonths(1))
        val nextDue = dueAfter(dueDay, next)
        val unbilledSafe = unbilled.coerceIn(0, owed.coerceAtLeast(0))
        return CardCycleInfo(
            lastStatement = last,
            nextStatement = next,
            billed = (owed - unbilledSafe).coerceAtLeast(0),
            billDue = dueAfter(dueDay, last),
            unbilled = unbilledSafe,
            interestFreeDays = ChronoUnit.DAYS.between(today, nextDue).toInt(),
            nextBillDue = nextDue,
        )
    }
}
