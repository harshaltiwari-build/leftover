package app.leftover.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AffordCheckTest {
    private val left = rupees(4280)

    @Test fun smallPurchaseWithinBudgetIsComfortable() {
        val r = AffordCheck.check(left, rupees(300), 3, categorySpent = rupees(1000), categoryLimit = rupees(3000))
        assertEquals(Verdict.COMFORTABLE, r.verdict)
        assertNull(r.categoryOverBy)
    }

    @Test fun purchaseThatBlowsTheCategoryBudgetIsAStretchEvenIfAffordable() {
        val r = AffordCheck.check(left, rupees(300), 3, categorySpent = rupees(3420), categoryLimit = rupees(3000))
        assertEquals(Verdict.STRETCH, r.verdict)
        assertEquals(rupees(720), r.categoryOverBy)
    }

    @Test fun purchaseThatEatsMostOfTheDailyAmountIsAStretch() {
        val r = AffordCheck.check(left, rupees(2999), 3, categorySpent = 0, categoryLimit = null)
        assertEquals(Verdict.STRETCH, r.verdict)
        assertEquals(rupees(1281), r.leftAfter)
        assertEquals(rupees(427), r.perDayAfter)
    }

    @Test fun purchaseBiggerThanWhatIsFreeCannotBeAffordedBeforePayday() {
        val r = AffordCheck.check(left, rupees(5999), 3, categorySpent = 0, categoryLimit = null)
        assertEquals(Verdict.NOT_BEFORE_PAYDAY, r.verdict)
        assertEquals(rupees(1719), r.shortBy)
        assertEquals(0L, r.perDayAfter)
    }

    @Test fun noMoneyLeftNeverDividesByZero() {
        val r = AffordCheck.check(0, rupees(10), 0, categorySpent = 0, categoryLimit = null)
        assertEquals(Verdict.NOT_BEFORE_PAYDAY, r.verdict)
    }
}
