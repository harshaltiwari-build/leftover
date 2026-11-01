package app.leftover.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxSource
import app.leftover.data.db.TxType
import app.leftover.domain.rupees
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** An entry saved from a screenshot shows what was read, how often you pay that payee, and similar spends. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class TransactionDetailJourneyTest : JourneyBase() {
    @Test fun detailShowsReadFieldsStatsAndSimilarSpends() {
        seedExpense("Zepto", 384)
        seedExpense("Zepto", 520)
        runBlocking {
            val s = app.repository.snapshot.first()
            app.repository.addTransaction(
                TransactionEntity(
                    type = TxType.EXPENSE, amount = rupees(612), accountId = s.accounts.first().id, categoryId = s.categories.first { it.name == "Eating out" }.id,
                    merchant = "Zepto", note = "UPI ref 412345678901", source = TxSource.SCAN, occurredAt = System.currentTimeMillis() + 1_000,
                ),
            )
        }
        launchHome()
        navigate { compose.onNodeWithText("Activity").performClick() }
        waitForText("Zepto")
        navigate { compose.onAllNodesWithText("Zepto", substring = true).onFirst().performClick() }
        waitForText("Read from your screenshot")
        compose.onNodeWithText("412345678901").assertExists()
        compose.onNodeWithText("You and Zepto").performScrollTo().assertExists()
        compose.onNodeWithText("times this year").assertExists()
        compose.onNodeWithText("₹1,516").assertExists()
        compose.onNodeWithText("Similar spends").performScrollTo().assertExists()
    }
}
