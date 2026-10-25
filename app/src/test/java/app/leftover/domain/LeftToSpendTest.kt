package app.leftover.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LeftToSpendTest {
    private val sample = LeftToSpend(
        inAccounts = rupees(12930),
        bills = listOf(Deduction("Spotify", rupees(119)), Deduction("Jio", rupees(349)), Deduction("Electricity", rupees(682)), Deduction("Gym", rupees(2000))),
        cardDues = listOf(Deduction("ICICI card", rupees(2500))),
        goals = listOf(Deduction("New phone", rupees(2000)), Deduction("Goa trip", rupees(1000))),
        comingBack = rupees(2340),
        daysUntilPayday = 3,
    )

    @Test fun matchesTheDesignExample() {
        assertEquals(rupees(4280), sample.left)
        assertEquals(rupees(1426), sample.perDay)
    }

    @Test fun moneyOwedToYouIsNotCounted() {
        assertEquals(sample.copy(comingBack = 0).left, sample.left)
    }

    @Test fun overspendingNeverGivesANegativeDailyAmount() {
        val tight = sample.copy(inAccounts = rupees(5000))
        assertEquals(rupees(-3650), tight.left)
        assertEquals(0L, tight.perDay)
    }

    @Test fun slicesCoverTheWholeBalance() {
        assertEquals(1f, sample.slices().sum(), 0.001f)
    }
}
