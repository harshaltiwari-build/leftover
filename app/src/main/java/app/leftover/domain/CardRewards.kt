package app.leftover.domain

import app.leftover.data.db.CardPerkEntity
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Progress towards spending enough on a card in a year to have its annual fee waived. */
data class FeeWaiver(val target: Paise, val spent: Paise, val windowStart: LocalDate, val windowEnd: LocalDate, val daysLeft: Int) {
    val remaining: Paise get() = (target - spent).coerceAtLeast(0)
    val met: Boolean get() = spent >= target
    /** What would need to go on the card each day to get there; zero once met or when the year has ended. */
    val perDayNeeded: Paise get() = if (met || daysLeft <= 0) 0 else (remaining + daysLeft - 1) / daysLeft
}

object CardRewards {
    /** The latest anniversary (day and month) that is not after [today]. A 29 Feb anniversary falls on 28 Feb in other years. */
    fun yearStart(day: Int, month: Int, today: LocalDate): LocalDate {
        fun inYear(year: Int) = LocalDate.of(year, month, 1).let { it.withDayOfMonth(day.coerceIn(1, it.lengthOfMonth())) }
        val thisYear = inYear(today.year)
        return if (thisYear.isAfter(today)) inYear(today.year - 1) else thisYear
    }

    fun waiver(perk: CardPerkEntity?, cardId: Long, transactions: List<TransactionEntity>, today: LocalDate): FeeWaiver? {
        val target = perk?.waiverSpend?.takeIf { it > 0 } ?: return null
        val start = perk.yearStartEpochDay?.let(LocalDate::ofEpochDay) ?: return null
        val end = start.plusYears(1).minusDays(1)
        val spent = transactions.filter { it.accountId == cardId && it.occurredAt.toLocalDate().let { d -> !d.isBefore(start) && !d.isAfter(end) } }.sumOf {
            when (it.type) {
                TxType.EXPENSE -> it.amount
                TxType.REFUND -> -it.amount
                else -> 0L
            }
        }.coerceAtLeast(0)
        val left = ChronoUnit.DAYS.between(today, end).toInt().coerceAtLeast(0)
        return FeeWaiver(target, spent, start, end, left)
    }

    /** Days until reward points lapse when that is within [withinDays] and they are worth something; null otherwise. */
    fun pointsLapseIn(perk: CardPerkEntity?, today: LocalDate, withinDays: Int = 60): Int? {
        if (perk == null || perk.points <= 0) return null
        val expiry = perk.pointsExpiryEpochDay?.let(LocalDate::ofEpochDay) ?: return null
        val days = ChronoUnit.DAYS.between(today, expiry).toInt()
        return days.takeIf { it in 0..withinDays }
    }

    /** How much of the credit limit is in use, 0 to beyond 1. Null without a limit. */
    fun limitUsed(perk: CardPerkEntity?, owed: Paise): Float? = perk?.creditLimit?.takeIf { it > 0 }?.let { owed.toFloat() / it }
}

/** Spots bank charges (annual fees, late fees, interest, GST on fees) by their wording. */
object FeeDetector {
    private val patterns = listOf(
        "Annual fee" to Regex("(?i)\\b(annual|renewal|joining|membership)\\s+(fee|charge)s?\\b"),
        "Late payment fee" to Regex("(?i)\\blate\\s+(payment\\s+)?(fee|charge)s?\\b"),
        "Interest charged" to Regex("(?i)\\b(finance\\s+charges?|interest\\s+(charged|on\\s+(?:your\\s+)?(?:card|outstanding|dues)))\\b"),
        "Overlimit fee" to Regex("(?i)\\bover[- ]?limit\\s+(fee|charge)s?\\b"),
        "Processing fee" to Regex("(?i)\\b(processing|convenience|service)\\s+(fee|charge)s?\\b"),
        "Forex markup" to Regex("(?i)\\bforex\\s+(mark[- ]?up|fee|charge)s?\\b"),
        "SMS alert charges" to Regex("(?i)\\bsms\\s+(alert\\s+)?(charge|fee)s?\\b"),
        "Low balance charge" to Regex("(?i)\\b(min(imum)?\\s+(avg\\.?\\s+)?balance|amb|non[- ]maintenance)\\s+(charge|fee|penalty)s?\\b"),
        "ATM fee" to Regex("(?i)\\batm\\s+(withdrawal\\s+)?(fee|charge)s?\\b"),
        "Tax on charges" to Regex("(?i)\\bgst\\s+on\\b"),
    )

    /** The kind of charge the wording describes, or null if it doesn't look like one. */
    fun detect(text: String): String? = patterns.firstOrNull { (_, regex) -> regex.containsMatchIn(text) }?.first

    fun isFee(tx: TransactionEntity): Boolean = tx.type == TxType.EXPENSE && detect("${tx.merchant} ${tx.note}") != null

    /** Fees and charges inside a cycle, newest first, with their total. */
    fun feesIn(transactions: List<TransactionEntity>, cycle: PayCycle): Pair<List<TransactionEntity>, Paise> {
        val fees = transactions.filter { isFee(it) && cycle.contains(it.occurredAt.toLocalDate()) }.sortedByDescending { it.occurredAt }
        return fees to fees.sumOf { it.amount }
    }
}
