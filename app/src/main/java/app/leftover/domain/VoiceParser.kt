package app.leftover.domain

import app.leftover.data.db.CategoryEntity
import app.leftover.data.db.CategoryKind

enum class EntryKind { EXPENSE, INCOME }

/** What the parser understood from a spoken or typed sentence. Anything it couldn't find is null so the UI can ask. */
data class ParsedEntry(
    val heard: String,
    val kind: EntryKind,
    val amount: Paise?,
    val category: CategoryEntity?,
    val merchant: String?,
    val daysAgo: Int,
) {
    /** True when the category came from a real keyword, not a fallback guess. */
    val categoryConfident: Boolean get() = category != null
}

/**
 * Turns "spent 180 on coffee today at Blue Tokai" into fields. It never saves anything: the voice screen shows the result
 * for review first. Handles digits ("1,250"), shorthand ("2k", "1.5 lakh") and number words ("twelve hundred").
 */
object VoiceParser {
    private val numberWords = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
        "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
        "eighteen" to 18, "nineteen" to 19, "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70,
        "eighty" to 80, "ninety" to 90,
    )

    /** Hindi number words, written the way people type them in English letters. */
    private val hindiNumbers = mapOf(
        "ek" to 1, "do" to 2, "teen" to 3, "char" to 4, "paanch" to 5, "panch" to 5, "chhe" to 6, "chah" to 6, "saat" to 7, "aath" to 8, "nau" to 9, "das" to 10,
        "gyarah" to 11, "barah" to 12, "terah" to 13, "chaudah" to 14, "pandrah" to 15, "solah" to 16, "satrah" to 17, "atharah" to 18, "unnees" to 19, "bees" to 20,
        "tees" to 30, "chalis" to 40, "pachas" to 50, "pachaas" to 50, "saath" to 60, "sattar" to 70, "assi" to 80, "nabbe" to 90,
    )

    /** A few common Devanagari words, turned into the Latin spelling the rest of the parser knows. Digits are handled separately. */
    private val devanagari = listOf(
        "रुपये" to " ", "रुपए" to " ", "रूपये" to " ", "रुपया" to " ", "खर्च" to "kharch", "चाय" to "chai", "खाना" to "khana", "नाश्ता" to "nashta",
        "पेट्रोल" to "petrol", "ऑटो" to "auto", "किराना" to "kirana", "सब्जी" to "sabzi", "सब्ज़ी" to "sabzi", "दूध" to "doodh", "कपड़े" to "kapde",
        "सैलरी" to "salary", "मिला" to "mila", "आज" to "aaj", "सौ" to "sau", "हजार" to "hazaar", "हज़ार" to "hazaar", "लाख" to "lakh",
        "एक" to "ek", "दो" to "do", "तीन" to "teen", "चार" to "char", "पांच" to "paanch", "पाँच" to "paanch", "छह" to "chhe", "सात" to "saat",
        "आठ" to "aath", "नौ" to "nau", "दस" to "das", "बीस" to "bees", "पचास" to "pachaas",
    )

    private fun normalise(input: String): String {
        var text = input
        text = text.map { ch -> if (ch in '०'..'९') ('0' + (ch - '०')) else ch }.joinToString("")
        devanagari.forEach { (word, latin) -> text = text.replace(word, " $latin ") }
        // "dedh sau" is 150 and "dhai sau" is 250; the same halves apply to thousands.
        return text.lowercase().replace(Regex("\\bdedh\\s+sau\\b"), "150").replace(Regex("\\bdhai\\s+sau\\b"), "250")
            .replace(Regex("\\bdedh\\s+haz+aar\\b"), "1500").replace(Regex("\\bdhai\\s+haz+aar\\b"), "2500")
    }

    private val multipliers = mapOf(
        "k" to 1_000.0, "thousand" to 1_000.0, "hazar" to 1_000.0, "hazaar" to 1_000.0,
        "lakh" to 100_000.0, "lakhs" to 100_000.0, "lac" to 100_000.0, "hundred" to 100.0, "sau" to 100.0,
    )

    private val incomeWords = listOf("got ", "received", "receive ", "salary", "credited", "earned", "income", "paid me", "mila", "mile ", "aaye", "kamaye", "freelance", "pocket money")
    private val expenseWords = listOf("spent", "paid for", "bought", "gave", "bill for", "paid to", "kharch", "diye", "diya", "liye", "kharid", "bhara", "bhare")

    /** Keyword → default category name. First match wins, so more specific words come first. */
    private val expenseKeywords: List<Pair<String, String>> = listOf(
        "coffee" to "Coffee", "chai" to "Coffee", "tea" to "Coffee", "cafe" to "Coffee", "starbucks" to "Coffee", "tokai" to "Coffee",
        "swiggy" to "Eating out", "zomato" to "Eating out", "lunch" to "Eating out", "dinner" to "Eating out", "breakfast" to "Eating out",
        "restaurant" to "Eating out", "pizza" to "Eating out", "burger" to "Eating out", "biryani" to "Eating out", "food" to "Eating out", "eating" to "Eating out",
        "snack" to "Snacks", "chips" to "Snacks", "samosa" to "Snacks",
        "grocery" to "Groceries", "groceries" to "Groceries", "vegetable" to "Groceries", "bigbasket" to "Groceries", "zepto" to "Groceries", "blinkit" to "Groceries", "milk" to "Groceries",
        "uber" to "Transport", "ola" to "Transport", "rapido" to "Transport", "metro" to "Transport", "bus" to "Transport", "cab" to "Transport",
        "auto" to "Transport", "petrol" to "Transport", "fuel" to "Transport", "train" to "Transport", "taxi" to "Transport", "transport" to "Transport",
        "amazon" to "Shopping", "myntra" to "Shopping", "flipkart" to "Shopping", "clothes" to "Shopping", "shoes" to "Shopping", "shopping" to "Shopping",
        "netflix" to "Fun & subscriptions", "spotify" to "Fun & subscriptions", "movie" to "Fun & subscriptions", "subscription" to "Fun & subscriptions", "game" to "Fun & subscriptions",
        "khana" to "Eating out", "nashta" to "Eating out", "sabzi" to "Groceries", "kirana" to "Groceries", "doodh" to "Groceries", "ration" to "Groceries",
        "rickshaw" to "Transport", "bhada" to "Transport", "kapde" to "Shopping", "joote" to "Shopping", "cinema" to "Fun & subscriptions",
        "dawai" to "Personal care", "dawa" to "Personal care", "recharge" to "Other",
        "salon" to "Personal care", "haircut" to "Personal care", "medicine" to "Personal care", "pharmacy" to "Personal care",
    )

    private val incomeKeywords: List<Pair<String, String>> = listOf(
        "salary" to "Salary", "client" to "Freelance", "freelance" to "Freelance", "invoice" to "Freelance",
        "pocket" to "Pocket money", "allowance" to "Pocket money", "gift" to "Gift",
    )

    private val knownMerchants = listOf(
        "swiggy", "zomato", "uber", "ola", "rapido", "amazon", "myntra", "flipkart", "zepto", "blinkit", "bigbasket", "netflix", "spotify", "starbucks",
    )

    fun parse(input: String, categories: List<CategoryEntity>): ParsedEntry {
        val heard = input.trim()
        val lower = " " + normalise(heard).replace("₹", " ").replace(Regex("\\brs\\.?\\b|\\brupees?\\b|\\brupaye\\b|\\binr\\b"), " ") + " "

        val daysAgo = parseDaysAgo(lower)
        val withoutDates = removeDatePhrases(lower)
        val amount = parseAmount(withoutDates)
        val kind = parseKind(withoutDates)
        val category = parseCategory(withoutDates, kind, categories)
        val merchant = parseMerchant(heard, kind)
        return ParsedEntry(heard, kind, amount, category, merchant, daysAgo)
    }

    private fun parseKind(text: String): EntryKind = when {
        expenseWords.any { text.contains(it) } -> EntryKind.EXPENSE
        incomeWords.any { text.contains(it) } -> EntryKind.INCOME
        else -> EntryKind.EXPENSE
    }

    private val dayPhrases = listOf(
        Regex("\\bday before yesterday\\b") to 2,
        Regex("\\byesterday\\b") to 1,
        Regex("\\b(today|tonight|just now|abhi|aaj)\\b") to 0,
    )
    private val daysAgoDigits = Regex("\\b(\\d{1,2})\\s+days?\\s+ago\\b")
    private val daysAgoWords = Regex("\\b(one|two|three|four|five|six|seven)\\s+days?\\s+ago\\b")

    private fun parseDaysAgo(text: String): Int {
        daysAgoDigits.find(text)?.let { return it.groupValues[1].toInt().coerceIn(0, 60) }
        daysAgoWords.find(text)?.let { return numberWords.getValue(it.groupValues[1]) }
        return dayPhrases.firstOrNull { it.first.containsMatchIn(text) }?.second ?: 0
    }

    private fun removeDatePhrases(text: String): String {
        var result = daysAgoDigits.replace(text, " ")
        result = daysAgoWords.replace(result, " ")
        dayPhrases.forEach { result = it.first.replace(result, " ") }
        return result
    }

    private val numeric = Regex("(\\d[\\d,]*(?:\\.\\d+)?)\\s*(k|thousand|hazaar|hazar|lakhs?|lac|hundred|sau)?\\b")

    private fun parseAmount(text: String): Paise? {
        val match = numeric.find(text)
        if (match != null) {
            val base = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
            val multiplier = multipliers[match.groupValues[2]] ?: 1.0
            return Math.round(base * multiplier * 100.0).takeIf { it > 0 }
        }
        return parseWordAmount(text)
    }

    /** Handles "five hundred", "twelve hundred", "two thousand five hundred". */
    private fun parseWordAmount(text: String): Paise? {
        var total = 0L
        var current = 0L
        var sawNumber = false
        for (token in text.split(Regex("[^a-z]+")).filter { it.isNotEmpty() }) {
            val value = numberWords[token] ?: hindiNumbers[token]
            val multiplier = multipliers[token]?.toLong()
            when {
                value != null -> { current += value; sawNumber = true }
                multiplier != null && token != "k" -> {
                    if (multiplier == 100L) current = maxOf(current, 1) * 100
                    else { total += maxOf(current, 1) * multiplier; current = 0 }
                    sawNumber = true
                }
                sawNumber -> break
            }
        }
        val result = total + current
        return if (sawNumber && result > 0) result * 100 else null
    }

    private fun parseCategory(text: String, kind: EntryKind, categories: List<CategoryEntity>): CategoryEntity? {
        val wanted = if (kind == EntryKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
        val candidates = categories.filter { !it.hidden && it.kind == wanted }
        candidates.filter { containsWord(text, it.name.lowercase()) }.maxByOrNull { it.name.length }?.let { return it }
        val keywords = if (kind == EntryKind.INCOME) incomeKeywords else expenseKeywords
        val name = keywords.firstOrNull { containsWord(text, it.first) }?.second
        return name?.let { target -> candidates.firstOrNull { it.name.equals(target, ignoreCase = true) } }
    }

    /** Whole-word match, so "ola" doesn't fire inside "cola" or "bus" inside "business". */
    private fun containsWord(text: String, word: String): Boolean = Regex("\\b" + Regex.escape(word) + "\\b").containsMatchIn(text)

    private val atMerchant = Regex("\\bat\\s+([A-Za-z0-9&'.\\- ]+?)(?=\\s+(?:today|yesterday|for|on|using|via|with|from)\\b|$)", RegexOption.IGNORE_CASE)
    private val fromSource = Regex("\\bfrom\\s+([A-Za-z0-9&'.\\- ]+?)(?=\\s+(?:today|yesterday|for|on|using|via|with)\\b|$)", RegexOption.IGNORE_CASE)

    private fun parseMerchant(heard: String, kind: EntryKind): String? {
        val source = if (kind == EntryKind.INCOME) fromSource else atMerchant
        source.find(heard)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { return titleCase(it) }
        val lower = heard.lowercase()
        return knownMerchants.firstOrNull { Regex("\\b$it\\b").containsMatchIn(lower) }?.let { titleCase(it) }
    }

    private fun titleCase(text: String) = text.split(" ").filter { it.isNotEmpty() }.joinToString(" ") { word ->
        word.lowercase().replaceFirstChar { it.uppercase() }
    }
}
