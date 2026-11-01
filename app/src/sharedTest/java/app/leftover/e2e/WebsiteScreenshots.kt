package app.leftover.e2e

import android.graphics.Bitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.leftover.data.OnboardingAnswers
import app.leftover.data.ThemeMode
import app.leftover.data.db.BillEntity
import app.leftover.data.db.GoalEntity
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxSource
import app.leftover.data.db.TxType
import app.leftover.domain.rupees
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Screenshots for the website, with made-up demo data in the separate "qa" app. Skipped unless run with
 * `-e screenshots true` on a phone; never part of the normal suites. Images land in the qa app's files/shots folder.
 */
@ScreenshotsOnly
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class WebsiteScreenshots : JourneyBase() {
    private val zone = ZoneId.systemDefault()
    private fun at(date: LocalDate, hour: Int) = date.atTime(hour, 10).atZone(zone).toInstant().toEpochMilli()

    private fun seed() = runBlocking {
        val repo = app.repository
        repo.deleteEverything()
        repo.completeOnboarding(OnboardingAnswers(null, null, "Salary", rupees(48_000), 5, rupees(22_000), emptyList()))
        app.settings.setName("Asha")
        app.settings.setSpendingBudget(rupees(15_000))
        val s = repo.snapshot.first()
        val account = s.accounts.first()
        fun cat(name: String) = s.categories.first { it.name == name }.id
        val today = LocalDate.now()
        val spends = listOf(
            Triple("Swiggy", 450L, "Eating out") to 9L, Triple("Zomato", 380L, "Eating out") to 4L,
            Triple("Blue Tokai", 220L, "Coffee") to 6L, Triple("Third Wave Coffee", 260L, "Coffee") to 2L,
            Triple("Blinkit", 455L, "Groceries") to 11L, Triple("Uber", 310L, "Transport") to 5L,
            Triple("Metro", 60L, "Transport") to 3L, Triple("Myntra", 1_499L, "Shopping") to 8L,
            Triple("PVR", 600L, "Fun & subscriptions") to 7L, Triple("Zepto", 384L, "Groceries") to 12L,
            Triple("Swiggy", 520L, "Eating out") to 33L, Triple("Myntra", 2_100L, "Shopping") to 35L,
            Triple("Uber", 280L, "Transport") to 31L, Triple("Blue Tokai", 180L, "Coffee") to 0L, Triple("Uber", 120L, "Transport") to 0L,
        )
        spends.forEachIndexed { i, (s, daysAgo) ->
            repo.addTransaction(
                TransactionEntity(type = TxType.EXPENSE, amount = rupees(s.second), accountId = account.id, categoryId = cat(s.third), merchant = s.first, occurredAt = at(today.minusDays(daysAgo), 9 + i % 10)),
            )
        }
        repo.addTransaction(
            TransactionEntity(
                type = TxType.EXPENSE, amount = rupees(612), accountId = account.id, categoryId = cat("Groceries"), merchant = "Zepto",
                note = "UPI ref 412345678901", source = TxSource.SCAN, occurredAt = at(today.minusDays(1), 18),
            ),
        )
        listOf(Triple("Netflix", 649L, "tv"), Triple("Electricity", 1_280L, "bolt"), Triple("Rent", 9_000L, "home")).forEachIndexed { i, (name, amount, icon) ->
            repo.saveBill(BillEntity(name = name, amount = rupees(amount), dueDay = 3 + i, icon = icon, accountId = account.id))
        }
        repo.addGoal(GoalEntity(name = "Goa trip", target = rupees(30_000), perCycle = rupees(3_000), cover = "beach"))
        // Today's balance in the account, whatever the dates above added up to.
        val now = repo.snapshot.first()
        repo.updateAccount(account.copy(openingBalance = account.openingBalance + (rupees(24_000) - now.ledger.balance(account.id))))
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(900)
        val full = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val res = app.resources
        fun dimen(n: String) = res.getIdentifier(n, "dimen", "android").takeIf { it != 0 }?.let(res::getDimensionPixelSize) ?: 0
        val top = dimen("status_bar_height")
        val bottom = dimen("navigation_bar_height")
        val cropped = Bitmap.createBitmap(full, 0, top, full.width, full.height - top - bottom)
        val dir = File(app.getExternalFilesDir(null), "shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun tour(theme: String) {
        shot("home-$theme")
        // The panel stays open between passes; only open it when it's closed.
        if (compose.onAllNodes(hasText("Day by day until payday →")).fetchSemanticsNodes().isEmpty()) compose.onNodeWithText("How?").performClick()
        navigate { compose.onNodeWithText("Day by day until payday →").performClick() }
        waitForText("Until payday")
        shot("timeline-$theme")
        navigate { compose.onNodeWithContentDescription("Back").performClick() }
        navigate { compose.onNodeWithText("Afford?").performClick() }
        waitForText("Can I afford this?")
        shot("afford-$theme")
        navigate { compose.onNodeWithContentDescription("Back").performClick() }
        navigate { compose.onNodeWithText("Plan").performClick() }
        waitForText("left of ₹15,000", substring = true)
        shot("plan-$theme")
        navigate { compose.onNodeWithText("Insights").performClick() }
        waitForText("Daily average")
        shot("insights-$theme")
        navigate { compose.onNodeWithText("Activity").performClick() }
        waitForText("Search merchant, note or amount")
        shot("activity-$theme")
        navigate { compose.onAllNodesWithText("Zepto").onFirst().performClick() }
        waitForText("Read from your screenshot")
        shot("detail-$theme")
        navigate { compose.onNodeWithContentDescription("Back").performClick() }
        navigate { compose.onNodeWithText("Home").performClick() }
        compose.waitUntilAtLeastOneExists(hasText("Safe to spend today"), 10_000)
    }

    @Test fun captureWebsiteScreens() {
        assumeTrue("Run with -e screenshots true", InstrumentationRegistry.getArguments().getString("screenshots") == "true")
        seed()
        runBlocking { app.settings.setThemeMode(ThemeMode.LIGHT) }
        launchHome()
        tour("light")
        runBlocking { app.settings.setThemeMode(ThemeMode.DARK) }
        compose.waitForIdle()
        Thread.sleep(800)
        tour("dark")
        navigate { compose.onNodeWithContentDescription("Add an expense").performClick() }
        waitForText("Expense")
        shot("add-dark")
    }
}
