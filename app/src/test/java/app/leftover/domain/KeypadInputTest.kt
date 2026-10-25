package app.leftover.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class KeypadInputTest {
    @Test fun typesDigitsAndDeletes() {
        var s = ""
        s = KeypadInput.press(s, "1")
        s = KeypadInput.press(s, "8")
        s = KeypadInput.press(s, "0")
        assertEquals("180", s)
        assertEquals("18", KeypadInput.press(s, KeypadInput.DELETE))
        assertEquals("", KeypadInput.press("", KeypadInput.DELETE))
    }

    @Test fun leadingZeroIsReplaced() {
        assertEquals("5", KeypadInput.press("0", "5"))
    }

    @Test fun doubleZeroNeedsADigitFirst() {
        assertEquals("", KeypadInput.press("", KeypadInput.DOUBLE_ZERO))
        assertEquals("500", KeypadInput.press("5", KeypadInput.DOUBLE_ZERO))
    }

    @Test fun lengthIsCapped() {
        assertEquals("1234567", KeypadInput.press("1234567", "8"))
        assertEquals("1234500", KeypadInput.press("12345", KeypadInput.DOUBLE_ZERO))
    }

    @Test fun displaysAndConverts() {
        assertEquals("0", KeypadInput.display(""))
        assertEquals("1,25,000", KeypadInput.display("125000"))
        assertEquals(rupees(180), KeypadInput.toPaise("180"))
    }
}
