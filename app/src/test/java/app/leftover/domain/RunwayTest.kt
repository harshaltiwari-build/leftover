package app.leftover.domain

import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RunwayTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 25)
    private val payday = LocalDate.of(2026, 10, 28)

    private fun millis(date: LocalDate) = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private fun spend(daysAgo: Int, rupees: Long, merchant: String = "", split: Long = 0, type: TxType = TxType.EXPENSE) =
        TransactionEntity(type = type, amount = rupees(rupees), accountId = 1, merchant = merchant, occurredAt = millis(today.minusDays(daysAgo.toLong())), splitOthers = rupees(split))

    private fun project(left: Long, txs: List<TransactionEntity>, bills: Set<String> = emptySet(), payday: LocalDate = this.payday) =
        RunwayCalc.project(rupees(left), txs, bills, today, payday, zone)

    @Test fun noHistoryGivesNoProjection() {
        assertEquals(RunwayStatus.NOT_ENOUGH_DATA, project(5000, emptyList()).status)
        assertEquals(RunwayStatus.NOT_ENOUGH_DATA, project(5000, listOf(spend(0, 100))).status)
    }

    @Test fun moneyThatLastsReachesPayday() {
        val txs = (0..6).map { spend(it, 100) }
        val r = project(5000, txs)
        assertEquals(RunwayStatus.REACHES_PAYDAY, r.status)
        assertEquals(rupees(100), r.pacePerDay)
    }

    @Test fun fasterSpendingRunsOutOnAKnownDay() {
        val txs = (0..6).map { spend(it, 700) }
        val r = project(2000, txs, payday = LocalDate.of(2026, 11, 5))
        assertEquals(RunwayStatus.RUNS_OUT, r.status)
        assertEquals(LocalDate.of(2026, 10, 27), r.runsOutOn)
    }

    @Test fun nothingLeftMeansToday() {
        val txs = (0..6).map { spend(it, 100) }
        assertEquals(today, project(0, txs).runsOutOn)
    }

    @Test fun billsMarkedPaidDoNotInflateThePace() {
        val txs = (0..6).map { spend(it, 100) } + spend(1, 9000, merchant = "Rent")
        val r = project(5000, txs, bills = setOf("rent"))
        assertEquals(rupees(100), r.pacePerDay)
    }

    @Test fun onlyYourShareOfASplitCountsAndRefundsReduceIt() {
        val txs = (0..6).map { spend(it, 100) } + spend(0, 1000, split = 800) + spend(0, 100, type = TxType.REFUND)
        // 700 + 200 − 100 = 800 over 7 days
        assertEquals(rupees(800) / 7, project(5000, txs).pacePerDay)
    }

    @Test fun aNewUserIsAveragedOverTheDaysTheyHave() {
        val txs = listOf(spend(3, 300), spend(2, 300), spend(1, 300), spend(0, 300))
        assertEquals(rupees(300), project(5000, txs).pacePerDay)
    }

    @Test fun goalImpactTurnsAPurchaseIntoDaysOfSaving() {
        assertEquals(10, GoalImpact.daysCloser(rupees(1000), rupees(3000), 30))
        assertNull(GoalImpact.daysCloser(rupees(10), rupees(30000), 30))
        assertNull(GoalImpact.daysCloser(rupees(1000), 0, 30))
    }
}
