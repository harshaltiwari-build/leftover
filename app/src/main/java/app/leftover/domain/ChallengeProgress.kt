package app.leftover.domain

import app.leftover.data.db.ChallengeEntity
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxType
import java.time.LocalDate

/**
 * How a self-set challenge is going. There are no streaks and nothing resets: it just says how many days had no
 * spend in what the challenge covers, and which days had some.
 */
data class ChallengeProgress(
    val totalDays: Int,
    val daysSoFar: Int,
    val spendDays: List<LocalDate>,
    val finished: Boolean,
    val notStarted: Boolean,
) {
    val cleanDays: Int get() = (daysSoFar - spendDays.size).coerceAtLeast(0)
    val kept: Boolean get() = finished && spendDays.isEmpty()

    companion object {
        fun of(challenge: ChallengeEntity, transactions: List<TransactionEntity>, today: LocalDate): ChallengeProgress {
            val start = LocalDate.ofEpochDay(challenge.startEpochDay)
            val end = LocalDate.ofEpochDay(challenge.endEpochDay)
            val total = (end.toEpochDay() - start.toEpochDay() + 1).toInt().coerceAtLeast(1)
            val last = if (today.isAfter(end)) end else today
            val soFar = if (today.isBefore(start)) 0 else (last.toEpochDay() - start.toEpochDay() + 1).toInt()
            val days = transactions
                .filter { it.type == TxType.EXPENSE && (challenge.categoryId == null || it.categoryId == challenge.categoryId) }
                .map { it.occurredAt.toLocalDate() }
                .filter { !it.isBefore(start) && !it.isAfter(last) }
                .distinct().sorted()
            return ChallengeProgress(total, soFar, days, finished = today.isAfter(end), notStarted = today.isBefore(start))
        }
    }
}
