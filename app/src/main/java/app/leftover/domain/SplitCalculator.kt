package app.leftover.domain

/** The person id that stands for "you" in shares, payers and settlements. Real people start at 1. */
const val ME = 0L

enum class SplitMode { EQUAL, EXACT, SHARES }

data class SplitResult(
    /** What each participant owes toward the bill. Always whole paise. */
    val amounts: Map<Long, Paise>,
    val total: Paise,
) {
    val assigned: Paise get() = amounts.values.sum()

    /** Positive: still to assign. Negative: more than the bill. */
    val difference: Paise get() = total - assigned

    val balanced: Boolean get() = amounts.isNotEmpty() && difference == 0L
}

/** Divides a bill between people. Every paise is accounted for, so the parts always add back up to the total. */
object SplitCalculator {
    fun calculate(
        total: Paise,
        participants: List<Long>,
        mode: SplitMode,
        exact: Map<Long, Paise> = emptyMap(),
        shares: Map<Long, Int> = emptyMap(),
    ): SplitResult {
        if (participants.isEmpty() || total <= 0) return SplitResult(emptyMap(), total.coerceAtLeast(0))
        val amounts = when (mode) {
            SplitMode.EQUAL -> distribute(total, participants.associateWith { 1 })
            SplitMode.EXACT -> participants.associateWith { exact[it] ?: 0L }
            SplitMode.SHARES -> distribute(total, participants.associateWith { (shares[it] ?: 1).coerceAtLeast(0) })
        }
        return SplitResult(amounts, total)
    }

    /**
     * Splits [total] in proportion to [weights] using the largest-remainder method, so rounding never loses or invents paise.
     * Ties go to the earlier participant.
     */
    fun distribute(total: Paise, weights: Map<Long, Int>): Map<Long, Paise> {
        val sum = weights.values.sumOf { it.toLong() }
        if (sum <= 0) return weights.mapValues { 0L }
        val base = weights.mapValues { (_, w) -> total * w / sum }
        var leftover = total - base.values.sum()
        val order = weights.keys.sortedByDescending { id -> (total * weights.getValue(id)) % sum }
        val extra = HashMap<Long, Long>()
        for (id in order) {
            if (leftover <= 0) break
            if (weights.getValue(id) > 0) {
                extra[id] = 1
                leftover--
            }
        }
        return base.mapValues { (id, value) -> value + (extra[id] ?: 0L) }
    }
}
