package app.leftover.domain

import app.leftover.data.db.PendingSmsEntity
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxSource
import app.leftover.data.db.TxType
import kotlin.math.abs

/** A payment app Leftover is able to read notifications from, once the user allows it. */
data class UpiApp(val packageName: String, val label: String) {
    /** The sender key used in the review queue and sender list. */
    val senderKey: String get() = "APP:" + label.uppercase().filter { it.isLetterOrDigit() }
}

object UpiApps {
    val known = listOf(
        UpiApp("com.google.android.apps.nbu.paisa.user", "Google Pay"),
        UpiApp("com.phonepe.app", "PhonePe"),
        UpiApp("net.one97.paytm", "Paytm"),
        UpiApp("in.org.npci.upiapp", "BHIM"),
        UpiApp("com.dreamplug.androidapp", "CRED"),
        UpiApp("com.naviapp", "Navi"),
        UpiApp("money.super.payments", "super.money"),
    )

    fun byPackage(packageName: String): UpiApp? = known.firstOrNull { it.packageName == packageName }
}

/**
 * Reads the wording payment apps use in their notifications ("Paid ₹250 to Blue Tokai", "Received ₹500 from Meera").
 * Like the SMS reader, it returns null for anything it can't read cleanly rather than guessing.
 */
object UpiNotificationParser {
    private const val AMOUNT = "(?:₹|rs\\.?|inr)\\s*([\\d,]+(?:\\.\\d{1,2})?)"
    private val skip = Regex("(?i)\\b(request(?:ed)?|failed|declined|pending|cashback|reward|scratch|offer|reminder|due|otp|expired|reversed|refund initiated|collect)\\b")
    private val paid = Regex("(?i)\\b(?:you\\s+)?(?:paid|sent)\\s+$AMOUNT\\s+to\\s+(.+)")
    private val paymentOf = Regex("(?i)\\bpayment of\\s+$AMOUNT\\s+(?:to|at)\\s+(.+?)\\s+(?:was\\s+)?successful")
    private val received = Regex("(?i)\\b(?:you\\s+)?received\\s+$AMOUNT\\s+from\\s+(.+)")
    private val receivedLead = Regex("(?i)$AMOUNT\\s+received\\s+from\\s+(.+)")
    private val paidYou = Regex("(?i)(.+?)\\s+(?:has\\s+)?(?:paid|sent)\\s+you\\s+$AMOUNT")
    private val tail = Regex("(?i)\\s+(?:via|using|from your|from|on|successfully|with|ref|upi|utr)\\b.*$|[.!]+\\s.*$|[.!]+$")

    fun parse(text: String): ParsedSms? {
        val clean = text.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        if (clean.isEmpty() || skip.containsMatchIn(clean)) return null
        paymentOf.find(clean)?.let { return build(SmsKind.DEBIT, it.groupValues[1], it.groupValues[2]) }
        paid.find(clean)?.let { return build(SmsKind.DEBIT, it.groupValues[1], it.groupValues[2]) }
        received.find(clean)?.let { return build(SmsKind.CREDIT, it.groupValues[1], it.groupValues[2]) }
        receivedLead.find(clean)?.let { return build(SmsKind.CREDIT, it.groupValues[1], it.groupValues[2]) }
        paidYou.find(clean)?.let { return build(SmsKind.CREDIT, it.groupValues[2], it.groupValues[1]) }
        return null
    }

    private fun build(kind: SmsKind, amountText: String, rawName: String): ParsedSms? {
        val amount = Money.parse(amountText)?.takeIf { it > 0 } ?: return null
        val merchant = SmsParser.cleanMerchant(rawName.replace(tail, "").trim())
        return ParsedSms(
            kind = kind, amount = amount, merchant = merchant, last4 = null, isCard = false, reference = null,
            possibleTransfer = false, confidence = if (merchant.isNotBlank()) Confidence.HIGH else Confidence.LOW,
        )
    }
}

/** The same payment often arrives twice: once as the app's notification and once as the bank's text. */
object CrossSource {
    const val windowMillis = 10 * 60_000L

    /** True when a transaction or waiting item of the same size and direction came from a text or notification moments away. */
    fun isDuplicate(parsed: ParsedSms, at: Long, transactions: List<TransactionEntity>, pending: List<PendingSmsEntity>): Boolean {
        val types = if (parsed.kind == SmsKind.DEBIT) setOf(TxType.EXPENSE) else setOf(TxType.INCOME, TxType.REFUND, TxType.REPAYMENT)
        val asTransaction = transactions.any {
            (it.source == TxSource.SMS || it.source == TxSource.NOTIFICATION) && it.type in types && it.amount == parsed.amount && abs(it.occurredAt - at) <= windowMillis
        }
        val asPending = pending.any { it.kind == parsed.kind && it.amount == parsed.amount && abs(it.occurredAt - at) <= windowMillis }
        return asTransaction || asPending
    }
}
