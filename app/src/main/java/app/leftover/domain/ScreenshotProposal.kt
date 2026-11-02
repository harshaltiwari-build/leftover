package app.leftover.domain

import app.leftover.data.db.AccountType
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxSource
import app.leftover.data.db.TxType
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * What Leftover offers after reading a payment screenshot: the entry it would add, in which account and category, and
 * a warning for anything worth a second look. It is only an offer; the person taps Add (or Edit) on the confirm screen.
 * Anything that isn't a clear successful payment you made has no proposal and goes to the full Quick add form instead.
 */
data class ScreenshotProposal(val tx: TransactionEntity, val accountName: String, val categoryName: String?, val warnings: List<Warning>) {
    enum class Warning(private val shown: String) {
        DUPLICATE("Looks like one you already have"),
        OLD("Older than yesterday: check the date"),
        LARGE("A large amount: check it"),
        NO_PAYEE("No payee found: add one if you like"),
        DATE_ASSUMED("No date found: today is assumed");

        /** The words for this warning. None includes an amount, so they're safe with Hide balances on. */
        fun text(hidden: Boolean): String = shown
    }

    companion object {
        fun of(shot: ScreenshotAutoSave.Shot, ledger: Ledger, zone: ZoneId = ZoneId.systemDefault(), now: Long = System.currentTimeMillis()): ScreenshotProposal? {
            val amount = shot.amount
            if (!shot.read || shot.income || shot.status != ScreenshotStatus.SUCCESS || amount == null || amount <= 0) return null
            val account = ledger.accounts.firstOrNull { it.type == AccountType.BANK && it.includeInLeft && !it.archived }
                ?: ledger.accounts.firstOrNull { (it.type == AccountType.BANK || it.type == AccountType.CASH) && !it.archived }
                ?: return null
            val today = ledger.today
            val payee = shot.merchant.trim().take(40)
            val date = shot.date ?: today
            val time = shot.time ?: if (date == today) LocalTime.now() else LocalTime.NOON
            val categoryId = ScreenshotAutoSave.categoryFor(payee, ledger)
            val warnings = buildList {
                if (ScreenshotAutoSave.alreadyHave(amount, date, shot.reference, ledger, zone)) add(Warning.DUPLICATE)
                if (shot.date != null && date.isBefore(today.minusDays(1))) add(Warning.OLD)
                if (amount > ScreenshotAutoSave.MAX_AMOUNT) add(Warning.LARGE)
                if (payee.isEmpty()) add(Warning.NO_PAYEE)
                if (shot.date == null) add(Warning.DATE_ASSUMED)
            }
            val tx = TransactionEntity(
                type = TxType.EXPENSE, amount = amount, accountId = account.id, categoryId = categoryId, merchant = payee,
                note = shot.reference.take(60), occurredAt = date.atTime(time).atZone(zone).toInstant().toEpochMilli(),
                receiptFile = shot.receipt, source = TxSource.SCAN,
            )
            return ScreenshotProposal(tx, account.name, ledger.category(categoryId)?.name, warnings)
        }
    }
}
