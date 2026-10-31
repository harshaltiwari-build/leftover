package app.leftover.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** The date line on a trip or event: "18 – 22 Dec · in 54 days", "Day 2 of 5" or "Ended 3 days ago". */
object TripDates {
    fun describe(start: LocalDate, end: LocalDate, today: LocalDate): String {
        val range = range(start, end)
        val total = ChronoUnit.DAYS.between(start, end).toInt() + 1
        return when {
            today.isBefore(start) -> {
                val days = ChronoUnit.DAYS.between(today, start).toInt()
                "$range · " + if (days == 1) "tomorrow" else "in $days days"
            }
            today.isAfter(end) -> {
                val days = ChronoUnit.DAYS.between(end, today).toInt()
                "$range · ended " + if (days == 1) "yesterday" else "$days days ago"
            }
            total == 1 -> "$range · today"
            else -> "$range · day ${ChronoUnit.DAYS.between(start, today).toInt() + 1} of $total"
        }
    }

    fun range(start: LocalDate, end: LocalDate): String {
        val d = DateTimeFormatter.ofPattern("d", Locale.ENGLISH)
        val dm = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
        return when {
            start == end -> dm.format(start)
            start.month == end.month && start.year == end.year -> "${d.format(start)} – ${dm.format(end)}"
            else -> "${dm.format(start)} – ${dm.format(end)}"
        }
    }

    /** Your slice of a group budget, split evenly across everyone in the group, you included. */
    fun budgetShare(budget: Paise, people: Int): Paise = budget / people.coerceAtLeast(1)
}
