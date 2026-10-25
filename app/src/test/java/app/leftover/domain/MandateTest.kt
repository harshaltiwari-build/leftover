package app.leftover.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class MandateTest {
    @Test fun aNewMandateIsRecognised() {
        val e = MandateParser.parse("UPI Mandate created for Rs.199.00 for NETFLIX on your A/c XX1234. Frequency: Monthly. If not you, call 1800.")!!
        assertEquals(MandateKind.CREATED, e.kind)
        assertEquals(19900L, e.amount)
        assertEquals("Netflix", e.merchant)
        assertEquals("monthly", e.frequency)
    }

    @Test fun aPreDebitNoticeCarriesTheDate() {
        val e = MandateParser.parse("Rs 649.00 will be debited on 05-Nov-26 from A/c XX1234 for SPOTIFY via UPI AutoPay. Ref 629912345678")!!
        assertEquals(MandateKind.UPCOMING, e.kind)
        assertEquals(64900L, e.amount)
        assertEquals("Spotify", e.merchant)
        assertEquals(LocalDate.of(2026, 11, 5), e.nextDebit)
    }

    @Test fun numericDatesWork() {
        val e = MandateParser.parse("Pre-debit: Rs 299 will be debited on 12/11/2026 towards HOTSTAR using UPI AutoPay")!!
        assertEquals(LocalDate.of(2026, 11, 12), e.nextDebit)
    }

    @Test fun otpsAndOrdinaryDebitsAreNotMandates() {
        assertNull(MandateParser.parse("123456 is your OTP to create mandate of Rs 199 for NETFLIX"))
        assertNull(MandateParser.parse("Sent Rs.180.00 From HDFC Bank A/C *1234 To BLUE TOKAI On 25/10/26 Ref 629912345678"))
        assertNull(MandateParser.parse("Your mandate was created"))
    }

    @Test fun aFutureDebitIsNeverReadAsATransaction() {
        assertNull(SmsParser.parse("Rs 649.00 will be debited on 05-Nov-26 from A/c XX1234 for SPOTIFY via UPI AutoPay"))
    }

    @Test fun captureLinksAreStrict() {
        val ok = CaptureLink.parse("180", "Blue Tokai", "chai", "expense")!!
        assertEquals(SmsKind.DEBIT, ok.kind)
        assertEquals(18000L, ok.amount)
        assertEquals(SmsKind.CREDIT, CaptureLink.parse("1,250.50", null, null, "income")!!.kind)
        assertNull(CaptureLink.parse("0", "x", "", "expense"))
        assertNull(CaptureLink.parse("abc", "x", "", "expense"))
        assertNull(CaptureLink.parse("99999999999", "x", "", "expense"))
        assertNull(CaptureLink.parse("100", "x", "", "transfer"))
        assertEquals(40, CaptureLink.parse("100", "x".repeat(500), "", null)!!.merchant.length)
    }
}
