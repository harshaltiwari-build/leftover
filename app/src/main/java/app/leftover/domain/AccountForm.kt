package app.leftover.domain

import app.leftover.data.db.AccountEntity
import app.leftover.data.db.AccountType

/**
 * Adding and editing an account. Editing never touches entries: setting today's balance moves the account's starting
 * point instead, so every past entry stays as it was and the balance comes out at what you typed.
 */
object AccountForm {
    sealed interface Result {
        data class Ok(val account: AccountEntity) : Result
        data class Invalid(val message: String) : Result
    }

    const val MAX_NAME = 30
    private const val MAX_RUPEES = 99_99_99_999L

    fun defaultName(type: AccountType) = when (type) {
        AccountType.BANK -> "Bank account"
        AccountType.CASH -> "Cash"
        AccountType.CARD -> "Credit card"
        AccountType.SAVINGS -> "Goal savings"
    }

    /**
     * [amount] is whole rupees as typed: the balance, or for a card what you owe. [currentBalance] is the account's
     * balance from its entries now (0 for a new one). [others] are all accounts, to keep names unique.
     */
    fun save(
        existing: AccountEntity?, type: AccountType, currentBalance: Paise, name: String, amount: String,
        include: Boolean, dueDay: String, statementDay: String, others: List<AccountEntity>,
    ): Result {
        val tidy = BillDrafts.tidy(name).take(MAX_NAME).trim()
        val clean = if (tidy.isEmpty() && existing == null) defaultName(type) else tidy
        if (clean.isEmpty()) return Result.Invalid("Give it a name")
        others.firstOrNull { it.id != existing?.id && !it.archived && it.name.equals(clean, ignoreCase = true) }
            ?.let { return Result.Invalid("You already have an account called ${it.name}") }
        val digits = amount.filter(Char::isDigit).trimStart('0')
        val rupeesTyped = if (digits.isEmpty()) 0L else digits.takeIf { it.length <= 10 }?.toLong()?.takeIf { it <= MAX_RUPEES }
            ?: return Result.Invalid("Up to ${Money.format(rupees(MAX_RUPEES))}")
        val card = type == AccountType.CARD
        val due = if (card) day(dueDay) ?: return Result.Invalid("Pick a day from 1 to 31") else null
        val statement = if (card) day(statementDay) ?: return Result.Invalid("Pick a day from 1 to 31") else null
        val target = if (card) -rupees(rupeesTyped) else rupees(rupeesTyped)
        val base = existing ?: AccountEntity(name = clean, type = type, openingBalance = 0, includeInLeft = include)
        return Result.Ok(
            base.copy(
                name = clean, includeInLeft = include,
                openingBalance = if (existing == null) target else existing.openingBalance + (target - currentBalance),
                dueDay = due?.value, statementDay = statement?.value,
            ),
        )
    }

    /** Why [account] can't be hidden yet, or null when it can. Hiding never changes what you can spend. */
    fun whyNotHide(account: AccountEntity, balance: Paise, accounts: List<AccountEntity>): String? = when {
        balance != 0L -> "Bring its balance to ₹0 first by moving the money to another account."
        account.isSpending && accounts.none { it.id != account.id && !it.archived && it.isSpending } -> "This is your only spending account."
        else -> null
    }

    private val AccountEntity.isSpending get() = type == AccountType.BANK || type == AccountType.CASH

    /** A typed day: empty is "no day" (Day(null)), 1 to 31 is that day, anything else is null (invalid). */
    private class Day(val value: Int?)
    private fun day(text: String): Day? {
        val d = text.filter(Char::isDigit)
        if (d.isEmpty()) return Day(null)
        return d.take(3).toInt().takeIf { it in 1..31 }?.let { Day(it) }
    }
}
