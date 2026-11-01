package app.leftover.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Can I afford this?" answered in today's terms: does it fit today, and what it does to every day after. */
class AffordAnswerTest {
    private val payday = LocalDate.of(2026, 12, 1)
    // The canvas example: ₹615 safe today, ₹230 spent, 16 days to payday, ₹9,610 left.
    private val day = SafeToday(rupees(615), rupees(230), 16, rupees(9_610))
    private fun ask(price: Long, safe: SafeToday = day, spent: Long = 0, limit: Long? = null, category: String = "Shopping", hidden: Boolean = false) =
        AffordAnswer.of(safe, rupees(price), payday, category, rupees(spent), limit?.let(::rupees), hidden)

    @Test fun `a price inside today's safe amount fits`() {
        val a = ask(300)
        assertEquals(Verdict.COMFORTABLE, a.verdict)
        assertEquals("Yes, it fits in today", a.title)
        assertEquals("It's within today's ₹385. Tomorrow would be ₹620 instead of ₹640.", a.body)
    }

    @Test fun `past today's amount but within Left to spend is a stretch, shown per day`() {
        val a = ask(2_999)
        assertEquals(Verdict.STRETCH, a.verdict)
        assertEquals("You can, but it's a stretch", a.title)
        assertEquals("It's ₹2,614 more than today's safe amount, so the next 15 days each get ₹200 less. Waiting until payday costs you nothing.", a.body)
        assertEquals(
            listOf(
                ImpactRow("Safe each day after today", "₹640", "₹440", "Spread over the 15 days to 1 Dec", warning = true),
                ImpactRow("Left until payday", "₹9,610", "₹6,611", "Bills and card dues stay covered", warning = false),
            ),
            a.rows,
        )
    }

    @Test fun `more than Left to spend waits for payday`() {
        val a = ask(10_000)
        assertEquals(Verdict.NOT_BEFORE_PAYDAY, a.verdict)
        assertEquals("Not before payday", a.title)
        assertEquals("It's ₹390 more than you have until payday. It would come out of bill or goal money.", a.body)
        assertEquals(ImpactRow("Left until payday", "₹9,610", "₹390 short", "Dips into money kept for bills and goals", warning = true), a.rows.first { it.name == "Left until payday" })
    }

    @Test fun `a category going over its limit makes it a stretch and says by how much`() {
        val a = ask(300, spent = 2_900, limit = 3_000)
        assertEquals(Verdict.STRETCH, a.verdict)
        assertTrue(a.body.endsWith("Shopping would go ₹200 over its limit."))
        assertEquals(ImpactRow("Shopping budget", "₹100 left", "₹200 over", "Of ₹3,000 this cycle", warning = true), a.rows.last())
    }

    @Test fun `on the last day there are no days after today to spread it over`() {
        val last = SafeToday(rupees(500), 0, 1, rupees(500))
        val a = ask(700, safe = last)
        assertEquals(Verdict.NOT_BEFORE_PAYDAY, a.verdict)
        assertFalse(a.rows.any { it.name == "Safe each day after today" })
        assertEquals(Verdict.COMFORTABLE, ask(400, safe = last).verdict)
    }

    @Test fun `when today is already over, even a small buy is a stretch`() {
        val over = SafeToday(rupees(615), rupees(700), 16, rupees(9_000))
        val a = ask(100, safe = over)
        assertEquals(Verdict.STRETCH, a.verdict)
        assertTrue(a.body, a.body.startsWith("Today is already past its safe amount"))
    }

    @Test fun `hide balances keeps amounts out`() {
        listOf(300L, 2_999L, 10_000L).forEach { price ->
            val a = ask(price, spent = 2_900, limit = 3_000, hidden = true)
            assertFalse(a.body, a.body.any(Char::isDigit))
            a.rows.forEach { assertFalse(it.toString(), (it.from + it.to + it.note).any(Char::isDigit)) }
        }
    }

    // Seen on the phone: with nothing left, "₹0 → ₹0" for every day after today says nothing.
    @Test fun `with nothing left, the per-day line is left out`() {
        val empty = SafeToday(0, rupees(228), 27, -rupees(28))
        assertFalse(ask(999, safe = empty).rows.any { it.name == "Safe each day after today" })
    }
}
