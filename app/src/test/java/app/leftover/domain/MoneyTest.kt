package app.leftover.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test fun groupsInIndianStyle() {
        assertEquals("999", Money.groupIndian(999))
        assertEquals("4,280", Money.groupIndian(4280))
        assertEquals("1,00,000", Money.groupIndian(100000))
        assertEquals("12,34,567", Money.groupIndian(1234567))
    }

    @Test fun formatsSignsAndRounding() {
        assertEquals("₹4,280", Money.format(rupees(4280)))
        assertEquals("−₹180", Money.format(-rupees(180)))
        assertEquals("+₹600", Money.format(rupees(600), signed = true))
        assertEquals("₹13", Money.format(1250))
        assertEquals("₹12.50", Money.format(1250, showPaise = true))
    }

    @Test fun parsesKeypadInput() {
        assertEquals(rupees(1250), Money.parse("1,250"))
        assertEquals(12550L, Money.parse("125.5"))
        assertNull(Money.parse(""))
        assertNull(Money.parse("1.2.3"))
    }

    @Test fun inputTextKeepsPaiseOnlyWhenThereAreSome() {
        assertEquals("33", Money.toInput(3300))
        assertEquals("33.33", Money.toInput(3333))
        assertEquals("0.05", Money.toInput(5))
        assertEquals(3333L, Money.parse(Money.toInput(3333)))
    }
}
