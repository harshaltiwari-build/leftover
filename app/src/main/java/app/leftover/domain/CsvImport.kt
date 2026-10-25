package app.leftover.domain

import app.leftover.data.db.TransactionEntity
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One date style a bank statement might use. */
enum class DateStyle(val label: String, val pattern: String) {
    DAY_MONTH_YEAR("31/12/2026", "d/M/yyyy"),
    DAY_MONTH_YEAR_DASH("31-12-2026", "d-M-yyyy"),
    DAY_MON_YEAR("31-Dec-2026", "d-MMM-yyyy"),
    DAY_MON_YEAR_SPACE("31 Dec 2026", "d MMM yyyy"),
    YEAR_MONTH_DAY("2026-12-31", "yyyy-M-d"),
    MONTH_DAY_YEAR("12/31/2026", "M/d/yyyy"),
    DAY_MONTH_SHORT_YEAR("31/12/26", "d/M/yy");

    fun parse(text: String): LocalDate? = runCatching {
        LocalDate.parse(text.trim(), DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
    }.getOrNull()
}

/** Which column holds what. Spending is either a Debit column or a single Amount column whose sign says which way it went. */
data class CsvMapping(
    val hasHeader: Boolean,
    val date: Int,
    val description: Int?,
    val amount: Int?,
    val debit: Int?,
    val credit: Int?,
    val dateStyle: DateStyle,
    /** With a single Amount column: whether positive numbers are spending (some banks list debits as positive). */
    val positiveIsSpending: Boolean = false,
)

data class ImportRow(val date: LocalDate, val paise: Paise, val isExpense: Boolean, val description: String, val duplicate: Boolean)

data class ImportPreview(val rows: List<ImportRow>, val unreadable: Int) {
    val duplicates: Int get() = rows.count { it.duplicate }
    val fresh: Int get() = rows.count { !it.duplicate }
}

/**
 * Reads a bank-statement or spreadsheet CSV into transactions. Nothing is saved from here: the caller shows a preview first,
 * and rows that look like something already in Leftover are flagged so the same spend isn't added twice.
 */
object CsvImport {
    /** Splits CSV text into rows of cells. Handles quotes, doubled quotes, line breaks inside quotes, and comma, semicolon or tab separators. */
    fun parse(text: String): List<List<String>> {
        val clean = text.removePrefix("\uFEFF")
        val firstLine = clean.lineSequence().firstOrNull().orEmpty()
        val delimiter = listOf(',', ';', '\t').maxByOrNull { d -> firstLine.count { it == d } }?.takeIf { d -> firstLine.contains(d) } ?: ','
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < clean.length) {
            val ch = clean[i]
            when {
                quoted && ch == '"' && clean.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                !quoted && ch == delimiter -> { row.add(cell.toString().trim()); cell.clear() }
                !quoted && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && clean.getOrNull(i + 1) == '\n') i++
                    row.add(cell.toString().trim()); cell.clear()
                    if (row.any { it.isNotEmpty() }) rows.add(row)
                    row = mutableListOf()
                }
                else -> cell.append(ch)
            }
            i++
        }
        row.add(cell.toString().trim())
        if (row.any { it.isNotEmpty() }) rows.add(row)
        return rows
    }

    /** A number out of text such as "₹1,200.50", "(450)", "1200 Dr" or "-99". Returns the value in paise with its sign, or null. */
    fun amount(text: String): Paise? {
        var t = text.trim()
        if (t.isEmpty()) return null
        var negative = false
        if (t.startsWith("(") && t.endsWith(")")) { negative = true; t = t.substring(1, t.length - 1) }
        if (Regex("(?i)\\bdr\\.?$").containsMatchIn(t)) { negative = true; t = t.replace(Regex("(?i)\\s*dr\\.?$"), "") }
        t = t.replace(Regex("(?i)\\s*cr\\.?$"), "")
        if (t.startsWith("-")) { negative = !negative; t = t.substring(1) }
        t = t.replace("₹", "").replace(Regex("(?i)rs\\.?|inr"), "").replace(",", "").replace(" ", "")
        val paise = Money.parse(t) ?: return null
        return if (negative) -paise else paise
    }

    /** Best guess at the mapping from the first rows. The user can change every part of it. */
    fun guess(table: List<List<String>>): CsvMapping? {
        if (table.size < 2) return null
        val first = table.first()
        val header = first.none { cell -> cell.isNotBlank() && (amount(cell) != null && cell.any { it.isDigit() } && DateStyle.entries.none { it.parse(cell) != null } && cell.length > 3 && !cell.any { it.isLetter() }) }
        // Short abbreviations (dr, cr) must be a word on their own, or "description" would count as a credit column.
        fun hit(cell: String, word: String): Boolean = if (word.length <= 2) cell.lowercase().split(Regex("[^a-z]+")).contains(word) else cell.lowercase().contains(word)
        fun find(vararg words: String): Int? = if (!header) null else first.indexOfFirst { cell -> words.any { hit(cell, it) } }.takeIf { it >= 0 }
        val dateColumn = find("date") ?: 0
        val sample = table.drop(if (header) 1 else 0).take(20).mapNotNull { it.getOrNull(dateColumn) }.filter { it.isNotBlank() }
        val style = DateStyle.entries.firstOrNull { s -> sample.isNotEmpty() && sample.all { s.parse(it) != null } } ?: DateStyle.DAY_MONTH_YEAR
        val debit = find("debit", "withdraw", "paid out", "dr")
        val credit = find("credit", "deposit", "paid in", "cr")
        val amountColumn = if (debit != null && credit != null) null else find("amount", "amt")
        val description = find("narration", "description", "particulars", "details", "merchant", "remarks", "payee", "memo")
            ?: first.indices.firstOrNull { it != dateColumn && it != amountColumn && it != debit && it != credit }
        return CsvMapping(header, dateColumn, description, amountColumn ?: if (debit == null && credit == null) first.indices.firstOrNull { it != dateColumn && it != description } else null, debit, credit, style)
    }

    /** Turns rows into import candidates and flags those that match something already stored (same day, amount and direction). */
    fun preview(table: List<List<String>>, mapping: CsvMapping, existing: List<TransactionEntity>, zone: ZoneId = ZoneId.systemDefault()): ImportPreview {
        val seen = existing.groupingBy { Triple(java.time.Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate(), it.amount, it.type == app.leftover.data.db.TxType.EXPENSE) }.eachCount().toMutableMap()
        val rows = mutableListOf<ImportRow>()
        var unreadable = 0
        for (raw in table.drop(if (mapping.hasHeader) 1 else 0)) {
            val date = raw.getOrNull(mapping.date)?.let(mapping.dateStyle::parse)
            val description = mapping.description?.let { raw.getOrNull(it) }.orEmpty()
            var paise: Paise? = null
            var expense = true
            if (mapping.debit != null || mapping.credit != null) {
                val out = mapping.debit?.let { raw.getOrNull(it) }?.let(::amount)?.let { kotlin.math.abs(it) }?.takeIf { it > 0 }
                val inn = mapping.credit?.let { raw.getOrNull(it) }?.let(::amount)?.let { kotlin.math.abs(it) }?.takeIf { it > 0 }
                if (out != null) { paise = out; expense = true } else if (inn != null) { paise = inn; expense = false }
            } else if (mapping.amount != null) {
                val value = raw.getOrNull(mapping.amount)?.let(::amount)
                if (value != null && value != 0L) {
                    paise = kotlin.math.abs(value)
                    expense = if (mapping.positiveIsSpending) value > 0 else value < 0
                }
            }
            if (date == null || paise == null) { if (raw.any { it.isNotBlank() }) unreadable++; continue }
            val key = Triple(date, paise, expense)
            val left = seen[key] ?: 0
            val duplicate = left > 0
            if (duplicate) seen[key] = left - 1
            rows += ImportRow(date, paise, expense, description.take(60), duplicate)
        }
        return ImportPreview(rows, unreadable)
    }
}
