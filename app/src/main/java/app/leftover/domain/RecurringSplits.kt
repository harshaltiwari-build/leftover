package app.leftover.domain

import app.leftover.data.db.RecurringSplitEntity
import java.time.LocalDate
import java.time.YearMonth

/** Works out which months a repeating shared bill still has to be created for. */
object RecurringSplits {
    /**
     * The due dates after the last one created, up to and including [today]. If the app wasn't opened for a long time,
     * only the most recent [maxCatchUp] months are created, so a forgotten bill doesn't flood the ledger.
     */
    fun due(rule: RecurringSplitEntity, today: LocalDate, maxCatchUp: Int = 3): List<LocalDate> {
        val last = rule.lastCreatedEpochDay?.let(LocalDate::ofEpochDay)
        var month = if (last == null) YearMonth.from(today) else YearMonth.from(last).plusMonths(1)
        val out = mutableListOf<LocalDate>()
        while (!month.isAfter(YearMonth.from(today))) {
            val date = DueDates.occurrence(rule.dueDay, month)
            if (!date.isAfter(today)) out += date
            month = month.plusMonths(1)
        }
        return out.takeLast(maxCatchUp)
    }

    /** The next date a split will be created, for showing in a list. */
    fun next(rule: RecurringSplitEntity, today: LocalDate): LocalDate {
        val pending = due(rule, today)
        if (pending.isNotEmpty()) return today
        val last = rule.lastCreatedEpochDay?.let(LocalDate::ofEpochDay)
        var month = if (last == null) YearMonth.from(today) else YearMonth.from(last).plusMonths(1)
        while (DueDates.occurrence(rule.dueDay, month).isBefore(today)) month = month.plusMonths(1)
        return DueDates.occurrence(rule.dueDay, month)
    }

    /** Equal shares of [total] among you (when [includesMe]) and the listed friends. */
    fun shares(rule: RecurringSplitEntity): Map<Long, Paise> {
        val people = (if (rule.includesMe) listOf(ME) else emptyList()) + rule.participantIds
        return SplitCalculator.distribute(rule.total, people.associateWith { 1 })
    }
}
