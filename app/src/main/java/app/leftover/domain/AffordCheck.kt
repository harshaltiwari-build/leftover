package app.leftover.domain

enum class Verdict { COMFORTABLE, STRETCH, NOT_BEFORE_PAYDAY }

data class AffordResult(
    val verdict: Verdict,
    val leftAfter: Paise,
    val perDayAfter: Paise,
    /** How far over its budget the category would go if bought now; null when the category has no limit or stays within it. */
    val categoryOverBy: Paise?,
    /** How much more than is free this purchase costs, when it can't be afforded before payday. */
    val shortBy: Paise,
)

/** Answers "can I afford this?" from the current Left to spend, the days until payday and the category budget. */
object AffordCheck {
    /** Staying within this share of today's daily amount still counts as comfortable. */
    private const val COMFORT_SHARE = 0.7

    fun check(
        leftNow: Paise,
        price: Paise,
        daysUntilPayday: Int,
        categorySpent: Paise,
        categoryLimit: Paise?,
    ): AffordResult {
        val days = daysUntilPayday.coerceAtLeast(1)
        val after = leftNow - price
        val perDayNow = if (leftNow > 0) leftNow / days else 0L
        val perDayAfter = if (after > 0) after / days else 0L
        val overBy = categoryLimit?.let { (categorySpent + price - it).takeIf { over -> over > 0 } }
        val verdict = when {
            after < 0 -> Verdict.NOT_BEFORE_PAYDAY
            overBy == null && perDayAfter >= perDayNow * COMFORT_SHARE -> Verdict.COMFORTABLE
            else -> Verdict.STRETCH
        }
        return AffordResult(verdict, after, perDayAfter, overBy, if (after < 0) -after else 0)
    }
}
