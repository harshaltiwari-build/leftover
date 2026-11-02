package app.leftover.domain

import app.leftover.data.db.AccountType
import app.leftover.data.db.CategoryKind
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxSource
import app.leftover.data.db.TxType
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Whether a shared payment screenshot saves itself. Only a clearly read, successful payment you made today or
 * yesterday, that isn't already in the books, is saved without asking, and it comes with Undo. Everything else, and
 * all money received, opens Quick add filled in for you to check.
 */
object ScreenshotAutoSave {
    /** What was read off the screenshot. [reference] is the note to keep, such as "UPI ref 4123…". */
    data class Shot(
        val read: Boolean, val income: Boolean, val status: ScreenshotStatus, val amount: Paise?, val merchant: String,
        val date: LocalDate?, val time: LocalTime?, val reference: String, val receipt: String?,
    )

    sealed interface Decision {
        data class Save(val tx: TransactionEntity) : Decision
        data class Ask(val reason: String) : Decision
    }

    /** Bigger payments always ask: a wrong one would matter, and any app can hand Leftover a picture. */
    val MAX_AMOUNT: Paise = rupees(50_000)


    /** Screenshots saved without asking per day; past this, each one asks, so a flood of images can't fill the books. */
    const val MAX_PER_DAY = 10

    fun decide(shot: Shot, ledger: Ledger, enabled: Boolean, locked: Boolean, zone: ZoneId = ZoneId.systemDefault(), now: Long = System.currentTimeMillis()): Decision {
        val today = ledger.today
        val amount = shot.amount
        val date = shot.date
        val payee = shot.merchant.trim()
        val problem = when {
            !enabled -> "Automatic saving is off"
            locked -> "Leftover is locked"
            !shot.read -> "Couldn't read it clearly"
            shot.income -> "Money you received: check it first"
            shot.status != ScreenshotStatus.SUCCESS -> "Not a successful payment"
            amount == null || amount <= 0 -> "No amount found"
            amount > MAX_AMOUNT -> "Large amount: check it first"
            payee.isEmpty() -> "No payee found"
            date == null -> "No date found"
            date.isAfter(today) || date.isBefore(today.minusDays(1)) -> "Older than yesterday"
            alreadyHave(amount, date, shot.reference, ledger, zone) -> "Looks like one you already have"
            savedToday(ledger, zone, now) >= MAX_PER_DAY -> "Lots of screenshots today: check this one"
            else -> null
        }
        if (problem != null) return Decision.Ask(problem)
        val account = ledger.accounts.firstOrNull { it.type == AccountType.BANK && it.includeInLeft && !it.archived }
            ?: ledger.accounts.firstOrNull { (it.type == AccountType.BANK || it.type == AccountType.CASH) && !it.archived }
            ?: return Decision.Ask("No spending account to save it to")
        val time = shot.time ?: if (date == today) LocalTime.now() else LocalTime.NOON
        return Decision.Save(
            TransactionEntity(
                type = TxType.EXPENSE, amount = amount!!, accountId = account.id, categoryId = categoryFor(payee, ledger),
                merchant = payee.take(40), note = shot.reference.take(60), occurredAt = date!!.atTime(time).atZone(zone).toInstant().toEpochMilli(),
                receiptFile = shot.receipt, source = TxSource.SCAN,
            ),
        )
    }

    internal fun savedToday(ledger: Ledger, zone: ZoneId, now: Long): Int {
        val day = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return ledger.transactions.count { it.source == TxSource.SCAN && java.time.Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() == day }
    }

    /** Same amount within a day either side, or the same payment reference anywhere: most likely already logged, maybe from a bank text. */
    internal fun alreadyHave(amount: Paise, date: LocalDate, reference: String, ledger: Ledger, zone: ZoneId): Boolean {
        val ref = reference.filter(Char::isDigit).takeIf { it.length >= 8 }
        return ledger.transactions.any { tx ->
            (ref != null && tx.note.filter(Char::isDigit).contains(ref)) ||
                (tx.type == TxType.EXPENSE && tx.amount == amount &&
                    abs(ChronoUnit.DAYS.between(java.time.Instant.ofEpochMilli(tx.occurredAt).atZone(zone).toLocalDate(), date)) <= 1)
        }
    }

    /** The category you last gave this payee; else one whose icon fits the name ("Third Wave Coffee" → Coffee); else Other. */
    internal fun categoryFor(payee: String, ledger: Ledger): Long? {
        SmsPolicy.categoryFor(payee, ledger.transactions)?.let { return it }
        val expense = ledger.categories.filter { it.kind == CategoryKind.EXPENSE && !it.hidden }
        val key = IconKeys.guess(payee)
        if (key != "other") expense.firstOrNull { it.icon == key }?.let { return it.id }
        return (expense.firstOrNull { it.name.equals("Other", ignoreCase = true) } ?: expense.firstOrNull())?.id
    }
}
