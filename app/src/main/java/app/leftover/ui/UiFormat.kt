package app.leftover.ui

import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxType
import app.leftover.domain.Ledger
import app.leftover.domain.toLocalDate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object UiFormat {
    /** 1st, 2nd, 3rd, 4th, 11th, 22nd. */
    fun ordinal(day: Int): String = "$day" + if (day in 11..13) "th" else when (day % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }

    private val weekdayDayMonth = DateTimeFormatter.ofPattern("EEE d MMM")
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM")
    private val time = DateTimeFormatter.ofPattern("h:mm a")

    /** "Today · Sun 25 Oct", "Yesterday", or "Fri 23 Oct". */
    fun dayHeader(date: LocalDate, today: LocalDate): String = when (ChronoUnit.DAYS.between(date, today)) {
        0L -> "Today · ${date.format(weekdayDayMonth)}"
        1L -> "Yesterday"
        else -> date.format(weekdayDayMonth)
    }

    fun shortDay(date: LocalDate, today: LocalDate): String = when (ChronoUnit.DAYS.between(today, date)) {
        0L -> "Today"
        1L -> "Tomorrow"
        in 2L..6L -> "In ${ChronoUnit.DAYS.between(today, date)} days"
        else -> date.format(dayMonth)
    }

    fun pastDay(date: LocalDate, today: LocalDate): String = when (ChronoUnit.DAYS.between(date, today)) {
        0L -> "Today"
        1L -> "Yesterday"
        else -> date.format(dayMonth)
    }

    fun clock(epochMillis: Long): String =
        java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault()).format(time).lowercase()

    fun dayMonth(date: LocalDate): String = date.format(dayMonth)

    /** "1 payment", "2 payments": the right plural for a count. */
    fun count(n: Int, one: String, many: String = one + "s"): String = "$n ${if (n == 1) one else many}"

    fun greeting(hour: Int): String = when {
        hour < 12 -> "Morning"
        hour < 17 -> "Afternoon"
        else -> "Evening"
    }

    /** The label for a transaction row: merchant, or the category, or the type as a last resort. */
    fun title(tx: TransactionEntity, ledger: Ledger): String = when {
        tx.merchant.isNotBlank() -> tx.merchant
        tx.type == TxType.TRANSFER -> "${ledger.accounts.firstOrNull { it.id == tx.accountId }?.name ?: "Account"} → ${ledger.accounts.firstOrNull { it.id == tx.toAccountId }?.name ?: "Account"}"
        else -> ledger.category(tx.categoryId)?.name ?: tx.type.label()
    }

    /** A short label for entries that aren't ordinary spending or income, so they can't be mistaken for it. */
    fun tag(tx: TransactionEntity, ledger: Ledger): String? = when (tx.type) {
        TxType.REFUND -> "Refund"
        TxType.REPAYMENT -> "Repayment"
        TxType.TRANSFER -> if (ledger.accounts.firstOrNull { it.id == tx.toAccountId }?.type == app.leftover.data.db.AccountType.CARD) "Card payment" else "Not spending"
        TxType.SETTLE_OUT -> "Not spending"
        else -> null
    }

    fun subtitle(tx: TransactionEntity, ledger: Ledger): String {
        val account = ledger.accounts.firstOrNull { it.id == tx.accountId }?.name
        val category = ledger.category(tx.categoryId)?.name
        return when (tx.type) {
            TxType.TRANSFER -> "Own accounts"
            TxType.REPAYMENT -> "Money back · not income"
            TxType.SETTLE_OUT -> "Paid a friend · not spending"
            TxType.REFUND -> listOfNotNull("Refund", category, account).joinToString(" · ")
            else -> listOfNotNull(category, account).joinToString(" · ")
        }
    }

    fun TxType.label(): String = when (this) {
        TxType.EXPENSE -> "Expense"
        TxType.INCOME -> "Income"
        TxType.TRANSFER -> "Transfer"
        TxType.REFUND -> "Refund"
        TxType.REPAYMENT -> "Repayment"
        TxType.SETTLE_OUT -> "Paid back"
        TxType.GOAL_SAVE -> "Saved to goal"
        TxType.GOAL_WITHDRAW -> "Taken from goal"
    }

    /** Sign shown in front of the amount: money out is negative, money in positive, transfers neutral. */
    fun signed(tx: TransactionEntity): Long = when (tx.type) {
        TxType.EXPENSE, TxType.GOAL_SAVE, TxType.SETTLE_OUT -> -tx.amount
        TxType.INCOME, TxType.REFUND, TxType.REPAYMENT, TxType.GOAL_WITHDRAW -> tx.amount
        TxType.TRANSFER -> tx.amount
    }

    fun iconKey(tx: TransactionEntity, ledger: Ledger): String = when (tx.type) {
        TxType.TRANSFER, TxType.GOAL_SAVE, TxType.GOAL_WITHDRAW -> "transfer"
        TxType.REPAYMENT, TxType.SETTLE_OUT -> "moneyback"
        TxType.REFUND -> "refund"
        TxType.INCOME -> "income"
        TxType.EXPENSE -> ledger.category(tx.categoryId)?.icon ?: "other"
    }

    fun colorKey(tx: TransactionEntity, ledger: Ledger): String = when (tx.type) {
        TxType.TRANSFER, TxType.GOAL_SAVE, TxType.GOAL_WITHDRAW, TxType.REPAYMENT, TxType.SETTLE_OUT -> "grey"
        TxType.INCOME -> "lime"
        else -> ledger.category(tx.categoryId)?.color ?: "grey"
    }

    fun date(tx: TransactionEntity): LocalDate = tx.occurredAt.toLocalDate()
}
