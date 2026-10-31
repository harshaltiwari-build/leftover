package app.leftover.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

enum class ScreenshotStatus { SUCCESS, FAILED, PENDING, UNKNOWN }

/**
 * What was read off a payment screenshot from GPay, PhonePe, Paytm, BHIM or a bank app. Every field can be missing;
 * Quick add shows what was found and the person checks it before anything is saved.
 */
data class ScreenshotPayment(
    val amount: Paise?,
    val payee: String,
    val date: LocalDate?,
    val time: LocalTime?,
    /** True for "Received from …" screenshots: money in, not a spend. */
    val received: Boolean,
    val status: ScreenshotStatus,
    val upiRef: String,
    val app: String,
) {
    val foundAnything: Boolean get() = amount != null || payee.isNotBlank()
}

/**
 * Turns the text recognised in a payment screenshot into an entry. Built around how the big UPI apps lay out a
 * receipt: one large amount, "Paid to" / "To" with the name under or beside it, a date and time, a status and a
 * reference number. Text recognition often misreads "₹" as "%", "&", "F", "X" or "=", so those count as the rupee sign
 * when they sit alone right before a number. Digits are never treated as a misread sign, since "₹2,500" read as
 * "₹500" would be worse than no amount at all.
 */
object PaymentScreenshotParser {
    private val rupee = """(?:₹|\bRs\.?|\bINR|(?<![A-Za-z0-9])[%&F=¥€£X]\s?(?=\d))"""
    private val number = """(\d{1,3}(?:,\d{2,3})+(?:\.\d{1,2})?|\d{1,7}(?:\.\d{1,2})?)"""
    private val amountWithSign = Regex("""$rupee\s*$number""", RegexOption.IGNORE_CASE)
    private val bareAmountLine = Regex("""^\s*$number\s*$""")
    private val upiId = Regex("""[A-Za-z0-9._-]{2,}@[A-Za-z]{2,}""")
    private val months = Month.entries.associateBy { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase().take(3) }
    private val dayMonthYear = Regex("""\b(\d{1,2})(?:st|nd|rd|th)?\s+([A-Za-z]{3,9})[,]?\s+(\d{4})\b""")
    private val monthDayYear = Regex("""\b([A-Za-z]{3,9})\s+(\d{1,2})[,]?\s+(\d{4})\b""")
    private val numericDate = Regex("""\b(\d{1,2})[/.-](\d{1,2})[/.-](\d{2,4})\b""")
    private val clock = Regex("""\b(\d{1,2}):(\d{2})\s*([AaPp][Mm])?\b""")
    private val payeeLabel = Regex("""^(?:paid\s+to|sent\s+to|payment\s+to|paid\s+successfully\s+to|transferred\s+to|to)\s*:?\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val payerLabel = Regex("""^(?:received\s+from|money\s+received\s+from|from)\s*:?\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val refLabel = Regex("""(?:upi\s*(?:transaction|txn|ref(?:erence)?)\s*(?:id|no\.?|number)?|utr|transaction\s*id|ref(?:erence)?\s*(?:no\.?|number|id))\s*:?\s*([A-Z0-9]{8,30})?""", RegexOption.IGNORE_CASE)
    private val longId = Regex("""\b([A-Z]{0,3}\d{10,25})\b""")
    private val noise = setOf("google pay", "gpay", "phonepe", "paytm", "bhim", "upi", "completed", "successful", "success", "transaction successful", "payment successful", "share", "done", "view details", "split expense", "banking name", "powered by", "from", "to")

    fun parse(raw: String, today: LocalDate): ScreenshotPayment {
        val lines = raw.replace(' ', ' ').lines().map { it.trim().replace(Regex("""\s+"""), " ") }.filter { it.isNotEmpty() }
        val lower = raw.lowercase()
        val received = Regex("""\breceived\s+from\b|\bmoney\s+received\b|\bcredited\b""").containsMatchIn(lower) && !Regex("""\bpaid\s+to\b""").containsMatchIn(lower)
        val status = when {
            Regex("""\bfail(?:ed|ure)\b|\bdeclined\b|\bunsuccessful\b""").containsMatchIn(lower) -> ScreenshotStatus.FAILED
            Regex("""\bpending\b|\bprocessing\b|\bin progress\b""").containsMatchIn(lower) -> ScreenshotStatus.PENDING
            Regex("""\bsuccess(?:ful(?:ly)?)?\b|\bcompleted\b|\bpaid\b|\bsent\b|\breceived\b""").containsMatchIn(lower) -> ScreenshotStatus.SUCCESS
            else -> ScreenshotStatus.UNKNOWN
        }
        val app = when {
            "phonepe" in lower -> "PhonePe"
            "paytm" in lower -> "Paytm"
            "google pay" in lower || "gpay" in lower || "g pay" in lower -> "Google Pay"
            "bhim" in lower -> "BHIM"
            "cred" in lower.split(Regex("""\W+""")) -> "CRED"
            else -> ""
        }
        return ScreenshotPayment(
            amount = amount(lines),
            payee = person(lines, if (received) payerLabel else payeeLabel),
            date = date(raw, today),
            time = time(raw),
            received = received,
            status = status,
            upiRef = ref(lines),
            app = app,
        )
    }

    /** The amount: the first rupee-marked number, else a line that is only a number (the big display figure). */
    fun amount(lines: List<String>): Paise? {
        val skip = Regex("""(?i)\b(?:id|ref|utr|no\.?|a/c|account|xx|\*{2,}|balance|mobile|phone|\+91)\b""")
        val signed = lines.asSequence().filter { !skip.containsMatchIn(it) }
            .mapNotNull { line -> amountWithSign.find(line)?.groupValues?.get(1) }
            .mapNotNull(Money::parse).firstOrNull { it in 1..CaptureLink.MAX_PAISE }
        if (signed != null) return signed
        return lines.asSequence().filter { !skip.containsMatchIn(it) && !clock.containsMatchIn(it) }
            .mapNotNull { bareAmountLine.find(it)?.groupValues?.get(1) }
            .filter { it.replace(",", "").substringBefore('.').length in 1..7 }
            .mapNotNull(Money::parse).firstOrNull { it in 1..CaptureLink.MAX_PAISE }
    }

    /** The name after "Paid to" / "Received from", on the same line or the next one that looks like a name. */
    private fun person(lines: List<String>, label: Regex): String {
        for ((i, line) in lines.withIndex()) {
            val m = label.find(line) ?: continue
            val sameLine = clean(m.groupValues[1])
            if (looksLikeName(sameLine)) return sameLine
            val next = lines.drop(i + 1).take(3).map(::clean).firstOrNull(::looksLikeName)
            if (next != null) return next
        }
        // No label: a UPI id's handle ("zepto@ybl") is better than nothing.
        return lines.firstNotNullOfOrNull { upiId.find(it)?.value }?.substringBefore('@')?.replace(Regex("""[._-]+"""), " ")
            ?.trim()?.takeIf { it.any(Char::isLetter) && it.length >= 3 }?.replaceFirstChar(Char::titlecase).orEmpty()
    }

    private fun clean(s: String): String = s.replace(Regex("""^[^A-Za-z0-9]+|[^A-Za-z0-9.)]+$"""), "")
        .replace(amountWithSign, "").replace(upiId, "").trim().take(40)

    private fun looksLikeName(s: String): Boolean =
        s.length >= 2 && s.count(Char::isLetter) >= 2 && s.lowercase() !in noise && !s.any { it == '@' } &&
            s.count(Char::isDigit) <= s.length / 3 && months.keys.none { s.lowercase().startsWith("$it ") && s.any(Char::isDigit) }

    fun date(text: String, today: LocalDate): LocalDate? {
        val lower = text.lowercase()
        fun ok(d: LocalDate?) = d?.takeIf { !it.isAfter(today.plusDays(1)) && it.isAfter(today.minusYears(2)) }
        dayMonthYear.findAll(text).forEach { m ->
            val month = months[m.groupValues[2].lowercase().take(3)] ?: return@forEach
            ok(runCatching { LocalDate.of(m.groupValues[3].toInt(), month, m.groupValues[1].toInt()) }.getOrNull())?.let { return it }
        }
        monthDayYear.findAll(text).forEach { m ->
            val month = months[m.groupValues[1].lowercase().take(3)] ?: return@forEach
            ok(runCatching { LocalDate.of(m.groupValues[3].toInt(), month, m.groupValues[2].toInt()) }.getOrNull())?.let { return it }
        }
        numericDate.findAll(text).forEach { m ->
            val year = m.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
            ok(runCatching { LocalDate.of(year, m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull())?.let { return it }
        }
        return when {
            Regex("""\byesterday\b""").containsMatchIn(lower) -> today.minusDays(1)
            Regex("""\btoday\b""").containsMatchIn(lower) -> today
            else -> null
        }
    }

    fun time(text: String): LocalTime? {
        val m = clock.find(text) ?: return null
        var h = m.groupValues[1].toInt(); val min = m.groupValues[2].toInt()
        when (m.groupValues[3].lowercase()) { "pm" -> if (h < 12) h += 12; "am" -> if (h == 12) h = 0 }
        return if (h in 0..23 && min in 0..59) LocalTime.of(h, min) else null
    }

    private fun ref(lines: List<String>): String {
        for ((i, line) in lines.withIndex()) {
            val m = refLabel.find(line) ?: continue
            m.groupValues.getOrNull(1)?.takeIf { it.any(Char::isDigit) }?.let { return it }
            lines.getOrNull(i + 1)?.let { longId.find(it.replace(" ", ""))?.value }?.let { return it }
        }
        return ""
    }
}
