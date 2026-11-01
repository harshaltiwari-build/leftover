package app.leftover.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.leftover.domain.rupees
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Rename an account and set its real balance, add another, then hide it once it's empty. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AccountsJourneyTest : JourneyBase() {
    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasContentDescription(label))

    @Test fun accountsCanBeAddedEditedAndHidden() {
        launchHome()
        navigate { compose.onNodeWithContentDescription("Your profile").performClick() }
        navigate { compose.onNodeWithContentDescription("Settings").performClick() }
        navigate { compose.onNodeWithText("Accounts and cards").performScrollTo().performClick() }
        waitForText("Main account")

        navigate { compose.onNodeWithText("Main account").performClick() }
        waitForText("Edit account")
        field("Name").performTextReplacement("HDFC")
        field("Current balance").performTextReplacement("15000")
        navigate { compose.onNodeWithText("Save changes").performClick() }
        waitForText("HDFC")

        navigate { compose.onNodeWithContentDescription("Add account").performClick() }
        waitForText("Add an account")
        field("Name").performTextInput("Old savings")
        navigate { compose.onNodeWithText("Add account").performClick() }
        waitForText("Old savings")

        navigate { compose.onNodeWithText("Old savings").performClick() }
        waitForText("Edit account")
        navigate { compose.onNodeWithText("Hide account").performScrollTo().performClick() }
        waitForText("1 hidden account", substring = true)

        val s = runBlocking { app.repository.snapshot.first() }
        val hdfc = s.accounts.single { it.name == "HDFC" }
        assertEquals(rupees(15_000), s.ledger.balance(hdfc.id))
        assertTrue(s.hiddenAccounts.single { it.name == "Old savings" }.archived)
        assertTrue(s.accounts.none { it.name == "Old savings" })
        compose.onNode(hasText("Old savings")).assertDoesNotExist()
    }
}
