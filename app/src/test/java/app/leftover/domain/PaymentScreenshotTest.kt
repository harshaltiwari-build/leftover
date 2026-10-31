package app.leftover.domain

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Text shaped like what on-device recognition returns for common UPI receipts, misreads included. */
class PaymentScreenshotTest {
    private val today = LocalDate.of(2026, 10, 31)

    @Test fun `google pay paid to a person`() {
        val text = """
            ₹500
            Paid to Rohan Sharma
            rohan.sharma@okaxis
            Completed
            25 Oct 2026, 6:42 pm
            UPI transaction ID
            629812345678
            To: ROHAN SHARMA
            Google Pay
        """.trimIndent()
        val p = PaymentScreenshotParser.parse(text, today)
        assertEquals(rupees(500), p.amount)
        assertEquals("Rohan Sharma", p.payee)
        assertEquals(LocalDate.of(2026, 10, 25), p.date)
        assertEquals(LocalTime.of(18, 42), p.time)
        assertEquals(ScreenshotStatus.SUCCESS, p.status)
        assertEquals("629812345678", p.upiRef)
        assertEquals("Google Pay", p.app)
        assertFalse(p.received)
    }

    @Test fun `phonepe with the name under the label and a misread rupee sign`() {
        val text = """
            Transaction Successful
            06:42 pm on 25 Oct 2026
            Paid to
            Zepto
            %612
            zepto.payu@hdfcbank
            Transaction ID
            T2610251842123456789
            Debited from XXXXXXXX5521
            PhonePe
        """.trimIndent()
        val p = PaymentScreenshotParser.parse(text, today)
        assertEquals(rupees(612), p.amount)
        assertEquals("Zepto", p.payee)
        assertEquals(LocalDate.of(2026, 10, 25), p.date)
        assertEquals("T2610251842123456789", p.upiRef)
        assertEquals("PhonePe", p.app)
    }

    @Test fun `paytm with commas, paise and a bare amount line`() {
        val text = """
            Paid Successfully to
            Blue Tokai Coffee
            1,250.50
            31 Oct 2026, 09:05 AM
            UPI Ref No: 430512345678
            paytm
        """.trimIndent()
        val p = PaymentScreenshotParser.parse(text, today)
        assertEquals(125_050L, p.amount)
        assertEquals("Blue Tokai Coffee", p.payee)
        assertEquals(LocalTime.of(9, 5), p.time)
        assertEquals("430512345678", p.upiRef)
    }

    @Test fun `received money is marked as money in`() {
        val text = "Received from\nMeera Iyer\n₹ 1,200\nYesterday, 8:10 pm\nCompleted"
        val p = PaymentScreenshotParser.parse(text, today)
        assertTrue(p.received)
        assertEquals("Meera Iyer", p.payee)
        assertEquals(rupees(1200), p.amount)
        assertEquals(today.minusDays(1), p.date)
    }

    @Test fun `failed and pending payments are flagged`() {
        assertEquals(ScreenshotStatus.FAILED, PaymentScreenshotParser.parse("Payment failed\n₹349\nPaid to Jio", today).status)
        assertEquals(ScreenshotStatus.PENDING, PaymentScreenshotParser.parse("Payment pending\n₹349\nTo Jio", today).status)
    }

    @Test fun `ids, phone numbers and balances are not taken as the amount`() {
        val text = "UPI transaction ID\n629812345678\nMobile +91 98765 43210\nBalance ₹ 8,000\nPaid to Swiggy\n₹420"
        assertEquals(rupees(420), PaymentScreenshotParser.parse(text, today).amount)
    }

    @Test fun `a payee can come from the upi id when there is no label`() {
        val p = PaymentScreenshotParser.parse("₹80\nchaipoint@ybl\n31/10/2026", today)
        assertEquals("Chaipoint", p.payee)
        assertEquals(today, p.date)
    }

    @Test fun `nothing readable gives nothing`() {
        val p = PaymentScreenshotParser.parse("a photo of a cat", today)
        assertNull(p.amount)
        assertEquals("", p.payee)
        assertFalse(p.foundAnything)
    }

    @Test fun `dates in the future are ignored`() {
        assertNull(PaymentScreenshotParser.date("25 Dec 2026", today))
    }
}
