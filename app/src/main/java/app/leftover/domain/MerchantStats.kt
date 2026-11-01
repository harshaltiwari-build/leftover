package app.leftover.domain

import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxSource
import app.leftover.data.db.TxType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "You and Zepto" on an entry's page: how often you've paid this payee this year, how much in all, and your usual
 * amount. Payee names match loosely ("ZEPTO." is Zepto). One-off payees have no stats.
 */
data class MerchantStats(val name: String, val count: Int, val total: Paise, val usual: Paise) {
    companion object {
        private const val SIMILAR_DAYS = 90L
        private fun key(name: String) = name.lowercase().filter { it.isLetterOrDigit() }

        fun of(tx: TransactionEntity, ledger: Ledger): MerchantStats? {
            val k = key(tx.merchant)
            if (k.isEmpty()) return null
            val year = ledger.today.year
            val same = ledger.transactions.filter { it.type == TxType.EXPENSE && key(it.merchant) == k && it.occurredAt.toLocalDate().year == year }
            if (same.size < 2) return null
            val amounts = same.map { it.amount }.sorted()
            return MerchantStats(tx.merchant.trim(), same.size, amounts.sum(), amounts[(amounts.size - 1) / 2])
        }

        /**
         * Up to three spends like this one from the last 90 days: the same payee first, newest first, then the same
         * category at a similar price (within 30%).
         */
        fun similar(tx: TransactionEntity, ledger: Ledger, limit: Int = 3): List<TransactionEntity> {
            val since = ledger.today.minusDays(SIMILAR_DAYS)
            val recent = ledger.transactions.filter { it.id != tx.id && it.type == TxType.EXPENSE && !it.occurredAt.toLocalDate().isBefore(since) }
            val k = key(tx.merchant)
            val sameMerchant = if (k.isEmpty()) emptyList() else recent.filter { key(it.merchant) == k }.sortedByDescending { it.occurredAt }
            val low = tx.amount * 7 / 10
            val high = tx.amount * 13 / 10
            val sameKind = recent.filter { it.categoryId == tx.categoryId && tx.categoryId != null && it.amount in low..high && it !in sameMerchant }
                .sortedByDescending { it.occurredAt }
            return (sameMerchant + sameKind).take(limit)
        }

        private val whenFormat = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)

        /** For an entry read from a screenshot: what was found, as label and value. Empty for other entries. */
        fun readFields(tx: TransactionEntity, zone: ZoneId = ZoneId.systemDefault()): List<Pair<String, String>> {
            if (tx.source != TxSource.SCAN) return emptyList()
            return buildList {
                if (tx.merchant.isNotBlank()) add("Paid to" to tx.merchant)
                add("Amount" to Money.format(tx.amount))
                add("When" to Instant.ofEpochMilli(tx.occurredAt).atZone(zone).format(whenFormat).replace("AM", "am").replace("PM", "pm"))
                tx.note.takeIf { it.startsWith("UPI ref") }?.removePrefix("UPI ref")?.trim()?.takeIf { it.isNotEmpty() }?.let { add("UPI ref" to it) }
            }
        }
    }
}
