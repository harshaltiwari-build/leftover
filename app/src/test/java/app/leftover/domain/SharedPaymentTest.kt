package app.leftover.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedPaymentTest {
    @Test fun `reads amount and payee from common shared lines`() {
        assertEquals(SharedPayment(rupees(500), "Rohan Sharma"), SharedPaymentParser.parse("Paid ₹500 to Rohan Sharma"))
        assertEquals(SharedPayment(125_000, "Zepto"), SharedPaymentParser.parse("₹1,250.00 sent to Zepto via UPI"))
        assertEquals(SharedPayment(rupees(349), "Jio Prepaid"), SharedPaymentParser.parse("Payment of Rs. 349 to Jio Prepaid on 25 Oct was successful."))
        assertEquals(SharedPayment(rupees(80), "Chai Point"), SharedPaymentParser.parse("INR 80 paid to Chai Point, UPI ref 6123"))
    }

    @Test fun `missing parts stay empty`() {
        assertEquals(SharedPayment(null, ""), SharedPaymentParser.parse(null))
        assertEquals(SharedPayment(rupees(200), ""), SharedPaymentParser.parse("₹200"))
        assertNull(SharedPaymentParser.parse("Check out this screenshot").amount)
        assertEquals("", SharedPaymentParser.parse("₹50 paid to you").payee)
    }
}
