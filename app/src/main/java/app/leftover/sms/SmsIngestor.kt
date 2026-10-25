package app.leftover.sms

import app.leftover.data.LeftoverRepository
import app.leftover.data.Snapshot
import app.leftover.data.db.PendingSmsEntity
import app.leftover.data.db.TxType
import app.leftover.data.db.TxSource
import app.leftover.domain.CrossSource
import app.leftover.domain.FeeDetector
import app.leftover.domain.MandateParser
import app.leftover.domain.UpiApps
import app.leftover.domain.UpiNotificationParser
import app.leftover.domain.ParsedSms
import app.leftover.domain.PendingReason
import app.leftover.domain.SmsContext
import app.leftover.domain.SmsDecision
import app.leftover.domain.SmsKind
import app.leftover.domain.SmsParser
import app.leftover.domain.SmsPolicy
import app.leftover.domain.toLocalDate
import app.leftover.data.db.AccountType

enum class IngestResult { IGNORED, DUPLICATE, SAVED, QUEUED, MANDATE }

/** A one-tap choice offered in a notification. */
data class ReviewAction(val label: String, val type: TxType, val categoryId: Long?)

/** What the ingestor tells the user. Kept as an interface so the logic here has no notification code in it. */
interface SmsAlerts {
    fun saved(transactionId: Long, amount: Long, merchant: String, categoryName: String?, hideAmounts: Boolean)
    fun review(pending: PendingSmsEntity, actions: List<ReviewAction>, hideAmounts: Boolean)

    /** A recurring UPI debit the user hasn't seen before. */
    fun mandate(merchant: String, amount: Long, hideAmounts: Boolean) {}
}

/**
 * The whole path from a received text to a saved or waiting transaction. A text only gets this far if it comes from
 * a named sender, and the text itself is dropped as soon as its fields are read.
 */
class SmsIngestor(private val repository: LeftoverRepository, private val alerts: SmsAlerts) {

    suspend fun handle(address: String, body: String, at: Long, quiet: Boolean = false): IngestResult {
        if (!SmsParser.isTransactionalSender(address)) return IngestResult.IGNORED
        val snapshot = repository.currentSnapshot()
        if (!snapshot.settings.smsTracking) return IngestResult.IGNORED
        val sender = SmsParser.senderKey(address)
        if (repository.senderIgnored(sender)) return IngestResult.IGNORED

        // Recurring-debit notices are listed for the user and never counted as spending.
        MandateParser.parse(body)?.let { event ->
            val isNew = repository.noteMandate(event.merchant, event.amount, event.frequency, event.nextDebit, at)
            if (isNew && !quiet) alerts.mandate(event.merchant, event.amount, snapshot.settings.hideBalances)
            return IngestResult.MANDATE
        }
        val skipped = if (quiet) null else SmsParser.skipKind(body)
        val parsed = SmsParser.parse(body)?.let { p -> if (p.merchant.isBlank() && p.kind == SmsKind.DEBIT) FeeDetector.detect(body)?.let { label -> p.copy(merchant = label, confidence = app.leftover.domain.Confidence.HIGH) } ?: p else p } ?: run {
            skipped?.let { repository.noteSkipped(it) }
            return IngestResult.IGNORED
        }
        // A notification from a payment app may already have caught this payment.
        if (CrossSource.isDuplicate(parsed, at, snapshot.transactions.filter { it.source == TxSource.NOTIFICATION }, snapshot.pendingSms.filter { it.sender.startsWith("APP:") })) return IngestResult.DUPLICATE
        return process(sender, parsed, at, quiet, snapshot, TxSource.SMS)
    }

    /**
     * A notification from a payment app the user has allowed. Only the fields are kept; the notification text is dropped
     * here. The app is treated like a sender: until the user trusts it, everything waits for review.
     */
    suspend fun handleNotification(packageName: String, text: String, at: Long, quiet: Boolean = false): IngestResult {
        val app = UpiApps.byPackage(packageName) ?: return IngestResult.IGNORED
        val snapshot = repository.currentSnapshot()
        if (!snapshot.settings.captureNotifications || packageName !in snapshot.settings.notificationApps) return IngestResult.IGNORED
        if (repository.senderIgnored(app.senderKey)) return IngestResult.IGNORED
        val parsed = UpiNotificationParser.parse(text) ?: return IngestResult.IGNORED
        if (CrossSource.isDuplicate(parsed, at, snapshot.transactions.filter { it.source == TxSource.SMS || it.source == TxSource.NOTIFICATION }, snapshot.pendingSms)) return IngestResult.DUPLICATE
        return process(app.senderKey, parsed, at, quiet, snapshot, TxSource.NOTIFICATION)
    }

    private suspend fun process(sender: String, parsed: ParsedSms, at: Long, quiet: Boolean, snapshot: Snapshot, source: TxSource): IngestResult {
        val fingerprint = SmsParser.fingerprint(sender, parsed)
        if (repository.smsSeen(fingerprint, at, anyTime = parsed.reference != null)) return IngestResult.DUPLICATE
        repository.rememberSms(fingerprint, at)
        val senderRow = repository.touchSender(sender, at)

        val accountId = resolveAccount(snapshot, parsed)
        val categoryId = if (parsed.kind == SmsKind.DEBIT) {
            (SmsPolicy.ruleCategory(parsed.merchant, snapshot.rules.map { it.pattern to it.categoryId }) ?: SmsPolicy.categoryFor(parsed.merchant, snapshot.transactions))?.takeIf { id -> snapshot.categories.any { it.id == id && !it.hidden } }
        } else null
        val decided = SmsPolicy.decide(
            parsed,
            SmsContext(senderRow.trusted, accountId, categoryId, SmsPolicy.looksLikeExisting(parsed, at, snapshot.transactions)),
        )
        // With "save when confident" off, even a clear payment waits to be checked.
        val decision = if (decided == SmsDecision.AutoSave && !snapshot.settings.smsAutoSave) SmsDecision.Review(PendingReason.ASK_FIRST) else decided
        val quiet = quiet || !snapshot.settings.smsNotify
        val hide = snapshot.settings.hideBalances

        return when (decision) {
            SmsDecision.AutoSave -> {
                val id = repository.saveFromSms(TxType.EXPENSE, parsed.amount, accountId!!, categoryId, parsed.merchant, at, source)
                if (!quiet) alerts.saved(id, parsed.amount, parsed.merchant, snapshot.categories.firstOrNull { it.id == categoryId }?.name, hide)
                IngestResult.SAVED
            }
            is SmsDecision.Review -> {
                val pending = PendingSmsEntity(
                    sender = sender, kind = parsed.kind, amount = parsed.amount, merchant = parsed.merchant, last4 = parsed.last4,
                    isCard = parsed.isCard, occurredAt = at, reason = decision.reason, accountId = accountId, suggestedCategoryId = categoryId,
                )
                val id = repository.addPending(pending)
                if (!quiet) alerts.review(pending.copy(id = id), actionsFor(snapshot, parsed, decision.reason, accountId, categoryId), hide)
                IngestResult.QUEUED
            }
        }
    }

    /** The account the digits belong to: one the user linked earlier, or the only bank account when the text shows no digits. */
    private suspend fun resolveAccount(snapshot: Snapshot, parsed: ParsedSms): Long? {
        val live = snapshot.accounts.filter { !it.archived }
        parsed.accountKey?.let { key -> repository.linkedAccount(key)?.let { id -> if (live.any { it.id == id }) return id } }
        if (parsed.last4 == null && !parsed.isCard) return live.singleOrNull { it.type == AccountType.BANK }?.id
        return null
    }

    /** Buttons for the notification. Only offered when the text can be saved without any more questions. */
    private fun actionsFor(snapshot: Snapshot, parsed: ParsedSms, reason: PendingReason, accountId: Long?, suggested: Long?): List<ReviewAction> {
        if (accountId == null) return emptyList()
        if (parsed.kind == SmsKind.CREDIT) {
            return if (reason == PendingReason.CREDIT) listOf(ReviewAction("Income", TxType.INCOME, null), ReviewAction("Refund", TxType.REFUND, null)) else emptyList()
        }
        if (reason != PendingReason.NEW_MERCHANT && reason != PendingReason.NEW_SENDER) return emptyList()
        val since = snapshot.today.minusDays(90)
        val usual = snapshot.transactions
            .filter { it.type == TxType.EXPENSE && it.categoryId != null && !it.occurredAt.toLocalDate().isBefore(since) }
            .groupingBy { it.categoryId!! }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
        val ids = (listOfNotNull(suggested) + usual).distinct().filter { id -> snapshot.categories.any { it.id == id && !it.hidden } }.take(3)
        return ids.mapNotNull { id -> snapshot.categories.firstOrNull { it.id == id }?.let { ReviewAction(it.name, TxType.EXPENSE, it.id) } }
    }
}
