package app.leftover.domain

/** One line of the "How is this calculated?" breakdown. */
data class Deduction(val label: String, val amount: Paise, val detail: String = "")

/**
 * The single number the app is built around: money you can spend before payday without touching
 * bills, card dues or money set aside for goals. Every input is shown to the user so the number can be trusted.
 */
data class LeftToSpend(
    val inAccounts: Paise,
    val bills: List<Deduction>,
    val cardDues: List<Deduction>,
    val goals: List<Deduction>,
    val comingBack: Paise,
    val daysUntilPayday: Int,
    /** What you owe friends. Shown for awareness; it is not subtracted because there is no due date. */
    val owedByYou: Paise = 0,
) {
    val billsTotal: Paise get() = bills.sumOf { it.amount }
    val cardDuesTotal: Paise get() = cardDues.sumOf { it.amount }
    val goalsTotal: Paise get() = goals.sumOf { it.amount }
    val left: Paise get() = inAccounts - billsTotal - cardDuesTotal - goalsTotal

    /** Rounded down to the rupee so the daily figure never promises more than is there. */
    val perDay: Paise get() = if (left <= 0) 0 else (left / daysUntilPayday) / 100 * 100

    /** Share of [inAccounts] in each slice, for the split bar on Home. Values sum to 1 when there is money. */
    fun slices(): List<Float> {
        if (inAccounts <= 0) return listOf(0f, 0f, 0f, 0f)
        val total = inAccounts.toFloat()
        return listOf(billsTotal / total, cardDuesTotal / total, goalsTotal / total, left.coerceAtLeast(0) / total)
            .map { it.coerceIn(0f, 1f) }
    }
}
