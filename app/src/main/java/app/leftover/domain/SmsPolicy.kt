package app.leftover.domain

import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxSource
import app.leftover.data.db.TxType
import kotlin.math.abs

/** Why a parsed text waits for the user instead of being saved on its own. */
enum class PendingReason { NEW_SENDER, NEW_MERCHANT, NEW_ACCOUNT, POSSIBLE_TRANSFER, POSSIBLE_DUPLICATE, UNSURE, CREDIT, FROM_APP, ASK_FIRST }

sealed interface SmsDecision {
    data object AutoSave : SmsDecision
    data class Review(val reason: PendingReason) : SmsDecision
}

/** What is known about the text beyond its words. */
data class SmsContext(
    val senderTrusted: Boolean,
    val accountId: Long?,
    val categoryId: Long?,
    val duplicateOfExisting: Boolean,
)

/**
 * Decides whether a parsed text may be saved without asking. Saving unasked is the exception: it needs a
 * debit the user has allowed this sender to add, read cleanly, for an account and a merchant the app already knows.
 */
object SmsPolicy {
    fun decide(parsed: ParsedSms, context: SmsContext): SmsDecision = when {
        parsed.possibleTransfer -> SmsDecision.Review(PendingReason.POSSIBLE_TRANSFER)
        context.duplicateOfExisting -> SmsDecision.Review(PendingReason.POSSIBLE_DUPLICATE)
        parsed.kind == SmsKind.CREDIT -> SmsDecision.Review(PendingReason.CREDIT)
        parsed.confidence == Confidence.LOW -> SmsDecision.Review(PendingReason.UNSURE)
        context.accountId == null -> SmsDecision.Review(PendingReason.NEW_ACCOUNT)
        context.categoryId == null -> SmsDecision.Review(PendingReason.NEW_MERCHANT)
        !context.senderTrusted -> SmsDecision.Review(PendingReason.NEW_SENDER)
        else -> SmsDecision.AutoSave
    }

    private fun normal(name: String) = name.lowercase().filter { it.isLetterOrDigit() }

    /** The category of the first rule whose text appears in the merchant's name, ignoring case and punctuation. */
    fun ruleCategory(merchant: String, rules: List<Pair<String, Long>>): Long? {
        val name = normal(merchant)
        if (name.isEmpty()) return null
        return rules.firstOrNull { (pattern, _) -> normal(pattern).let { it.isNotEmpty() && name.contains(it) } }?.second
    }

    /** The category the user most recently gave to this merchant, so a repeat merchant needs no question. */
    fun categoryFor(merchant: String, history: List<TransactionEntity>): Long? {
        val key = normal(merchant)
        if (key.isEmpty()) return null
        return history.filter { it.type == TxType.EXPENSE && it.categoryId != null && normal(it.merchant) == key }
            .maxByOrNull { it.occurredAt }?.categoryId
    }

    /** A manual entry for the same amount around the same time means the user probably logged this already. */
    fun looksLikeExisting(parsed: ParsedSms, at: Long, history: List<TransactionEntity>, windowMillis: Long = 6 * 3_600_000L): Boolean {
        val types = if (parsed.kind == SmsKind.DEBIT) setOf(TxType.EXPENSE) else setOf(TxType.INCOME, TxType.REFUND)
        return history.any { it.type in types && it.amount == parsed.amount && it.source != TxSource.SMS && abs(it.occurredAt - at) <= windowMillis }
    }
}
