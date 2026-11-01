package app.leftover.domain

import app.leftover.data.db.AccountEntity
import app.leftover.data.db.AccountType
import app.leftover.data.db.GoalEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Goals that won't make their date at the current saving, and the two ways to catch up. */
class GoalsBehindTest {
    private val today = LocalDate.of(2026, 11, 15)
    private val bank = AccountEntity(id = 1, name = "HDFC", type = AccountType.BANK, openingBalance = rupees(50_000), includeInLeft = true)
    // Next payday 1 Dec; a 1 Feb date leaves 3 paydays (Dec, Jan, Feb).
    private val goa = GoalEntity(id = 1, name = "Goa trip", target = rupees(30_000), perCycle = rupees(5_000), cover = "beach", targetEpochDay = LocalDate.of(2027, 2, 1).toEpochDay())
    private val phone = GoalEntity(id = 2, name = "Phone", target = rupees(9_000), perCycle = rupees(3_000), cover = "phone", targetEpochDay = LocalDate.of(2027, 2, 1).toEpochDay())
    private val rainy = GoalEntity(id = 3, name = "Rainy day", target = rupees(100_000), perCycle = rupees(1_000), cover = "umbrella")
    private fun ledger(goals: List<GoalEntity>) = Ledger(listOf(bank), emptyList(), emptyList(), goals, emptyList(), PayCycle.forPayday(1, today), today)

    @Test fun `only dated goals that won't make it are behind, biggest gap first`() {
        val behind = GoalsBehind.of(ledger(listOf(phone, rainy, goa)))
        assertEquals(listOf("Goa trip"), behind.map { it.goal.name })
        val g = behind.single()
        assertEquals(rupees(10_000), g.raiseTo)
        assertEquals(LocalDate.of(2027, 5, 1), g.newDate)
    }

    @Test fun `either fix puts it back on track`() {
        val g = GoalsBehind.of(ledger(listOf(goa))).single()
        assertTrue(GoalsBehind.of(ledger(listOf(goa.copy(perCycle = g.raiseTo)))).isEmpty())
        assertTrue(GoalsBehind.of(ledger(listOf(goa.copy(targetEpochDay = g.newDate!!.toEpochDay())))).isEmpty())
    }

    @Test fun `a goal with nothing set aside can only be fixed by saving`() {
        val g = GoalsBehind.of(ledger(listOf(goa.copy(perCycle = 0)))).single()
        assertEquals(null, g.newDate)
        assertEquals(rupees(10_000), g.raiseTo)
    }

    @Test fun `archived and finished goals are left out`() {
        assertTrue(GoalsBehind.of(ledger(listOf(goa.copy(archived = true)))).isEmpty())
        assertTrue(GoalsBehind.of(ledger(listOf(goa.copy(target = 0)))).isEmpty())
    }

    // Attack: a close date can ask for more each payday than you earn. Then only moving the date is offered.
    @Test fun `saving more is only offered when it fits in what comes in`() {
        val g = GoalsBehind.of(ledger(listOf(goa))).single()
        assertTrue(g.canRaise(usualIncome = rupees(45_000)))
        assertTrue(!g.canRaise(usualIncome = rupees(8_000)))
        // Income not set: offer it, there's nothing to compare with.
        assertTrue(g.canRaise(usualIncome = 0))
    }
}
