package app.leftover.domain

import app.leftover.data.db.CardPerkEntity
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxType
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardRewardsTest {
    private fun at(d: LocalDate) = d.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun tx(d: LocalDate, rupees: Long, type: TxType = TxType.EXPENSE, account: Long = 7, merchant: String = "Shop") =
        TransactionEntity(type = type, amount = rupees(rupees), accountId = account, merchant = merchant, occurredAt = at(d))

    @Test fun `year start is the latest anniversary`() {
        assertEquals(LocalDate.of(2026, 3, 15), CardRewards.yearStart(15, 3, LocalDate.of(2026, 10, 25)))
        assertEquals(LocalDate.of(2025, 12, 1), CardRewards.yearStart(1, 12, LocalDate.of(2026, 10, 25)))
        assertEquals(LocalDate.of(2025, 2, 28), CardRewards.yearStart(29, 2, LocalDate.of(2026, 2, 10)))
    }
    @Test fun `waiver counts card purchases in the year less refunds`() {
        val start = LocalDate.of(2026, 3, 15)
        val perk = CardPerkEntity(7, annualFee = rupees(500), waiverSpend = rupees(100_000), yearStartEpochDay = start.toEpochDay())
        val txs = listOf(tx(start.plusDays(10), 40_000), tx(start.plusDays(20), 10_000), tx(start.plusDays(21), 2_000, TxType.REFUND), tx(start.plusDays(5), 99_000, account = 8), tx(start.minusDays(1), 50_000))
        val w = CardRewards.waiver(perk, 7, txs, LocalDate.of(2026, 10, 25))!!
        assertEquals(rupees(48_000), w.spent); assertEquals(rupees(52_000), w.remaining); assertFalse(w.met)
        assertEquals(LocalDate.of(2027, 3, 14), w.windowEnd)
        assertTrue(w.perDayNeeded * w.daysLeft >= w.remaining)
    }
    @Test fun `met waiver needs nothing more`() {
        val start = LocalDate.of(2026, 3, 15)
        val perk = CardPerkEntity(7, waiverSpend = rupees(1_000), yearStartEpochDay = start.toEpochDay())
        val w = CardRewards.waiver(perk, 7, listOf(tx(start.plusDays(3), 1_500)), LocalDate.of(2026, 6, 1))!!
        assertTrue(w.met); assertEquals(0L, w.perDayNeeded)
    }
    @Test fun `no waiver without a target`() = assertNull(CardRewards.waiver(CardPerkEntity(7), 7, emptyList(), LocalDate.of(2026, 6, 1)))
    @Test fun `points lapse warning`() {
        val today = LocalDate.of(2026, 10, 25)
        assertEquals(20, CardRewards.pointsLapseIn(CardPerkEntity(7, points = 5_000, pointsExpiryEpochDay = today.plusDays(20).toEpochDay()), today))
        assertNull(CardRewards.pointsLapseIn(CardPerkEntity(7, points = 5_000, pointsExpiryEpochDay = today.plusDays(200).toEpochDay()), today))
        assertNull(CardRewards.pointsLapseIn(CardPerkEntity(7, points = 0, pointsExpiryEpochDay = today.plusDays(5).toEpochDay()), today))
        assertNull(CardRewards.pointsLapseIn(CardPerkEntity(7, points = 100, pointsExpiryEpochDay = today.minusDays(1).toEpochDay()), today))
    }
    @Test fun `limit used`() {
        assertEquals(0.05f, CardRewards.limitUsed(CardPerkEntity(7, creditLimit = rupees(50_000)), rupees(2_500))!!, 0.0001f)
        assertNull(CardRewards.limitUsed(CardPerkEntity(7), rupees(100)))
    }
    @Test fun `fee wording is recognised`() {
        assertEquals("Annual fee", FeeDetector.detect("Annual Fee of Rs.500 debited"))
        assertEquals("Late payment fee", FeeDetector.detect("Late payment charges applied"))
        assertEquals("Interest charged", FeeDetector.detect("Finance charges for the month"))
        assertEquals("Tax on charges", FeeDetector.detect("GST on annual..."))
        assertNull(FeeDetector.detect("Swiggy order"))
        assertNull(FeeDetector.detect("Interest free period ends"))
    }
    @Test fun `fees in a cycle`() {
        val cycle = PayCycle.forPayday(1, LocalDate.of(2026, 10, 25))
        val (fees, total) = FeeDetector.feesIn(listOf(tx(LocalDate.of(2026, 10, 3), 500, merchant = "Annual fee"), tx(LocalDate.of(2026, 10, 4), 200, merchant = "Cafe"), tx(LocalDate.of(2026, 9, 3), 90, merchant = "Late fee")), cycle)
        assertEquals(1, fees.size); assertEquals(rupees(500), total)
    }
}
