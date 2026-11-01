package app.leftover.domain

/** A bill picked during setup, as it goes to the repository: name, amount, and its day of the month (null: the day before payday). */
data class BillAnswer(val name: String, val amount: Paise, val dueDay: Int? = null)

/** One chip on the setup bills step. [rupees] is whole rupees, as typed. */
data class BillDraft(val id: Int, val name: String, val rupees: Long, val dueDay: Int? = null, val on: Boolean = false)

/**
 * The bills on the setup screen. Every one, suggestions included, can be switched on, edited or removed, and your own
 * can be added. Changes that don't make sense come back as [Change.Invalid] with a reason to show.
 */
data class BillDrafts(val items: List<BillDraft>) {
    sealed interface Change {
        data class Ok(val bills: BillDrafts) : Change
        data class Invalid(val message: String) : Change
    }

    val selected: List<BillDraft> get() = items.filter { it.on }
    val totalRupees: Long get() = selected.sumOf { it.rupees }

    fun toggle(id: Int) = BillDrafts(items.map { if (it.id == id) it.copy(on = !it.on) else it })

    fun remove(id: Int) = BillDrafts(items.filterNot { it.id == id })

    /** Saves new details for a bill and switches it on. */
    fun edit(id: Int, name: String, amount: String, dueDay: String): Change = parse(name, amount, dueDay, exceptId = id) { clean, rupees, day ->
        BillDrafts(items.map { if (it.id == id) it.copy(name = clean, rupees = rupees, dueDay = day, on = true) else it })
    }

    /** Adds your own bill, switched on. */
    fun add(name: String, amount: String, dueDay: String): Change = if (items.size >= MAX_BILLS) Change.Invalid("That's plenty for now. Add more later in Plan → Bills.") else parse(name, amount, dueDay, exceptId = null) { clean, rupees, day ->
        BillDrafts(items + BillDraft((items.maxOfOrNull { it.id } ?: 0) + 1, clean, rupees, day, on = true))
    }

    fun answers(): List<BillAnswer> = selected.map { BillAnswer(it.name, rupees(it.rupees), it.dueDay) }

    private fun parse(name: String, amount: String, dueDay: String, exceptId: Int?, apply: (String, Long, Int?) -> BillDrafts): Change {
        val clean = tidy(name)
        if (clean.isEmpty()) return Change.Invalid("Give it a name")
        items.firstOrNull { it.id != exceptId && it.name.equals(clean, ignoreCase = true) }?.let { return Change.Invalid("${it.name} is already on the list") }
        val digits = amount.filter(Char::isDigit).trimStart('0')
        if (digits.isEmpty()) return Change.Invalid("Enter an amount")
        val rupees = digits.takeIf { it.length <= 7 }?.toLong() ?: return Change.Invalid("Up to ${Money.format(rupees(MAX_RUPEES))}")
        val dayText = dueDay.filter(Char::isDigit)
        val day = if (dayText.isEmpty()) null else dayText.take(3).toInt().takeIf { it in 1..31 } ?: return Change.Invalid("Pick a day from 1 to 31")
        return Change.Ok(apply(clean, rupees, day))
    }

    companion object {
        const val MAX_NAME = 24
        const val MAX_BILLS = 30
        const val MAX_RUPEES = 99_99_999L

        fun suggested() = BillDrafts(
            listOf("Spotify" to 119L, "Recharge" to 349L, "Electricity" to 682L, "Gym" to 2000L, "Card bill" to 2500L, "Rent" to 9000L)
                .mapIndexed { i, (name, rupees) -> BillDraft(i + 1, name, rupees) },
        )

        /** Single spaces, no control characters, at most [MAX_NAME] characters. */
        fun tidy(name: String): String = name.filterNot { it.isISOControl() && it != '\n' && it != '\t' }
            .split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ").take(MAX_NAME).trim()
    }
}
