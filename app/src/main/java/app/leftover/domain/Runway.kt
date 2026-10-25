package app.leftover.domain

import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class RunwayStatus {
    /** Not enough history yet to say anything useful. */
    NOT_ENOUGH_DATA,

    /** At the recent pace the money lasts until payday. */
    REACHES_PAYDAY,

    /** At the recent pace the money runs out before payday. */
    RUNS_OUT,
}

data class Runway(val status: RunwayStatus, val runsOutOn: LocalDate?, val pacePerDay: Paise)

/**
 * "At this pace you'll run out on the 22nd." The pace is the average daily spending over the last few days, not a promise:
 * bills the user marked as paid are left out because Left to spend already counts them separately.
 */
object RunwayCalc {
    const val WINDOW_DAYS = 7

    /** Fewer days of history than this and a projection would be noise. */
    const val MIN_HISTORY_DAYS = 3

    fun project(
        left: Paise,
        transactions: List<TransactionEntity>,
        billNames: Set<String>,
        today: LocalDate,
        nextPayday: LocalDate,
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    ): Runway {
        val first = transactions.minOfOrNull { it.occurredAt }?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            ?: return Runway(RunwayStatus.NOT_ENOUGH_DATA, null, 0)
        val history = (ChronoUnit.DAYS.between(first, today) + 1).toInt()
        if (history < MIN_HISTORY_DAYS) return Runway(RunwayStatus.NOT_ENOUGH_DATA, null, 0)
        val window = history.coerceAtMost(WINDOW_DAYS)
        val since = today.minusDays(window - 1L)
        val names = billNames.map { it.trim().lowercase() }.toSet()
        val spent = transactions.filter { tx ->
            val date = java.time.Instant.ofEpochMilli(tx.occurredAt).atZone(zone).toLocalDate()
            !date.isBefore(since) && !date.isAfter(today) && !tx.excludeFromBudgets && tx.merchant.trim().lowercase() !in names
        }.sumOf {
            when (it.type) {
                TxType.EXPENSE -> it.amount - it.splitOthers
                TxType.REFUND -> -it.amount
                else -> 0L
            }
        }.coerceAtLeast(0)
        val pace = spent / window
        val daysToPayday = ChronoUnit.DAYS.between(today, nextPayday).toInt().coerceAtLeast(1)
        if (pace <= 0) return Runway(RunwayStatus.REACHES_PAYDAY, null, 0)
        if (left <= 0) return Runway(RunwayStatus.RUNS_OUT, today, pace)
        val covered = (left / pace).toInt()
        return if (covered >= daysToPayday) Runway(RunwayStatus.REACHES_PAYDAY, null, pace)
        else Runway(RunwayStatus.RUNS_OUT, today.plusDays(covered.toLong()), pace)
    }
}

/** What a purchase costs in terms of a goal: the same money, expressed as days of saving. */
object GoalImpact {
    /** Days of saving at [perCycle] per [cycleDays] that [price] equals; null when nothing is being set aside. */
    fun daysCloser(price: Paise, perCycle: Paise, cycleDays: Int): Int? {
        if (perCycle <= 0 || price <= 0 || cycleDays <= 0) return null
        return (price.toDouble() * cycleDays / perCycle).toInt().takeIf { it >= 1 }
    }
}
