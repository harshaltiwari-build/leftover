package app.leftover.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The words around the safe-today number, which must make sense in every state, including "nothing left". */
class SafeTodayCopyTest {
    private fun safe(target: Long, spent: Long, days: Int, leftNow: Long) = SafeToday(rupees(target), rupees(spent), days, rupees(leftNow))

    @Test fun `normal day`() {
        val c = SafeTodayCopy.of(safe(615, 230, 16, 9_610), hidden = false)
        assertEquals("Safe to spend today", c.title)
        assertEquals("₹385", c.amount)
        assertEquals("of ₹615", c.of)
        assertEquals("Spend nothing more and tomorrow is ₹640", c.line)
        assertFalse(c.warning)
    }

    @Test fun `over today but money left`() {
        val c = SafeTodayCopy.of(safe(615, 1_000, 16, 8_840), hidden = false)
        assertEquals("Over today's target", c.title)
        assertEquals("₹385", c.amount)
        assertEquals("over ₹615", c.of)
        assertEquals("Spread over the 15 days left, tomorrow is ₹589", c.line)
        assertTrue(c.warning)
    }

    @Test fun `nothing left until payday`() {
        // The case seen on the phone: ₹200 in, ₹200 spent, 27 days to go. No "₹193 of ₹7".
        val c = SafeTodayCopy.of(safe(7, 200, 27, 0), hidden = false)
        assertEquals("Nothing left to spend", c.title)
        assertEquals("₹0", c.amount)
        assertEquals("", c.of)
        assertEquals("Essentials only until payday. Add money that came in to update this.", c.line)
        assertTrue(c.warning)
    }

    // The main card already says "Over until payday" and by how much; this card says what that means for today.
    @Test fun `spent past what's left`() {
        val c = SafeTodayCopy.of(safe(0, 500, 10, -1_240), hidden = false)
        assertEquals("Safe to spend today", c.title)
        assertEquals("₹0", c.amount)
        assertEquals("Essentials only until payday", c.line)
        assertTrue(c.warning)
    }

    @Test fun `last day before payday`() {
        val c = SafeTodayCopy.of(safe(500, 0, 1, 500), hidden = false)
        assertEquals("Payday tomorrow: a fresh cycle starts", c.line)
    }

    @Test fun `hidden keeps every amount out`() {
        listOf(safe(615, 230, 16, 9_610), safe(615, 1_000, 16, 8_840), safe(7, 200, 27, 0), safe(0, 500, 10, -1_240)).forEach {
            val c = SafeTodayCopy.of(it, hidden = true)
            assertFalse(c.toString(), (c.of + c.line).contains("₹"))
            assertEquals(Money.HIDDEN, c.amount)
        }
    }

    @Test fun `quick add impact line`() {
        val day = safe(615, 230, 16, 9_610)
        assertEquals("Safe today ₹385", SafeTodayCopy.impact(day, 0))
        assertEquals("Safe today ₹385 → ₹35", SafeTodayCopy.impact(day, rupees(350)))
        assertEquals("Safe today ₹385 → ₹115 over", SafeTodayCopy.impact(day, rupees(500)))
        // Already over before typing: say so instead of a negative "→".
        assertEquals("Already ₹193 over today's target", SafeTodayCopy.impact(safe(7, 200, 27, 0), 0))
        assertEquals("Already ₹193 over today's target", SafeTodayCopy.impact(safe(7, 200, 27, 0), rupees(50)))
    }

    // Attack: Hide balances was ignored on Quick add, showing today's safe amount to anyone looking at the screen.
    @Test fun `quick add impact keeps amounts out when hidden`() {
        val day = safe(615, 230, 16, 9_610)
        listOf(0L, rupees(350), rupees(500)).forEach { assertFalse(SafeTodayCopy.impact(day, it, hidden = true).contains("₹")) }
        assertFalse(SafeTodayCopy.impact(safe(7, 200, 27, 0), 0, hidden = true).contains("₹"))
        assertEquals("Takes you over today's safe amount", SafeTodayCopy.impact(day, rupees(500), hidden = true))
    }

    @Test fun `quick add knows when an amount goes over today`() {
        val day = safe(615, 230, 16, 9_610)
        assertFalse(SafeTodayCopy.goesOver(day, rupees(385)))
        assertTrue(SafeTodayCopy.goesOver(day, rupees(386)))
        assertTrue(SafeTodayCopy.goesOver(safe(7, 200, 27, 0), 0))
    }

    @Test fun `bank text notification line`() {
        val day = safe(615, 230, 16, 9_610)
        assertEquals("Save it and today goes ₹385 → ₹225", SafeTodayCopy.notificationLine(day, rupees(160), hidden = false))
        assertEquals("Save it and today goes ₹385 → ₹115 over", SafeTodayCopy.notificationLine(day, rupees(500), hidden = false))
        assertEquals("Today is already past its safe amount", SafeTodayCopy.notificationLine(safe(615, 700, 16, 9_000), rupees(100), hidden = false))
        // On the lock screen, Hide balances means no amounts at all.
        assertEquals(null, SafeTodayCopy.notificationLine(day, rupees(160), hidden = true))
    }
}
