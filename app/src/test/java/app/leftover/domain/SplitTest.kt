package app.leftover.domain

import app.leftover.data.db.GroupEntity
import app.leftover.data.db.GroupKind
import app.leftover.data.db.GroupMemberEntity
import app.leftover.data.db.PersonEntity
import app.leftover.data.db.SettleDirection
import app.leftover.data.db.SettleMethod
import app.leftover.data.db.SettlementEntity
import app.leftover.data.db.SharedExpenseEntity
import app.leftover.data.db.SplitShareEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SplitTest {
    private val rohan = PersonEntity(id = 1, name = "Rohan")
    private val meera = PersonEntity(id = 2, name = "Meera")
    private val kabir = PersonEntity(id = 3, name = "Kabir")
    private val people = listOf(rohan, meera, kabir)
    private val today = LocalDate.of(2026, 10, 25)
    private fun millis(date: LocalDate = today) = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun expense(id: Long, total: Long, paidBy: Long, shares: Map<Long, Int>, group: Long? = null, date: LocalDate = today, category: Long? = 1) =
        SharedExpenseEntity(id = id, title = "Dinner", total = rupees(total), paidBy = paidBy, groupId = group, categoryId = category, occurredAt = millis(date)) to
            shares.map { (person, amount) -> SplitShareEntity(expenseId = id, personId = person, amount = rupees(amount.toLong())) }

    private fun book(vararg items: Pair<SharedExpenseEntity, List<SplitShareEntity>>, settlements: List<SettlementEntity> = emptyList(), groups: List<GroupEntity> = emptyList(), members: List<GroupMemberEntity> = emptyList()) =
        SharedBook(people, groups, members, items.map { it.first }, items.flatMap { it.second }, settlements)

    // ---- splitting ----

    @Test fun equalSplitNeverLosesPaise() {
        val r = SplitCalculator.calculate(rupees(100), listOf(ME, 1, 2), SplitMode.EQUAL)
        assertEquals(listOf(3334L, 3333L, 3333L), r.amounts.values.toList())
        assertEquals(rupees(100), r.assigned)
        assertTrue(r.balanced)
    }

    @Test fun theDesignExampleSplitsFourWays() {
        val r = SplitCalculator.calculate(rupees(2400), listOf(ME, 1, 2, 3), SplitMode.EQUAL)
        assertTrue(r.amounts.values.all { it == rupees(600) })
    }

    @Test fun sharesAreProportional() {
        val r = SplitCalculator.calculate(rupees(2400), listOf(ME, 1, 2, 3), SplitMode.SHARES, shares = mapOf(ME to 1, 1L to 2, 2L to 1, 3L to 1))
        assertEquals(rupees(480), r.amounts[ME])
        assertEquals(rupees(960), r.amounts[1])
        assertEquals(rupees(2400), r.assigned)
    }

    @Test fun sharesThatDontDivideEvenlyStillAddUp() {
        val r = SplitCalculator.calculate(rupees(100), listOf(ME, 1, 2), SplitMode.SHARES, shares = mapOf(ME to 1, 1L to 1, 2L to 5))
        assertEquals(rupees(100), r.assigned)
        assertTrue(r.amounts.getValue(2) > r.amounts.getValue(1))
    }

    @Test fun exactAmountsReportWhatIsLeftToAssign() {
        val r = SplitCalculator.calculate(rupees(2400), listOf(ME, 1), SplitMode.EXACT, exact = mapOf(ME to rupees(700), 1L to rupees(600)))
        assertFalse(r.balanced)
        assertEquals(rupees(1100), r.difference)
        val over = SplitCalculator.calculate(rupees(1000), listOf(ME, 1), SplitMode.EXACT, exact = mapOf(ME to rupees(700), 1L to rupees(600)))
        assertEquals(-rupees(300), over.difference)
    }

    @Test fun nobodyToSplitWithIsNeverBalanced() {
        assertFalse(SplitCalculator.calculate(rupees(100), emptyList(), SplitMode.EQUAL).balanced)
        assertFalse(SplitCalculator.calculate(0, listOf(ME), SplitMode.EQUAL).balanced)
    }

    // ---- balances ----

    @Test fun whenYouPayFriendsOweTheirShare() {
        val b = book(expense(1, 2400, ME, mapOf(ME to 600, 1L to 600, 2L to 600, 3L to 600)))
        assertEquals(rupees(600), b.balanceWith(1))
        assertEquals(rupees(1800), b.owedToYou)
        assertEquals(0L, b.youOwe)
    }

    @Test fun whenAFriendPaysYouOweYourShare() {
        val b = book(expense(1, 1720, 3, mapOf(ME to 860, 3L to 860)))
        assertEquals(-rupees(860), b.balanceWith(3))
        assertEquals(rupees(860), b.youOwe)
    }

    @Test fun balancesNetOffAcrossBills() {
        val b = book(expense(1, 1200, ME, mapOf(ME to 600, 1L to 600)), expense(2, 400, 1, mapOf(ME to 200, 1L to 200)))
        assertEquals(rupees(400), b.balanceWith(1))
    }

    @Test fun settlingBringsTheBalanceBackToZero() {
        val e = expense(1, 1200, ME, mapOf(ME to 600, 1L to 600))
        val paidBack = SettlementEntity(personId = 1, amount = rupees(600), direction = SettleDirection.THEY_PAID, method = SettleMethod.UPI, occurredAt = millis())
        assertEquals(0L, book(e, settlements = listOf(paidBack)).balanceWith(1))
        val f = expense(2, 1720, 3, mapOf(ME to 860, 3L to 860))
        val youPaid = SettlementEntity(personId = 3, amount = rupees(860), direction = SettleDirection.YOU_PAID, method = SettleMethod.CASH, occurredAt = millis())
        assertEquals(0L, book(f, settlements = listOf(youPaid)).balanceWith(3))
    }

    @Test fun anExpensePaidByAThirdPersonDoesNotChangeBalancesWithOthers() {
        val b = book(expense(1, 900, 3, mapOf(ME to 300, 1L to 300, 3L to 300)))
        assertEquals(0L, b.balanceWith(1))
        assertEquals(-rupees(300), b.balanceWith(3))
    }

    // ---- budgets ----

    @Test fun onlyBillsOthersPaidCountAsExtraSpending() {
        val b = book(expense(1, 2400, ME, mapOf(ME to 600, 1L to 1800)), expense(2, 1720, 3, mapOf(ME to 860, 3L to 860)))
        assertEquals(rupees(860), b.othersPaidShare())
        assertEquals(rupees(860), b.othersPaidShareIn(1))
        assertEquals(0L, b.othersPaidShareIn(2))
    }

    @Test fun cycleLimitsWhatCounts() {
        val cycle = PayCycle.forPayday(28, today)
        val old = expense(1, 1000, 3, mapOf(ME to 500, 3L to 500), date = LocalDate.of(2026, 9, 1))
        assertEquals(0L, book(old).othersPaidShare(cycle))
    }

    // ---- groups ----

    @Test fun groupSummaryTotalsAndYourShare() {
        val group = GroupEntity(id = 1, name = "Goa", kind = GroupKind.TRIP, budget = rupees(50000))
        val b = book(
            expense(1, 18000, ME, mapOf(ME to 3600, 1L to 3600, 2L to 3600, 3L to 3600, 4L to 3600), group = 1, category = 1),
            expense(2, 4100, 1, mapOf(ME to 820, 1L to 820, 2L to 820, 3L to 820, 4L to 820), group = 1, category = 2),
            groups = listOf(group),
        )
        val s = b.summary(1)!!
        assertEquals(rupees(22100), s.total)
        assertEquals(rupees(4420), s.yourShare)
        assertEquals(1L, s.byCategory.first().first)
    }

    @Test fun settlementPlanMinimisesPayments() {
        val plan = GroupSettlement.simplify(mapOf(ME to rupees(1200), 1L to -rupees(1200), 2L to rupees(2050), 3L to -rupees(2050)))
        assertEquals(2, plan.size)
        assertEquals(Transfer(from = 3, to = 2, amount = rupees(2050)), plan.first { it.to == 2L })
        assertEquals(Transfer(from = 1, to = ME, amount = rupees(1200)), plan.first { it.to == ME })
    }

    @Test fun planAlwaysBalances() {
        val nets = mapOf(ME to rupees(500), 1L to -rupees(300), 2L to -rupees(400), 3L to rupees(200))
        val plan = GroupSettlement.simplify(nets)
        val received = plan.groupBy { it.to }.mapValues { e -> e.value.sumOf { it.amount } }
        val paid = plan.groupBy { it.from }.mapValues { e -> e.value.sumOf { it.amount } }
        nets.forEach { (id, net) -> assertEquals(net, (received[id] ?: 0L) - (paid[id] ?: 0L)) }
    }

    @Test fun groupSettlementsAreAppliedToTheNets() {
        val group = GroupEntity(id = 1, name = "Flat", kind = GroupKind.HOME)
        val e = expense(1, 1200, ME, mapOf(ME to 600, 1L to 600), group = 1)
        val paid = SettlementEntity(personId = 1, amount = rupees(600), direction = SettleDirection.THEY_PAID, method = SettleMethod.UPI, occurredAt = millis(), groupId = 1)
        val s = book(e, settlements = listOf(paid), groups = listOf(group)).summary(1)!!
        assertTrue(s.plan.isEmpty())
    }

    @Test fun unknownGroupHasNoSummary() {
        assertNull(SharedBook.Empty.summary(99))
    }

    // ---- UPI ----

    @Test fun buildsAPaymentLinkWithEncodedParts() {
        val link = UpiLink.build("kabir@okaxis", "Kabir S", rupees(860), "Office lunches & chai")!!
        assertEquals("upi://pay?pa=kabir%40okaxis&pn=Kabir%20S&am=860.00&cu=INR&tn=Office%20lunches%20%26%20chai", link)
    }

    @Test fun rejectsBadIdsAndAmounts() {
        assertNull(UpiLink.build("not an id", "X", rupees(10), ""))
        assertNull(UpiLink.build("a@b", "X", rupees(10), ""))
        assertNull(UpiLink.build("kabir@okaxis", "X", 0, ""))
        assertTrue(UpiLink.isValidId("9876543210@ybl"))
        assertFalse(UpiLink.isValidId("evil@bank&am=1"))
    }

    @Test fun keepsPaiseInTheAmount() {
        assertTrue(UpiLink.build("kabir@okaxis", "K", 12550, "x")!!.contains("am=125.50"))
    }
}
