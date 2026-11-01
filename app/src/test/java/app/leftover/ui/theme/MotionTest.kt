package app.leftover.ui.theme

import app.leftover.domain.rupees
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The headline number counts to its new value; it must land exactly, never overshoot, and stop when motion is reduced. */
class MotionTest {
    @Test fun `the count lands exactly on the real number, paise included`() {
        assertEquals(1_234_567L, Motion.countValue(rupees(10_000), 1_234_567L, 1f))
        assertEquals(rupees(10_000), Motion.countValue(rupees(10_000), 1_234_567L, 0f))
    }

    @Test fun `part-way it shows whole rupees between the two`() {
        val mid = Motion.countValue(rupees(10_000), rupees(9_000), 0.5f)
        assertEquals(rupees(9_500), mid)
        listOf(0.1f, 0.33f, 0.77f, 0.99f).forEach { p ->
            val v = Motion.countValue(rupees(9_000), rupees(10_000), p)
            assertTrue(v in rupees(9_000)..rupees(10_000))
            assertEquals(0L, v % 100)
        }
    }

    @Test fun `big balances don't overflow or lose their sign`() {
        assertEquals(-rupees(50_000_000), Motion.countValue(rupees(50_000_000), -rupees(50_000_000), 1f))
        assertTrue(Motion.countValue(rupees(50_000_000), -rupees(50_000_000), 0.5f) in -rupees(100)..rupees(100))
    }

    @Test fun `reduce motion means no animation`() {
        assertEquals(0, Motion.durationMs(reduce = true))
        assertTrue(Motion.durationMs(reduce = false) in 300..900)
    }
}
