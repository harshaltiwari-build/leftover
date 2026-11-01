package app.leftover.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onFirst
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
import org.junit.Test
import org.junit.runner.RunWith

/** First run: edit a suggested bill, remove one, add your own, and the app starts with exactly those. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class OnboardingBillsJourneyTest : JourneyBase() {
    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasContentDescription(label))

    @Test fun billsCanBeEditedRemovedAndAddedDuringSetup() {
        runBlocking { app.repository.deleteEverything() }
        launchApp()
        waitForText("Get started")
        navigate { compose.onNodeWithText("Get started").performClick() }
        waitForText("What did you last spend on?")
        navigate { compose.onNodeWithText("Skip").performClick() }
        waitForText("What's your money rhythm?")
        compose.onNodeWithText("1st").performScrollTo().performClick()
        navigate { compose.onNodeWithText("Continue").performClick() }
        waitForText("Two quick numbers")
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("10000")

        // Edit a suggestion.
        compose.onNodeWithText("Rent ₹9,000").performScrollTo().performClick()
        waitForText("Edit Rent")
        compose.onNodeWithText("Edit Rent").performScrollTo().performClick()
        field("Name").performTextReplacement("Flat rent")
        field("Amount").performTextReplacement("12000")
        field("Due on").performTextReplacement("5")
        compose.onNodeWithText("Save bill").performScrollTo().performClick()
        compose.onNodeWithText("Flat rent ₹12,000").assertExists()

        // Switch one on, then remove it.
        compose.onNodeWithText("Spotify ₹119").performScrollTo().performClick()
        waitForText("Edit Spotify")
        compose.onNodeWithText("Edit Spotify").performScrollTo().performClick()
        compose.onNodeWithText("Remove bill").performScrollTo().performClick()
        compose.onAllNodesWithText("Spotify", substring = true).assertCountEquals(0)

        // Add your own.
        compose.onNodeWithText("Other").performScrollTo().performClick()
        field("Name").performTextInput("Wi-Fi")
        field("Amount").performTextInput("599")
        field("Due on").performTextInput("10")
        compose.onNodeWithText("Add bill").performScrollTo().performClick()

        compose.onNodeWithText("₹10,000 − ₹12,599 in bills").assertExists()
        navigate { compose.onNodeWithText("Looks right").performClick() }
        waitForText("Go to home")
        navigate { compose.onNodeWithText("Go to home").performClick() }
        compose.waitUntilAtLeastOneExists(hasContentDescription("Your profile"), 20_000)

        val bills = runBlocking { app.repository.snapshot.first().bills }.associateBy { it.name }
        assertEquals(setOf("Flat rent", "Wi-Fi"), bills.keys)
        assertEquals(rupees(12_000), bills.getValue("Flat rent").amount)
        assertEquals(5, bills.getValue("Flat rent").dueDay)
        assertEquals(10, bills.getValue("Wi-Fi").dueDay)
    }
}
