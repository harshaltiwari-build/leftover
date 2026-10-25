package app.leftover.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinksTest {
    @Test fun opensQuickAddWithTheRequestedKind() {
        assertEquals("add?kind=Income", DeepLinks.toRoute("leftover", "add", "Income"))
        assertEquals("add?kind=Expense", DeepLinks.toRoute("leftover", "add", null))
    }

    @Test fun unknownKindsFallBackToExpense() {
        assertEquals("add?kind=Expense", DeepLinks.toRoute("leftover", "add", "../../etc"))
    }

    @Test fun opensVoiceAndHome() {
        assertEquals(Routes.VOICE, DeepLinks.toRoute("leftover", "voice", null))
        assertEquals(Routes.HOME, DeepLinks.toRoute("leftover", "home", null))
    }

    @Test fun ignoresOtherSchemesAndHosts() {
        assertNull(DeepLinks.toRoute("https", "add", null))
        assertNull(DeepLinks.toRoute("leftover", "settings", null))
        assertNull(DeepLinks.toRoute(null, null, null))
    }
}
