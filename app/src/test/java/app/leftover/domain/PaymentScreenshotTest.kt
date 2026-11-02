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

    // The layout of a real Google Pay receipt, with made-up people and numbers: avatar letter, masked phone, a "Pay again"
    // button, a timeline that repeats the amount and time, and the sender's own name and bank under "From".
    @Test fun `a google pay receipt with a timeline`() {
        val text = """
            R
            To Rohan Mehta
            +91 ••••• •1234
            ₹1,000
            Pay again
            Completed
            31 Oct 2026, 10:32 pm
            Example Small Finance Bank 1111
            Payment started
            31 Oct 2026, 10:32 pm
            ₹1,000 was debited
            10:32 pm
            ₹1,000 sent to Rohan Mehta
            10:32 pm
            Payment completed
            10:32 pm
            UPI transaction ID
            123456789012
            To: MEHTA ROHAN S
            ••••11-1@okexample on Google Pay
            From: ASHA SHARMA (Example Small Finance Bank)
            ••••2-22@okexample on Google Pay
            Google transaction ID
            ABCdEfGhIjKlMn
            POWERED BY
            UPI
            Having issues?
            Share
            Google Pay
            Split expense
        """.trimIndent()
        val p = PaymentScreenshotParser.parse(text, LocalDate.of(2026, 11, 2))
        assertEquals(rupees(1_000), p.amount)
        assertEquals("Rohan Mehta", p.payee)
        assertEquals(LocalDate.of(2026, 10, 31), p.date)
        assertEquals(LocalTime.of(22, 32), p.time)
        assertEquals(ScreenshotStatus.SUCCESS, p.status)
        assertEquals("123456789012", p.upiRef)
        assertEquals("Google Pay", p.app)
        assertFalse(p.received)
    }

    // The same layout when recognition garbles the rupee sign, a common failure on this screen.
    @Test fun `the same layout with a misread rupee sign`() {
        val text = "R\nTo Rohan Mehta\n+91 ••••• •1234\n21,000\nPay again\nCompleted\n31 Oct 2026, 10:32 pm\nUPI transaction ID\n123456789012\nGoogle Pay"
        val p = PaymentScreenshotParser.parse(text, LocalDate.of(2026, 11, 2))
        assertEquals("Rohan Mehta", p.payee)
        assertEquals(ScreenshotStatus.SUCCESS, p.status)
        assertEquals("123456789012", p.upiRef)
    }

    // Found on a real phone: recognition reads the big ₹ as "*" ("*1,000"), and in the timeline as "3" ("31,000 was debited").
    // The masked phone number's last digits ("+91" then "4091") must never be taken for the amount.
    @Test fun `a star where the rupee sign should be, with phone digits on their own line`() {
        val text = """
            \v

            To Rohan Mehta

            +91

            1234

            *1,000

            @ Completed

            31 Oct 2026, 10:32pm

            Example Small Finance Bank 1111

            Payment started

            31,000 was debited

            10:32pm

            31,000 sent to Rohan Mehta

            UPI transaction ID

            123456789012

            To: MEHTA ROHAN S

            ++++11-1@okexample on Google Pay

            From: ASHA SHARMA (Example Small Finance Bank)

            Google transaction ID

            ABCdEfGhIjKlMn
        """.trimIndent()
        val p = PaymentScreenshotParser.parse(text, LocalDate.of(2026, 11, 2))
        assertEquals(rupees(1_000), p.amount)
        assertEquals("Rohan Mehta", p.payee)
        assertEquals("123456789012", p.upiRef)
        assertEquals(ScreenshotStatus.SUCCESS, p.status)
    }

    @Test fun `a star amount with no other amount lines`() {
        val p = PaymentScreenshotParser.parse("A\n\nTo Example Traders\n\n*1,800\n\n@ Completed\n\n30 Oct 2026, 9:26am\n\nUPI transaction ID\n\n111122223333", LocalDate.of(2026, 11, 2))
        assertEquals(rupees(1_800), p.amount)
        assertEquals("Example Traders", p.payee)
    }

    @Test fun `phone digits alone are not an amount`() {
        assertNull(PaymentScreenshotParser.parse("To Rohan Mehta\n+91\n4091\nCompleted\n31 Oct 2026, 10:32pm", LocalDate.of(2026, 11, 2)).amount)
        assertNull(PaymentScreenshotParser.parse("To Rohan Mehta\n+91 ••••• •4091\n4091\nCompleted", LocalDate.of(2026, 11, 2)).amount)
    }

    @Test fun `junk marks only count in front of a whole amount line`() {
        // Masked ids and card numbers are full of stars; they aren't amounts.
        assertNull(PaymentScreenshotParser.parse("To Rohan\n****1234\n++++51-1@okexample\nCompleted", LocalDate.of(2026, 11, 2)).amount)
        assertEquals(rupees(250), PaymentScreenshotParser.parse("To Rohan\n@ 250\nCompleted", LocalDate.of(2026, 11, 2)).amount)
    }

    @Test fun `google pay is named from its own transaction id even when the payee uses another app`() {
        val p = PaymentScreenshotParser.parse("To Example Traders\n*1,800\nCompleted\nUPI transaction ID\n111122223333\nTo: EXAMPLE TRADERS\n++*11-2@ybl on PhonePe\nFrom: ASHA SHARMA\n++++7-14@okaxis on Google Pay\nGoogle transaction ID\nABCdEfGhIj", LocalDate.of(2026, 11, 2))
        assertEquals("Google Pay", p.app)
    }

    // The ₹ misread as "3" on a line that carries it as a marker: the marked big amount wins over the timeline's unmarked ones.
    @Test fun `a marked amount beats unmarked timeline lines`() {
        val p = PaymentScreenshotParser.parse("To Example Traders\n*1,000\nCompleted\n31,000 was debited\n31,000 sent to Example Traders", LocalDate.of(2026, 11, 2))
        assertEquals(rupees(1_000), p.amount)
    }
}
