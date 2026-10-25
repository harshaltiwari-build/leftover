package app.leftover.domain

import java.security.MessageDigest

enum class SmsKind { DEBIT, CREDIT }

enum class Confidence { HIGH, LOW }

/** What was recognised in one bank text. The text itself is never kept; only these fields are. */
data class ParsedSms(
    val kind: SmsKind,
    val amount: Paise,
    /** Where the money went or came from; empty when the text doesn't say. */
    val merchant: String,
    /** Last four digits of the account or card, when the text shows them. */
    val last4: String?,
    val isCard: Boolean,
    val reference: String?,
    /** The wording suggests a card payment or a move between the user's own accounts. */
    val possibleTransfer: Boolean,
    val confidence: Confidence,
) {
    /** Key used to remember which account or card this text belongs to. */
    val accountKey: String? get() = last4?.let { (if (isCard) "card:" else "acct:") + it }
}

/**
 * Turns a bank or wallet text into amount, direction, merchant and account digits, using only the text on hand.
 * It is deliberately cautious: anything it can't read cleanly returns null or low confidence, and the app
 * then asks the user instead of guessing. Bank wording varies, so this is a heuristic, not a guarantee.
 */
object SmsParser {
    private val senderShape = Regex("^(?:[A-Za-z]{2}-)?[A-Za-z][A-Za-z0-9]{2,10}(?:-[A-Za-z])?$")
    private val senderPrefix = Regex("^[A-Za-z]{2}-")
    private val senderSuffix = Regex("-[A-Za-z]$")

    /**
     * Banks and wallets send from named headers such as `AX-HDFCBK-S`. People send from phone numbers.
     * Only named headers are ever read, so personal chats are never touched.
     */
    fun isTransactionalSender(address: String): Boolean = senderShape.matches(address.trim())

    /** `AX-HDFCBK-S` and `VM-HDFCBK` are the same bank, so both become `HDFCBK`. */
    fun senderKey(address: String): String {
        val trimmed = address.trim()
        val withoutPrefix = if (trimmed.contains('-')) trimmed.replace(senderPrefix, "") else trimmed
        return withoutPrefix.replace(senderSuffix, "").uppercase()
    }

    enum class Skipped { OTP, PROMO }

    /** Why a text from a named sender was set aside rather than read as a payment, for the "what we skipped" count. */
    fun skipKind(body: String): Skipped? = when {
        otp.containsMatchIn(body) -> Skipped.OTP
        promo.containsMatchIn(body) -> Skipped.PROMO
        else -> null
    }

    private val otp = Regex("(?i)\\b(otp|one[- ]time (?:password|pin)|verification code|do not share|never share|cvv|passcode|login code)\\b")
    private val failed = Regex("(?i)\\b(declined|failed|unsuccessful|reversed|could not|not processed|insufficient)\\b")
    private val reminder = Regex("(?i)(\\bis due\\b|\\bdue on\\b|\\bmin(?:imum)? (?:amount )?due\\b|\\btotal (?:amount )?due\\b|\\bbill (?:is )?generated\\b|\\bstatement\\b)")
    private val promo = Regex("(?i)(pre-?approved|apply now|loan offer|collect request|requested money|has requested|will be (?:debited|deducted|charged)|to be debited|scheduled (?:debit|payment))")
    private val debitWord = Regex("(?i)\\b(debited|spent|paid|purchased?|withdrawn|withdrawal|sent|deducted)\\b")
    private val creditWord = Regex("(?i)\\b(credited|received|deposited|refunded?)\\b")
    private val amountPattern = Regex("(?i)(?:rs\\.?|inr|₹)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)")
    private val amountAfterVerb = Regex("(?i)\\b(?:debited|credited)\\s+(?:by|for|with)?\\s*(?:rs\\.?|inr|₹)?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)")
    private val balanceLead = Regex("(?i)(bal(?:ance)?|limit|lmt|avl|available|outstanding)(\\s*(is|of|:|-|\\.|bal(?:ance)?))*\\s*$")
    private val accountDigits = Regex("(?i)\\b(?:a/?c|acct?|account)\\b[^0-9]{0,15}(\\d{3,6})\\b")
    private val cardDigits = Regex("(?i)\\bcard\\b[^0-9]{0,20}(\\d{4})\\b")
    private val maskedDigits = Regex("(?i)\\bx{2,}(\\d{4})\\b")
    private val reference = Regex("(?i)(?:\\bref(?:erence)?\\.?\\s*(?:no\\.?|number|#|:)?\\s*|\\bupi:|\\butr:?\\s*|\\brrn:?\\s*|\\btxn\\s*(?:id|no)\\.?:?\\s*)([0-9]{6,16})\\b")
    private val transferWords = Regex("(?i)(towards\\s+your\\s+[A-Za-z ]{0,30}card|thank you for (?:your )?payment|self[- ]?transfer|own (?:a/c|account)|credited to (?:a/c|acct|account)\\b)")

    private const val NAME = "[A-Za-z0-9][A-Za-z0-9 &'*_@-]{1,40}?"
    private const val STOP = "(?=\\s+(?:on|via|using|with|ref\\w*|avl|bal|txn|if|dt|date|upi|not|call)\\b|[.,(]|$)"
    private val atMerchant = Regex("(?i)\\b(?:at|@)\\s+($NAME)$STOP")
    private val toMerchant = Regex("(?i)\\bto\\s+(?:vpa\\s+)?($NAME)$STOP")
    private val fromMerchant = Regex("(?i)\\b(?:from|by)\\s+(?:vpa\\s+)?($NAME)$STOP")
    private val infoMerchant = Regex("(?i)\\binfo:\\s*([^.\\n]{2,60})")
    private val notAName = Regex("(?i)^(your|a/?c|acct?|account|the|beneficiary|card|you|me|date|vpa)\\b")

    fun parse(body: String): ParsedSms? {
        val text = body.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty() || otp.containsMatchIn(text) || failed.containsMatchIn(text) || promo.containsMatchIn(text)) return null
        val debit = debitWord.find(text)
        val credit = creditWord.find(text)
        if (debit == null && credit == null) return null
        if (reminder.containsMatchIn(text) && !Regex("(?i)\\b(debited|credited|spent)\\b").containsMatchIn(text)) return null
        val kind = when {
            debit != null && credit != null -> if (debit.range.first < credit.range.first) SmsKind.DEBIT else SmsKind.CREDIT
            debit != null -> SmsKind.DEBIT
            else -> SmsKind.CREDIT
        }
        val amount = findAmount(text) ?: return null
        val last4 = (accountDigits.find(text) ?: cardDigits.find(text) ?: maskedDigits.find(text))?.groupValues?.get(1)?.takeLast(4)
        val ref = reference.find(text)?.groupValues?.get(1)
        val merchant = findMerchant(text, kind)
        return ParsedSms(
            kind = kind,
            amount = amount,
            merchant = merchant,
            last4 = last4,
            isCard = isCard(text, kind),
            reference = ref,
            possibleTransfer = transferWords.containsMatchIn(text),
            confidence = if (merchant.isNotBlank() || ref != null) Confidence.HIGH else Confidence.LOW,
        )
    }

    private fun findAmount(text: String): Paise? {
        for (match in amountPattern.findAll(text)) {
            val before = text.substring((match.range.first - 24).coerceAtLeast(0), match.range.first)
            if (balanceLead.containsMatchIn(before)) continue
            return toPaise(match.groupValues[1])
        }
        return amountAfterVerb.find(text)?.groupValues?.get(1)?.let(::toPaise)
    }

    private fun toPaise(digits: String): Paise? {
        val paise = Money.parse(digits) ?: return null
        return paise.takeIf { it in 1..MAX_PAISE }
    }

    private fun isCard(text: String, kind: SmsKind): Boolean = when {
        Regex("(?i)\\bcredit card\\b").containsMatchIn(text) -> true
        Regex("(?i)\\bdebit card\\b").containsMatchIn(text) -> false
        else -> kind == SmsKind.DEBIT && Regex("(?i)\\bcard\\b").containsMatchIn(text) && !accountDigits.containsMatchIn(text)
    }

    private fun findMerchant(text: String, kind: SmsKind): String {
        val candidates = buildList {
            infoMerchant.find(text)?.groupValues?.get(1)?.let { add(it.substringAfterLast('/')) }
            if (kind == SmsKind.DEBIT) {
                atMerchant.find(text)?.let { add(it.groupValues[1]) }
                toMerchant.find(text)?.let { add(it.groupValues[1]) }
            } else {
                fromMerchant.find(text)?.let { add(it.groupValues[1]) }
            }
        }
        return candidates.map(::cleanMerchant).firstOrNull { it.isNotBlank() } ?: ""
    }

    internal fun cleanMerchant(raw: String): String {
        var name = raw.trim().trimEnd('-', '*', '_', ' ')
        if (name.contains('@')) name = name.substringBefore('@')
        if (notAName.containsMatchIn(name)) return ""
        // A bare phone number is a person, not a merchant, and shouldn't be stored.
        if (name.count { it.isDigit() } >= name.length / 2 + 1) return ""
        name = name.replace(Regex("^(?:neft|imps|rtgs|upi)[- ]", RegexOption.IGNORE_CASE), "").trim()
        if (name.length < 2) return ""
        return if (name.any { it.isLowerCase() }) name else name.lowercase().split(' ').filter { it.isNotEmpty() }
            .joinToString(" ") { word -> if (word.none { it in "aeiou" }) word.uppercase() else word.replaceFirstChar { it.uppercase() } }
    }

    private const val MAX_PAISE = 10_00_00_000L * 100

    /**
     * A short fingerprint that stays the same when the same text is seen twice (live and during a scan).
     * Texts with a reference number match whatever the time; others match only when they arrive within [windowMillis].
     */
    fun fingerprint(sender: String, parsed: ParsedSms): String {
        val basis = listOf(sender, parsed.amount, parsed.last4.orEmpty(), parsed.reference ?: parsed.merchant.lowercase(), parsed.kind).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(basis.toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    const val windowMillis = 2 * 60_000L
}
