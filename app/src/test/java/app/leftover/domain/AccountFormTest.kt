package app.leftover.domain

import app.leftover.data.db.AccountEntity
import app.leftover.data.db.AccountType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Adding and editing accounts: names, balances, card days, and when an account can be hidden. */
class AccountFormTest {
    private val hdfc = AccountEntity(id = 1, name = "HDFC", type = AccountType.BANK, openingBalance = rupees(10_000), includeInLeft = true)
    private val card = AccountEntity(id = 2, name = "Amex", type = AccountType.CARD, openingBalance = 0, includeInLeft = true, dueDay = 5, statementDay = 20)
    private val all = listOf(hdfc, card)
    private fun ok(r: AccountForm.Result) = (r as AccountForm.Result.Ok).account
    private fun error(r: AccountForm.Result) = (r as AccountForm.Result.Invalid).message

    @Test fun `a new account starts at the balance typed`() {
        val a = ok(AccountForm.save(null, AccountType.CASH, 0, "Wallet", "500", include = true, dueDay = "", statementDay = "", others = all))
        assertEquals("Wallet", a.name)
        assertEquals(rupees(500), a.openingBalance)
        assertEquals(0L, a.id)
    }

    @Test fun `a new account with no name gets one for its type`() {
        assertEquals("Cash", ok(AccountForm.save(null, AccountType.CASH, 0, " ", "", true, "", "", all)).name)
    }

    @Test fun `setting today's balance keeps every entry and moves the starting point`() {
        // HDFC started at ₹10,000 and entries brought it to ₹7,450. Today it really holds ₹8,000.
        val a = ok(AccountForm.save(hdfc, AccountType.BANK, currentBalance = rupees(7_450), name = "HDFC Salary", amount = "8000", include = true, dueDay = "", statementDay = "", others = all))
        assertEquals("HDFC Salary", a.name)
        assertEquals(rupees(10_550), a.openingBalance)
        assertEquals(1L, a.id)
    }

    @Test fun `a card's amount is what you owe`() {
        // Purchases put the card at −₹3,000. You actually owe ₹3,200.
        val a = ok(AccountForm.save(card, AccountType.CARD, -rupees(3_000), "Amex", "3200", true, "7", "22", all))
        assertEquals(-rupees(200), a.openingBalance)
        assertEquals(7, a.dueDay)
        assertEquals(22, a.statementDay)
    }

    @Test fun `edits that don't make sense are refused`() {
        assertEquals("Give it a name", error(AccountForm.save(hdfc, AccountType.BANK, 0, "  ", "1", true, "", "", all)))
        assertEquals("You already have an account called Amex", error(AccountForm.save(hdfc, AccountType.BANK, 0, "amex", "1", true, "", "", all)))
        assertEquals("Up to ₹99,99,99,999", error(AccountForm.save(null, AccountType.BANK, 0, "Big", "1000000000", true, "", "", all)))
        assertEquals("Pick a day from 1 to 31", error(AccountForm.save(card, AccountType.CARD, 0, "Amex", "0", true, "32", "", all)))
        // Its own name, any case, is fine.
        ok(AccountForm.save(hdfc, AccountType.BANK, 0, "hdfc", "1", true, "", "", all))
        // A hidden account's name can be reused.
        ok(AccountForm.save(null, AccountType.BANK, 0, "Old", "1", true, "", "", all + hdfc.copy(id = 9, name = "Old", archived = true)))
    }

    @Test fun `only an empty account that isn't your last spending account can be hidden`() {
        assertEquals("Bring its balance to ₹0 first by moving the money to another account.", AccountForm.whyNotHide(hdfc, rupees(100), all + hdfc.copy(id = 3, name = "Cash", type = AccountType.CASH)))
        assertEquals("This is your only spending account.", AccountForm.whyNotHide(hdfc, 0, all))
        assertNull(AccountForm.whyNotHide(card, 0, all))
        assertNull(AccountForm.whyNotHide(hdfc, 0, all + hdfc.copy(id = 3, name = "Cash", type = AccountType.CASH)))
    }
}
