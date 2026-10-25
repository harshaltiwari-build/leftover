package app.leftover.widget

import app.leftover.data.db.AccountEntity
import app.leftover.data.db.AccountType
import app.leftover.data.db.CategoryEntity
import app.leftover.data.db.CategoryKind
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxType
import app.leftover.domain.Ledger
import app.leftover.domain.PayCycle
import app.leftover.domain.rupees
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WidgetModelTest {
    private val today = LocalDate.of(2026, 10, 25)
    private val bank = AccountEntity(id = 1, name = "HDFC", type = AccountType.BANK, openingBalance = rupees(10000), includeInLeft = true)
    private val chai = CategoryEntity(id = 1, name = "Coffee", icon = "coffee", color = "sand", kind = CategoryKind.EXPENSE)
    private val transport = CategoryEntity(id = 2, name = "Transport", icon = "transport", color = "sky", kind = CategoryKind.EXPENSE)

    private fun millis(daysAgo: Long) = today.minusDays(daysAgo).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun spend(amount: Long, daysAgo: Long, category: Long, merchant: String = "") =
        TransactionEntity(type = TxType.EXPENSE, amount = rupees(amount), accountId = 1, categoryId = category, merchant = merchant, occurredAt = millis(daysAgo))

    private fun ledger(vararg tx: TransactionEntity) =
        Ledger(listOf(bank), listOf(chai, transport), tx.toList(), emptyList(), emptyList(), PayCycle.forPayday(28, today), today)

    @Test fun repeatedSmallSpendsBecomePresets() {
        val presets = QuickPresets.from(ledger(spend(20, 1, 1, "Chai Point"), spend(20, 3, 1, "Chai Point"), spend(20, 8, 1, "Chai Point"), spend(236, 2, 2), spend(236, 9, 2)))
        assertEquals(listOf("Chai Point" to rupees(20), "Transport" to rupees(236)), presets.map { it.label to it.amount })
    }

    @Test fun aSingleSpendIsNotARepeat() {
        assertTrue(QuickPresets.from(ledger(spend(20, 1, 1), spend(25, 2, 1))).isEmpty())
    }

    @Test fun oldAndLargeSpendsAreIgnored() {
        assertTrue(QuickPresets.from(ledger(spend(20, 120, 1), spend(20, 130, 1))).isEmpty())
        assertTrue(QuickPresets.from(ledger(spend(5000, 1, 1), spend(5000, 2, 1))).isEmpty())
    }

    @Test fun mixedMerchantsFallBackToTheCategoryName() {
        val presets = QuickPresets.from(ledger(spend(20, 1, 1, "A"), spend(20, 2, 1, "B")))
        assertEquals("Coffee", presets.single().label)
    }

    @Test fun presetsPointAtAnAccount() {
        assertEquals(1L, QuickPresets.from(ledger(spend(20, 1, 1), spend(20, 2, 1))).single().accountId)
    }
}
