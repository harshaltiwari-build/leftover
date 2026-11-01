package app.leftover.domain

import app.leftover.data.db.CategoryKind

enum class BudgetPace { ON_PACE, WATCH, OVER }

/**
 * One spending number for the cycle: what you said you'd spend (Plan → Budget), or the sum of your category limits
 * if you haven't set one. Bills, rent and EMIs aren't in it; this is day-to-day spending only.
 */
data class MonthlyBudget(
    val budget: Paise,
    val spent: Paise,
    /** Share of the cycle gone, 0..1. */
    val pace: Float,
    val daysLeft: Int,
    val projected: Paise,
    /** True when the amount was set by you rather than added up from category limits. */
    val fromSetting: Boolean,
) {
    val left: Paise get() = budget - spent
    /** Share of the budget used, 0..1, for rings and bars. */
    val used: Float get() = if (budget <= 0) 0f else (spent.toFloat() / budget).coerceIn(0f, 1f)
    val projectedOver: Paise get() = ((projected - budget).coerceAtLeast(0) / 100_00) * 100_00
    /** What's left shared over the days to payday, rounded down to the rupee. */
    val perDayLeft: Paise get() = (left.coerceAtLeast(0) / daysLeft.coerceAtLeast(1)) / 100 * 100

    val status: BudgetPace get() {
        val share = if (budget <= 0) 0f else spent.toFloat() / budget
        return when {
            spent > budget || share > pace + 0.15f -> BudgetPace.OVER
            share > pace -> BudgetPace.WATCH
            else -> BudgetPace.ON_PACE
        }
    }

    companion object {
        /** The largest budget accepted: ₹1 crore a cycle. */
        const val MAX: Paise = 1_00_00_000L * 100

        /** What someone typed, as a budget: whole rupees, ignoring ₹ and commas. Null when empty, zero or over [MAX]. */
        fun parseInput(text: String): Paise? {
            val digits = text.filter(Char::isDigit).trimStart('0')
            if (digits.isEmpty() || digits.length > 9) return null
            return (digits.toLong() * 100).takeIf { it <= MAX }
        }

        fun of(ledger: Ledger, setAmount: Paise): MonthlyBudget? {
            val limits = ledger.categories.filter { it.kind == CategoryKind.EXPENSE && !it.hidden }.sumOf { it.budgetLimit ?: 0L }
            val budget = if (setAmount > 0) setAmount else limits
            if (budget <= 0) return null
            val cycle = ledger.cycle
            val day = cycle.dayOfCycle(ledger.today).coerceAtLeast(1)
            val spent = ledger.totalSpent().coerceAtLeast(0)
            return MonthlyBudget(
                budget = budget,
                spent = spent,
                pace = (day.toFloat() / cycle.totalDays).coerceIn(0f, 1f),
                daysLeft = cycle.daysUntilPayday(ledger.today),
                projected = spent * cycle.totalDays / day,
                fromSetting = setAmount > 0,
            )
        }

        /** A starting amount to offer: what the last cycle cost, rounded up to the next ₹500. Null with no history. */
        fun suggestion(ledger: Ledger): Paise? {
            val last = ledger.totalSpent(ledger.cycle.previous())
            if (last <= 0) return null
            val step = 500_00L
            return (last + step - 1) / step * step
        }
    }
}
