package app.leftover.domain

import app.leftover.data.db.PendingSmsEntity
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxSource
import app.leftover.data.db.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class UpiNotificationTest {
    @Test fun `paid to a merchant`() {
        val p = UpiNotificationParser.parse("Paid ₹250 to Blue Tokai Coffee")!!
        assertEquals(SmsKind.DEBIT, p.kind); assertEquals(25_000L, p.amount); assertEquals("Blue Tokai Coffee", p.merchant)
        assertEquals(Confidence.HIGH, p.confidence)
    }
    @Test fun `you paid with trailing words`() {
        val p = UpiNotificationParser.parse("You paid ₹1,299.50 to Swiggy via UPI. Ref 123456")!!
        assertEquals(129_950L, p.amount); assertEquals("Swiggy", p.merchant)
    }
    @Test fun `payment successful wording`() {
        val p = UpiNotificationParser.parse("Payment of ₹60 to Raju Chai was successful")!!
        assertEquals(SmsKind.DEBIT, p.kind); assertEquals(6_000L, p.amount)
    }
    @Test fun `received money is a credit`() {
        val p = UpiNotificationParser.parse("Received ₹500 from Meera Shah")!!
        assertEquals(SmsKind.CREDIT, p.kind); assertEquals(50_000L, p.amount); assertEquals("Meera Shah", p.merchant)
    }
    @Test fun `friend paid you`() {
        val p = UpiNotificationParser.parse("Rohan paid you ₹1,140")!!
        assertEquals(SmsKind.CREDIT, p.kind); assertEquals("Rohan", p.merchant)
    }
    @Test fun `requests offers and failures are ignored`() {
        assertNull(UpiNotificationParser.parse("Rohan requested ₹500 from you"))
        assertNull(UpiNotificationParser.parse("Payment of ₹200 to Cafe failed"))
        assertNull(UpiNotificationParser.parse("You won ₹50 cashback! Paid ₹200 to Cafe"))
        assertNull(UpiNotificationParser.parse("Your bill is due. Pay ₹349 now"))
        assertNull(UpiNotificationParser.parse("Tap to see your offers"))
    }
    @Test fun `unknown merchant is low confidence`() {
        val p = UpiNotificationParser.parse("Paid ₹100 to 9876543210")!!
        assertEquals(Confidence.LOW, p.confidence)
    }

    private fun parsed(amount: Long, kind: SmsKind = SmsKind.DEBIT) = ParsedSms(kind, amount, "X", null, false, null, false, Confidence.HIGH)

    @Test fun `bank text and app notification for the same payment are one`() {
        val sms = TransactionEntity(type = TxType.EXPENSE, amount = 25_000, accountId = 1, occurredAt = 1_000_000, source = TxSource.SMS)
        assertTrue(CrossSource.isDuplicate(parsed(25_000), 1_000_000 + 60_000, listOf(sms), emptyList()))
        assertFalse(CrossSource.isDuplicate(parsed(25_000), 1_000_000 + 11 * 60_000, listOf(sms), emptyList()))
        assertFalse(CrossSource.isDuplicate(parsed(30_000), 1_000_000, listOf(sms), emptyList()))
    }
    @Test fun `entries added by hand are left to the manual-entry check`() {
        val manual = TransactionEntity(type = TxType.EXPENSE, amount = 25_000, accountId = 1, occurredAt = 1_000_000)
        assertFalse(CrossSource.isDuplicate(parsed(25_000), 1_000_000, listOf(manual), emptyList()))
    }
    @Test fun `waiting item counts`() {
        val waiting = PendingSmsEntity(sender = "APP:PHONEPE", kind = SmsKind.DEBIT, amount = 25_000, merchant = "X", last4 = null, isCard = false, occurredAt = 1_000_000, reason = PendingReason.NEW_SENDER)
        assertTrue(CrossSource.isDuplicate(parsed(25_000), 1_000_500, emptyList(), listOf(waiting)))
        assertNotNull(waiting)
    }
}
