package app.leftover.domain

import app.leftover.data.db.AccountEntity
import app.leftover.data.db.AccountType
import app.leftover.data.db.CategoryEntity
import app.leftover.data.db.CategoryKind
import app.leftover.data.db.GoalEntity
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxType
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Home's main card when the numbers can't be normal: no balance yet, over until payday, and payday itself. */
class HeroStateTest {
    private val zone = ZoneId.systemDefault()
    private fun at(d: LocalDate) = d.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val food = CategoryEntity(id = 1, name = "Food", icon = "food", color = "peach", kind = CategoryKind.EXPENSE)
    private fun bank(rupees: Long, counts: Boolean = true) = AccountEntity(id = 1, name = "HDFC", type = AccountType.BANK, openingBalance = rupees(rupees), includeInLeft = counts)
    private fun ledger(today: LocalDate, accounts: List<AccountEntity>, txs: List<TransactionEntity> = emptyList(), goals: List<GoalEntity> = emptyList()) =
        Ledger(accounts, listOf(food), txs, goals, emptyList(), PayCycle.forPayday(1, today), today)
    private val mid = LocalDate.of(2026, 11, 15)

    @Test fun `normal days say left until payday`() {
        val h = HeroState.of(ledger(mid, listOf(bank(10_000))), usualIncome = rupees(45_000), hidden = false)
        assertEquals(HeroKind.NORMAL, h.kind)
        assertEquals("Left until payday", h.title)
        assertNull(h.line)
    }

    @Test fun `no balance asks for one instead of showing a made-up number`() {
        val h = HeroState.of(ledger(mid, listOf(bank(0, counts = false))), 0, false)
        assertEquals(HeroKind.NO_BALANCE, h.kind)
        assertEquals("Add roughly what's in your accounts and Leftover works out what's safe.", h.line)
        assertEquals("Add balance", h.action)
    }

    @Test fun `over until payday is calm and points to a way out`() {
        val spent = TransactionEntity(id = 1, type = TxType.EXPENSE, amount = rupees(1_240), accountId = 1, categoryId = 1, occurredAt = at(mid))
        val goa = GoalEntity(id = 1, name = "Goa trip", target = rupees(30_000), perCycle = rupees(3_125), cover = "beach")
        val h = HeroState.of(ledger(mid, listOf(bank(3_125)), listOf(spent), listOf(goa)), 0, false)
        assertEquals(HeroKind.OVER, h.kind)
        assertEquals("Over until payday", h.title)
        assertEquals("This would come out of bill or goal money. Pausing Goa trip this cycle frees ₹3,125.", h.line)
        assertEquals("See options", h.action)
    }

    @Test fun `payday celebrates and offers last cycle's recap when there was one`() {
        val payday = LocalDate.of(2026, 12, 1)
        val last = TransactionEntity(id = 1, type = TxType.EXPENSE, amount = rupees(500), accountId = 1, categoryId = 1, occurredAt = at(payday.minusDays(5)))
        val h = HeroState.of(ledger(payday, listOf(bank(30_000)), listOf(last)), usualIncome = rupees(45_000), hidden = false)
        assertEquals(HeroKind.PAYDAY, h.kind)
        assertEquals("It's payday", h.title)
        assertEquals("Yours for December after bills and goals. About ₹951 a day.", h.line)
        assertEquals("See November's recap", h.action)
        assertNull(HeroState.of(ledger(payday, listOf(bank(30_000))), 0, false).action)
    }

    // Found when the tests ran on a real payday: someone who set up the app this morning has nothing to celebrate yet.
    @Test fun `payday is only celebrated once the app was used last cycle`() {
        val payday = LocalDate.of(2026, 12, 1)
        val todayOnly = TransactionEntity(id = 1, type = TxType.EXPENSE, amount = rupees(200), accountId = 1, categoryId = 1, occurredAt = at(payday))
        assertEquals(HeroKind.NORMAL, HeroState.of(ledger(payday, listOf(bank(30_000)), listOf(todayOnly)), 0, false).kind)
    }

    @Test fun `hide balances keeps amounts out of the words`() {
        val spent = TransactionEntity(id = 1, type = TxType.EXPENSE, amount = rupees(1_240), accountId = 1, categoryId = 1, occurredAt = at(mid))
        val goa = GoalEntity(id = 1, name = "Goa trip", target = rupees(30_000), perCycle = rupees(3_125), cover = "beach")
        listOf(
            HeroState.of(ledger(mid, listOf(bank(3_125)), listOf(spent), listOf(goa)), 0, true),
            HeroState.of(ledger(LocalDate.of(2026, 12, 1), listOf(bank(30_000))), 0, true),
        ).forEach { assertFalse(it.toString(), it.line.orEmpty().contains("₹")) }
    }
}
