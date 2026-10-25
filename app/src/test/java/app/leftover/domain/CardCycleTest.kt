package app.leftover.domain

import app.leftover.data.db.AccountEntity
import app.leftover.data.db.AccountType
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CardCycleTest {
    private val today = LocalDate.of(2026, 10, 25)

    @Test fun lastStatementIsTheLatestOneOnOrBeforeToday() {
        assertEquals(LocalDate.of(2026, 10, 12), CardCycle.lastStatement(12, today))
        assertEquals(LocalDate.of(2026, 9, 28), CardCycle.lastStatement(28, today))
        assertEquals(today, CardCycle.lastStatement(25, today))
    }

    @Test fun theBillIsDueOnTheFirstDueDayAfterTheStatement() {
        assertEquals(LocalDate.of(2026, 11, 1), CardCycle.dueAfter(1, LocalDate.of(2026, 10, 12)))
        assertEquals(LocalDate.of(2026, 10, 30), CardCycle.dueAfter(30, LocalDate.of(2026, 10, 12)))
        assertEquals(LocalDate.of(2026, 2, 28), CardCycle.dueAfter(31, LocalDate.of(2026, 2, 5)))
    }

    @Test fun billedAndUnbilledSplitWhatIsOwed() {
        val info = CardCycle.info(statementDay = 12, dueDay = 1, owed = rupees(9000), unbilled = rupees(2500), today = today)
        assertEquals(rupees(6500), info.billed)
        assertEquals(rupees(2500), info.unbilled)
        assertEquals(LocalDate.of(2026, 11, 1), info.billDue)
        assertEquals(LocalDate.of(2026, 11, 12), info.nextStatement)
        // next bill: statement 12 Nov, due 1 Dec, so a purchase today waits 37 days
        assertEquals(LocalDate.of(2026, 12, 1), info.nextBillDue)
        assertEquals(37, info.interestFreeDays)
    }

    @Test fun unbilledNeverExceedsWhatIsOwed() {
        val info = CardCycle.info(12, 1, rupees(1000), rupees(5000), today)
        assertEquals(0L, info.billed)
        assertEquals(rupees(1000), info.unbilled)
    }

    private val zone = ZoneId.systemDefault()
    private fun millis(date: LocalDate) = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun leftToSpendOnlyReservesTheBilledPartOfACardWithAStatementDay() {
        val bank = AccountEntity(id = 1, name = "Bank", type = AccountType.BANK, openingBalance = rupees(20000), includeInLeft = true)
        val card = AccountEntity(id = 2, name = "Card", type = AccountType.CARD, openingBalance = -rupees(6500), includeInLeft = true, dueDay = 27, statementDay = 12)
        val unbilledSpend = TransactionEntity(type = TxType.EXPENSE, amount = rupees(2500), accountId = 2, occurredAt = millis(LocalDate.of(2026, 10, 20)))
        val cycle = PayCycle.forPayday(28, today)
        val ledger = Ledger(listOf(bank, card), emptyList(), listOf(unbilledSpend), emptyList(), emptyList(), cycle, today)
        val info = ledger.cardCycle(card)!!
        assertEquals(rupees(6500), info.billed)
        assertEquals(rupees(2500), info.unbilled)
        assertEquals(rupees(13500), ledger.leftToSpend.left)
    }

    @Test fun aCardWithoutAStatementDayKeepsTheOldRule() {
        val bank = AccountEntity(id = 1, name = "Bank", type = AccountType.BANK, openingBalance = rupees(20000), includeInLeft = true)
        val card = AccountEntity(id = 2, name = "Card", type = AccountType.CARD, openingBalance = -rupees(9000), includeInLeft = true, dueDay = 27)
        val cycle = PayCycle.forPayday(28, today)
        val ledger = Ledger(listOf(bank, card), emptyList(), emptyList(), emptyList(), emptyList(), cycle, today)
        assertNull(ledger.cardCycle(card))
        assertEquals(rupees(11000), ledger.leftToSpend.left)
    }
}
