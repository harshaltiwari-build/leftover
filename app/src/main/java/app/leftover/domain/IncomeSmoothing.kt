package app.leftover.domain

/**
 * For freelancers and gig workers whose income swings: plan on the leanest recent month, and treat anything above it as a buffer
 * for slow months. [floor] is the lowest of the recent cycles that had income, [average] their mean, and [extra] how far this cycle's
 * income already sits above the floor.
 */
data class IncomePlan(val floor: Paise, val average: Paise, val thisCycle: Paise, val extra: Paise, val cyclesUsed: Int)

object IncomeSmoothing {
    /** Two cycles with income are needed before a floor means anything. */
    const val MIN_CYCLES = 2

    /** [completed] are the incomes of earlier cycles, oldest first; cycles with no income logged are ignored. */
    fun plan(completed: List<Paise>, thisCycle: Paise): IncomePlan? {
        val earned = completed.filter { it > 0 }
        if (earned.size < MIN_CYCLES) return null
        val floor = earned.min()
        return IncomePlan(
            floor = floor,
            average = earned.sum() / earned.size,
            thisCycle = thisCycle,
            extra = (thisCycle - floor).coerceAtLeast(0),
            cyclesUsed = earned.size,
        )
    }
}
