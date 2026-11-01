package app.leftover.domain

import app.leftover.data.db.AccountEntity
import app.leftover.data.db.AccountType
import app.leftover.data.db.BillEntity
import app.leftover.data.db.EmiEntity
import app.leftover.data.db.GoalEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** Everything between today and payday, in date order, with what's left in your accounts after each one. */
class MoneyTimelineTest {
    private val today = LocalDate.of(2026, 11, 15)
    private val bank = AccountEntity(id = 1, name = "HDFC", type = AccountType.BANK, openingBalance = rupees(20_000), includeInLeft = true)
    private val card = AccountEntity(id = 2, name = "ICICI card", type = AccountType.CARD, openingBalance = -rupees(3_000), includeInLeft = true, dueDay = 22)
    private val bills = listOf(
        BillEntity(id = 1, name = "Netflix", amount = rupees(649), dueDay = 16, icon = "tv"),
        BillEntity(id = 2, name = "Electricity", amount = rupees(1_280), dueDay = 20, icon = "bolt"),
        BillEntity(id = 3, name = "Rent", amount = rupees(9_000), dueDay = 5, icon = "home"),
    )
    private val emi = EmiEntity(id = 1, name = "Phone EMI", monthlyAmount = rupees(2_500), totalMonths = 12, dueDay = 25)
    private val goal = GoalEntity(id = 1, name = "Goa trip", target = rupees(30_000), perCycle = rupees(5_000), cover = "beach")

    private fun ledger(b: List<BillEntity> = bills, goals: List<GoalEntity> = listOf(goal), emis: List<EmiEntity> = listOf(emi), accounts: List<AccountEntity> = listOf(bank, card)) =
        Ledger(accounts, emptyList(), emptyList(), goals, b, PayCycle.forPayday(1, today), today, emis = emis)

    @Test fun `events run in date order with the balance after each`() {
        val t = MoneyTimeline.build(ledger(), usualIncome = rupees(45_000))
        assertEquals(rupees(20_000), t.start)
        assertEquals(
            listOf(
                Triple(today, "Goa trip", rupees(15_000)),
                Triple(today.plusDays(1), "Netflix", rupees(14_351)),
                Triple(today.plusDays(5), "Electricity", rupees(13_071)),
                Triple(today.plusDays(7), "ICICI card", rupees(10_071)),
                Triple(today.plusDays(10), "Phone EMI", rupees(7_571)),
            ),
            t.outgoings.map { Triple(it.date, it.title, it.after) },
        )
        assertEquals(listOf(TimelineKind.GOAL, TimelineKind.BILL, TimelineKind.BILL, TimelineKind.CARD, TimelineKind.EMI), t.outgoings.map { it.kind })
    }

    @Test fun `it ends at payday with what Left to spend says`() {
        val l = ledger()
        val t = MoneyTimeline.build(l, usualIncome = rupees(45_000))
        assertEquals(l.leftToSpend.left, t.leftAtPayday)
        assertEquals(LocalDate.of(2026, 12, 1), t.payday.date)
        assertEquals(rupees(45_000), t.payday.amount)
        assertEquals(TimelineKind.PAYDAY, t.payday.kind)
    }

    @Test fun `a bill due after payday waits for the next cycle`() {
        val t = MoneyTimeline.build(ledger(), usualIncome = 0)
        assertEquals(false, t.outgoings.any { it.title == "Rent" })
    }

    @Test fun `with nothing due, it's just today and payday`() {
        val t = MoneyTimeline.build(ledger(b = emptyList(), goals = emptyList(), emis = emptyList(), accounts = listOf(bank)), usualIncome = 0)
        assertEquals(emptyList<TimelineEvent>(), t.outgoings)
        assertEquals(rupees(20_000), t.leftAtPayday)
    }

    @Test fun `the biggest days are easy to find`() {
        val t = MoneyTimeline.build(ledger(), usualIncome = 0)
        assertEquals(today, t.byDay.first().first)
        assertEquals(5, t.byDay.size)
        assertEquals(rupees(649), t.byDay[1].second.sumOf { it.amount })
    }

    // Attack: an unpaid bill from earlier this month is due now, not shown in the past.
    @Test fun `an overdue bill shows today`() {
        val l = Ledger(listOf(bank), emptyList(), emptyList(), emptyList(), listOf(BillEntity(id = 9, name = "Gym", amount = rupees(1_500), dueDay = 10, icon = "gym", paidThroughEpochDay = null)), PayCycle.forPayday(1, today), today)
        val t = MoneyTimeline.build(l, 0)
        t.outgoings.forEach { assert(!it.date.isBefore(today)) { "${it.title} on ${it.date}" } }
        assertEquals(l.leftToSpend.left, t.leftAtPayday)
    }

    // Attack: hundreds of bills, cards and EMIs still add up to Home's number, quickly.
    @Test fun `hundreds of payments still match Left to spend`() {
        val many = (1..300).map { BillEntity(id = it.toLong(), name = "Bill $it", amount = rupees(it.toLong()), dueDay = 1 + it % 28, icon = "other") }
        val emis = (1..50).map { EmiEntity(id = it.toLong(), name = "EMI $it", monthlyAmount = rupees(100), totalMonths = 10, dueDay = 1 + it % 28) }
        val l = Ledger(listOf(bank, card), emptyList(), emptyList(), listOf(goal), many, PayCycle.forPayday(1, today), today, emis = emis)
        val start = System.nanoTime()
        val t = MoneyTimeline.build(l, 0)
        assert((System.nanoTime() - start) / 1_000_000 < 1_000)
        assertEquals(l.leftToSpend.left, t.leftAtPayday)
        assertEquals(t.outgoings.sortedBy { it.date }.map { it.date }, t.outgoings.map { it.date })
    }
}
