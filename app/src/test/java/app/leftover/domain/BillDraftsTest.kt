package app.leftover.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bills picked during setup: every chip can be switched on, edited (name, amount, due day) or removed. */
class BillDraftsTest {
    private val start = BillDrafts.suggested()
    private fun id(name: String, b: BillDrafts = start) = b.items.first { it.name == name }.id
    private fun ok(r: BillDrafts.Change) = (r as BillDrafts.Change.Ok).bills
    private fun error(r: BillDrafts.Change) = (r as BillDrafts.Change.Invalid).message

    @Test fun `suggestions start switched off`() {
        assertEquals(listOf("Spotify", "Recharge", "Electricity", "Gym", "Card bill", "Rent"), start.items.map { it.name })
        assertTrue(start.items.none { it.on })
        assertEquals(0L, start.totalRupees)
    }

    @Test fun `tapping a chip switches it on and off`() {
        val on = start.toggle(id("Rent"))
        assertEquals(9_000L, on.totalRupees)
        assertEquals(0L, on.toggle(id("Rent")).totalRupees)
    }

    @Test fun `editing changes name, amount and due day, and switches the bill on`() {
        val b = ok(start.edit(id("Rent"), "Flat rent", "12000", "5"))
        val rent = b.items.single { it.id == id("Rent") }
        assertEquals("Flat rent", rent.name)
        assertEquals(12_000L, rent.rupees)
        assertEquals(5, rent.dueDay)
        assertTrue(rent.on)
        assertEquals(12_000L, b.totalRupees)
    }

    @Test fun `an empty due day means the day before payday`() {
        assertNull(ok(start.edit(id("Gym"), "Gym", "1500", "")).items.single { it.name == "Gym" }.dueDay)
    }

    @Test fun `edits that don't make sense are refused with a reason`() {
        assertEquals("Give it a name", error(start.edit(id("Rent"), "   ", "100", "")))
        assertEquals("Spotify is already on the list", error(start.edit(id("Rent"), "spotify", "100", "")))
        assertEquals("Enter an amount", error(start.edit(id("Rent"), "Rent", "0", "")))
        assertEquals("Up to ₹99,99,999", error(start.edit(id("Rent"), "Rent", "100000000", "")))
        assertEquals("Pick a day from 1 to 31", error(start.edit(id("Rent"), "Rent", "100", "32")))
        assertEquals("Pick a day from 1 to 31", error(start.edit(id("Rent"), "Rent", "100", "0")))
        // Keeping its own name is fine.
        ok(start.edit(id("Rent"), "RENT", "100", ""))
    }

    @Test fun `other bills are added switched on`() {
        val b = ok(start.add("Wi-Fi", "599", "10"))
        val wifi = b.items.last()
        assertEquals("Wi-Fi", wifi.name)
        assertEquals(10, wifi.dueDay)
        assertTrue(wifi.on)
        assertEquals("Wi-Fi is already on the list", error(b.add("wi-fi", "100", "")))
    }

    @Test fun `any bill can be removed, suggestions included`() {
        val b = start.toggle(id("Spotify")).remove(id("Spotify"))
        assertFalse(b.items.any { it.name == "Spotify" })
        assertEquals(0L, b.totalRupees)
    }

    @Test fun `names are tidied`() {
        val b = ok(start.add("  Maid\n  salary\u0000  ", "3000", ""))
        assertEquals("Maid salary", b.items.last().name)
        assertEquals(BillDrafts.MAX_NAME, ok(start.add("x".repeat(80), "1", "")).items.last().name.length)
    }

    @Test fun `only bills that are on become answers, in paise with their due day`() {
        val b = ok(ok(start.edit(id("Rent"), "Flat rent", "12000", "5")).add("Wi-Fi", "599", ""))
        assertEquals(listOf(BillAnswer("Flat rent", rupees(12_000), 5), BillAnswer("Wi-Fi", rupees(599), null)), b.answers())
    }

    // Attack: endless "Add bill" taps. Setup is for the few big ones; the rest go in Plan later.
    @Test fun `setup takes a sensible number of bills`() {
        var b = start
        repeat(100) { i -> (b.add("Bill $i", "10", "") as? BillDrafts.Change.Ok)?.let { b = it.bills } }
        assertEquals(BillDrafts.MAX_BILLS, b.items.size)
        assertEquals("That's plenty for now. Add more later in Plan → Bills.", error(b.add("One more", "10", "")))
    }
}
