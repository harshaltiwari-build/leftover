package app.leftover.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinTest {
    @Test fun `only four to six digits are allowed`() {
        assertTrue(Pin.valid("1234")); assertTrue(Pin.valid("123456"))
        assertFalse(Pin.valid("123")); assertFalse(Pin.valid("1234567")); assertFalse(Pin.valid("12a4"))
    }

    @Test fun `hash checks the right pin and is salted`() {
        val salt = Pin.newSalt()
        val hash = Pin.hash("2580", salt)
        assertTrue(Pin.matches("2580", hash, salt))
        assertFalse(Pin.matches("2581", hash, salt))
        assertNotEquals(hash, Pin.hash("2580", Pin.newSalt()))
    }

    @Test fun `waits grow after the free tries and stop at the cap`() {
        assertEquals(0, Pin.waitAfter(4))
        assertEquals(30_000, Pin.waitAfter(5))
        assertEquals(60_000, Pin.waitAfter(6))
        assertEquals(120_000, Pin.waitAfter(7))
        assertEquals(Pin.MAX_WAIT_MS, Pin.waitAfter(40))
    }
}
