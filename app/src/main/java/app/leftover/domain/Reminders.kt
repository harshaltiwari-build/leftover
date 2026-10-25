package app.leftover.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Which reminders the user has switched on. All off until they ask. */
data class ReminderPrefs(val bills: Boolean = false, val payday: Boolean = false, val budgets: Boolean = false, val weekly: Boolean = false) {
    val any: Boolean get() = bills || payday || budgets || weekly
}

enum class ReminderKind { BILL, PAYDAY, BUDGET, WEEKLY }

/**
 * One notification to show. [key] is unique per occasion (a bill's due date, a cycle's budget), so the same
 * reminder is never sent twice. [route] is the screen it opens.
 */
data class Reminder(val key: String, val kind: ReminderKind, val title: String, val body: String, val route: String)

/** Works out which reminders are due today. Pure, so it can be tested without a phone. */
object ReminderPlanner {
    const val BUDGET_WARN_AT = 0.9

    fun plan(ledger: Ledger, prefs: ReminderPrefs, hideAmounts: Boolean, alreadySent: Set<String>): List<Reminder> {
        val today = ledger.today
        val out = mutableListOf<Reminder>()

        if (prefs.bills) {
            ledger.bills.filter { !it.archived }.forEach { bill ->
                val due = ledger.billNextDue(bill)
                val days = ChronoUnit.DAYS.between(today, due).toInt()
                val key = "bill:${bill.id}:${due.toEpochDay()}"
                if (days in 0..bill.remindDaysBefore.coerceAtLeast(0) && key !in alreadySent) {
                    val whenText = when (days) { 0 -> "today"; 1 -> "tomorrow"; else -> "in $days days" }
                    out += Reminder(key, ReminderKind.BILL, "${bill.name} is due $whenText", if (hideAmounts) "Open Leftover to see the amount" else Money.format(bill.amount), "bills")
                }
            }
        }

        if (prefs.payday) {
            val key = "payday:${ledger.cycle.start.toEpochDay()}"
            if (today == ledger.cycle.start && key !in alreadySent) {
                out += Reminder(key, ReminderKind.PAYDAY, "It's payday", "A good moment to set aside for your goals and check the bills coming up.", "home")
            }
        }

        if (prefs.budgets) {
            ledger.categories.filter { it.kind == app.leftover.data.db.CategoryKind.EXPENSE && !it.hidden && (it.budgetLimit ?: 0) > 0 }.forEach { category ->
                val limit = category.budgetLimit!!
                val spent = ledger.spent(category.id)
                val key = "budget:${category.id}:${ledger.cycle.start.toEpochDay()}"
                if (spent >= limit * BUDGET_WARN_AT && key !in alreadySent) {
                    val over = spent > limit
                    val title = if (over) "${category.name} is over its limit" else "${category.name} is nearly at its limit"
                    val body = if (hideAmounts) "Open Leftover to see where it stands" else "${Money.format(spent)} of ${Money.format(limit)} used this cycle"
                    out += Reminder(key, ReminderKind.BUDGET, title, body, "plan")
                }
            }
        }

        if (prefs.weekly) {
            val key = "weekly:${today.toEpochDay()}"
            if (today.dayOfWeek == DayOfWeek.SUNDAY && key !in alreadySent) {
                out += Reminder(key, ReminderKind.WEEKLY, "Your week in money", "Two minutes to look back at the week.", "checkin")
            }
        }
        return out
    }

    /** Drops sent-keys older than [keepDays], so the list doesn't grow forever. Keys are stored as `key|epochDay`. */
    fun prune(sent: Set<String>, today: LocalDate, keepDays: Long = 45): Set<String> =
        sent.filter { (it.substringAfterLast('|').toLongOrNull() ?: 0L) >= today.toEpochDay() - keepDays }.toSet()
}
