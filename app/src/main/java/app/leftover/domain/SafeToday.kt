package app.leftover.domain

import app.leftover.data.db.CategoryEntity
import app.leftover.data.db.CategoryKind

/**
 * Today's spending target: what was left this morning, shared evenly over the days to payday (today included),
 * rounded down to the rupee. Bills, card dues and goal money are already out of "left", so the target never spends them.
 */
data class SafeToday(
    /** Safe to spend today, fixed for the day. */
    val target: Paise,
    /** Your own spending dated today. */
    val spentToday: Paise,
    /** Days to payday, today included. */
    val days: Int,
    /** Left to spend right now, after today's spending. */
    val leftNow: Paise,
) {
    /** What's still free today. Negative when today went over. */
    val leftToday: Paise get() = target - spentToday
    val over: Boolean get() = spentToday > target

    /** Tomorrow's target if nothing more is spent today, or null when tomorrow is payday and a new cycle starts. */
    val tomorrow: Paise? get() = if (days <= 1) null else roundDown(leftNow.coerceAtLeast(0) / (days - 1))

    /** Share of today's target already spent, 0..1, for the ring. */
    val fraction: Float get() = if (target <= 0) 1f else (spentToday.toFloat() / target).coerceIn(0f, 1f)

    companion object {
        private fun roundDown(p: Paise): Paise = p / 100 * 100

        fun of(ledger: Ledger): SafeToday {
            val today = ledger.today
            val spent = ActivityFilter.daySpend(ledger.transactions.filter { it.occurredAt.toLocalDate() == today }).coerceAtLeast(0)
            val left = ledger.leftToSpend.left
            val days = ledger.cycle.daysUntilPayday(today)
            val target = roundDown(((left + spent).coerceAtLeast(0)) / days)
            return SafeToday(target, spent, days, left)
        }
    }
}

/** A category heading over its limit at today's pace, with the daily amount that would hold it. */
data class PaceWarning(val category: CategoryEntity, val spent: Paise, val limit: Paise, val projectedOver: Paise, val perDayToHold: Paise) {
    val alreadyOver: Boolean get() = spent > limit

    fun text(hidden: Boolean): String = when {
        hidden && alreadyOver -> "${category.name} is over its limit this cycle."
        hidden -> "At this pace ${category.name} goes over its limit this cycle."
        alreadyOver -> "${category.name} is ${Money.format(spent - limit)} over its ${Money.format(limit)} limit this cycle."
        else -> "At this pace ${category.name} ends about ${Money.format(projectedOver)} over. Keeping to ${Money.format(perDayToHold)} a day holds it at ${Money.format(limit)}."
    }

    companion object {
        /** Projected overshoots under ₹100 aren't worth a warning. */
        private const val MIN_OVER: Paise = 100_00

        /** The category with the biggest projected overshoot, or null when every budget is on pace. Waits until day 3 of a cycle. */
        fun worst(ledger: Ledger): PaceWarning? {
            val day = ledger.cycle.dayOfCycle(ledger.today)
            if (day < 3) return null
            val total = ledger.cycle.totalDays
            val daysLeft = ledger.cycle.daysUntilPayday(ledger.today)
            return ledger.categories
                .filter { it.kind == CategoryKind.EXPENSE && !it.hidden && (it.budgetLimit ?: 0) > 0 }
                .mapNotNull { cat ->
                    val limit = cat.budgetLimit!!
                    val spent = ledger.spent(cat.id).coerceAtLeast(0)
                    val projected = spent * total / day
                    val over = projected - limit
                    if (over < MIN_OVER && spent <= limit) null
                    else PaceWarning(cat, spent, limit, (over / 10000) * 10000, (((limit - spent).coerceAtLeast(0)) / daysLeft) / 100 * 100)
                }
                .maxByOrNull { it.projectedOver }
        }
    }
}
