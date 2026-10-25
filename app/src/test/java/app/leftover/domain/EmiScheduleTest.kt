package app.leftover.domain

import app.leftover.data.db.AccountEntity
import app.leftover.data.db.AccountType
import app.leftover.data.db.EmiEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EmiScheduleTest {
    private val today = LocalDate.of(2026, 10, 25)
    private val emi = EmiEntity(id = 1, name = "Phone", monthlyAmount = rupees(2000), totalMonths = 12, paidMonths = 4, dueDay = 27)

    @Test fun summaryCountsWhatIsLeft() {
        val s = EmiSchedule.summary(emi, today)
        assertEquals(8, s.remainingMonths)
        assertEquals(rupees(16000), s.remainingToPay)
        assertEquals(rupees(16000), s.outstanding)
        assertEquals(LocalDate.of(2026, 10, 27), s.nextDue)
        assertEquals(LocalDate.of(2027, 5, 27), s.lastDue)
    }

    @Test fun aFinishedPlanHasNothingDue() {
        val s = EmiSchedule.summary(emi.copy(paidMonths = 12), today)
        assertEquals(0, s.remainingMonths)
        assertNull(s.nextDue)
    }

    @Test fun anInstalmentPaidThisMonthMovesTheNextDueToNextMonth() {
        val paid = emi.copy(paidMonths = 5, paidThroughEpochDay = LocalDate.of(2026, 10, 27).toEpochDay())
        assertEquals(LocalDate.of(2026, 11, 27), EmiSchedule.summary(paid, today).nextDue)
    }

    @Test fun interestMakesTheOutstandingPrincipalLessThanTheInstalments() {
        val withRate = emi.copy(annualRateBps = 1200)
        val owed = EmiSchedule.outstanding(withRate.monthlyAmount, 8, 1200)
        assertTrue(owed < rupees(16000))
        // 2000 a month for 8 months at 1% a month is about 15,303
        assertEquals(15303L, owed / 100)
    }

    @Test fun prepayingWithoutInterestShortensByWholeInstalments() {
        val p = EmiSchedule.prepay(emi, rupees(6000))!!
        assertEquals(3, p.monthsSaved)
        assertEquals(5, p.newRemainingMonths)
        assertEquals(0L, p.interestSaved)
    }

    @Test fun prepayingWithInterestSavesMonthsAndInterest() {
        val p = EmiSchedule.prepay(emi.copy(annualRateBps = 1200), rupees(5000))!!
        assertTrue(p.monthsSaved in 2..3)
        assertTrue(p.interestSaved > 0)
    }

    @Test fun prepayingEverythingEndsThePlan() {
        val owed = EmiSchedule.outstanding(emi.monthlyAmount, 8, null)
        val p = EmiSchedule.prepay(emi, owed)!!
        assertEquals(0, p.newRemainingMonths)
        assertEquals(8, p.monthsSaved)
    }

    @Test fun nonsensePrepaymentsAreRefused() {
        assertNull(EmiSchedule.prepay(emi, 0))
        assertNull(EmiSchedule.prepay(emi, rupees(999999)))
        assertNotNull(EmiSchedule.prepay(emi, rupees(1)))
    }

    @Test fun anEmiDueBeforePaydayReducesLeftToSpend() {
        val bank = AccountEntity(id = 1, name = "Bank", type = AccountType.BANK, openingBalance = rupees(10000), includeInLeft = true)
        val cycle = PayCycle.forPayday(28, today)
        val ledger = Ledger(listOf(bank), emptyList(), emptyList(), emptyList(), emptyList(), cycle, today, emis = listOf(emi))
        assertEquals(rupees(8000), ledger.leftToSpend.left)
        assertEquals("Phone", ledger.leftToSpend.bills.single().label)
    }

    @Test fun anEmiDueAfterPaydayIsNotYetDeducted() {
        val bank = AccountEntity(id = 1, name = "Bank", type = AccountType.BANK, openingBalance = rupees(10000), includeInLeft = true)
        val cycle = PayCycle.forPayday(20, today) // next payday 20 Nov, so the 27th falls in the following cycle
        val late = emi.copy(dueDay = 27, paidThroughEpochDay = LocalDate.of(2026, 10, 27).toEpochDay())
        val ledger = Ledger(listOf(bank), emptyList(), emptyList(), emptyList(), emptyList(), cycle, today, emis = listOf(late))
        assertEquals(rupees(10000), ledger.leftToSpend.left)
    }
}
