package app.leftover.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.leftover.data.Accent
import app.leftover.data.AppFont
import app.leftover.data.LeftoverRepository
import app.leftover.data.OnboardingAnswers
import app.leftover.data.SettingsRepository
import app.leftover.data.db.LeftoverDatabase
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxFeeling
import app.leftover.data.db.TxType
import app.leftover.domain.BillAnswer
import app.leftover.domain.SafeToday
import app.leftover.domain.TagTotals
import app.leftover.domain.rupees
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The database, repository and settings working together, as the app uses them. */
@RunWith(AndroidJUnit4::class)
class RepositoryIntegrationTest {
    private lateinit var db: LeftoverDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var repo: LeftoverRepository

    @Before fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LeftoverDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsRepository(context)
        settings.clear()
        repo = LeftoverRepository(db, settings)
        repo.completeOnboarding(OnboardingAnswers(null, null, "Salary", rupees(45_000), 1, rupees(10_000), emptyList()))
    }

    @After fun tearDown() = runBlocking {
        settings.clear()
        db.close()
    }

    private suspend fun account() = repo.snapshot.first().accounts.first().id
    private suspend fun food() = repo.snapshot.first().categories.first { it.name == "Eating out" }.id
    private fun now() = System.currentTimeMillis()

    @Test fun `onboarding creates an account that sets left to spend`() = runBlocking {
        val s = repo.snapshot.first()
        assertTrue(s.settings.onboarded)
        assertEquals(rupees(10_000), s.ledger.leftToSpend.left)
    }

    @Test fun `an expense lowers left to spend and counts against today`() = runBlocking {
        repo.addTransaction(TransactionEntity(type = TxType.EXPENSE, amount = rupees(230), accountId = account(), categoryId = food(), occurredAt = now()))
        val s = repo.snapshot.first()
        assertEquals(rupees(9_770), s.ledger.leftToSpend.left)
        val safe = SafeToday.of(s.ledger)
        assertEquals(rupees(230), safe.spentToday)
        assertEquals(safe.target - rupees(230), safe.leftToday)
    }

    @Test fun `a deleted entry comes back whole on undo, tags included`() = runBlocking {
        val tag = repo.addTag("goa", "lavender", null)
        val id = repo.addTransaction(TransactionEntity(type = TxType.EXPENSE, amount = rupees(500), accountId = account(), categoryId = food(), merchant = "Swiggy", occurredAt = now()))
        repo.setTransactionTags(id, setOf(tag))
        val original = repo.snapshot.first().transactions.single { it.id == id }

        repo.deleteTransaction(id)
        assertTrue(repo.snapshot.first().transactions.none { it.id == id })
        assertTrue(TagTotals.tagsOf(id, repo.snapshot.first().txTags).isEmpty())

        repo.addTransaction(original)
        repo.setTransactionTags(id, setOf(tag))
        val back = repo.snapshot.first()
        assertEquals(original, back.transactions.single { it.id == id })
        assertEquals(setOf(tag), TagTotals.tagsOf(id, back.txTags))
        assertEquals(rupees(9_500), back.ledger.leftToSpend.left)
    }

    @Test fun `feeling and receipt are stored with the entry`() = runBlocking {
        val id = repo.addTransaction(TransactionEntity(type = TxType.EXPENSE, amount = rupees(612), accountId = account(), categoryId = food(), occurredAt = now()))
        val tx = repo.snapshot.first().transactions.single { it.id == id }
        repo.updateTransaction(tx.copy(feeling = TxFeeling.REGRET, receiptFile = "receipt-1.jpg"))
        val saved = repo.snapshot.first().transactions.single { it.id == id }
        assertEquals(TxFeeling.REGRET, saved.feeling)
        assertEquals("receipt-1.jpg", saved.receiptFile)
    }

    @Test fun `bills from setup keep their own due day and get a fitting icon`() = runBlocking {
        repo.deleteEverything()
        repo.completeOnboarding(
            OnboardingAnswers(null, null, "Salary", rupees(45_000), 10, rupees(20_000),
                listOf(BillAnswer("Flat rent", rupees(12_000), 5), BillAnswer("Wi-Fi", rupees(599), null), BillAnswer("Odd", rupees(1), 31))),
        )
        val bills = repo.snapshot.first().bills.associateBy { it.name }
        assertEquals(5, bills.getValue("Flat rent").dueDay)
        assertEquals(rupees(12_000), bills.getValue("Flat rent").amount)
        assertEquals("home", bills.getValue("Flat rent").icon)
        // No day given: the day before payday.
        assertEquals(9, bills.getValue("Wi-Fi").dueDay)
        assertEquals("wifi", bills.getValue("Wi-Fi").icon)
        // The 31st still has a next due date in short months.
        val s = repo.snapshot.first()
        assertTrue(s.ledger.billNextDue(bills.getValue("Odd")) >= s.today)
    }

    @Test fun `editing an account's balance keeps its entries and updates left to spend`() = runBlocking {
        repo.addTransaction(TransactionEntity(type = TxType.EXPENSE, amount = rupees(2_550), accountId = account(), categoryId = food(), occurredAt = now()))
        val s = repo.snapshot.first()
        val main = s.accounts.single()
        val edited = (app.leftover.domain.AccountForm.save(main, main.type, s.ledger.balance(main.id), "HDFC", "8000", true, "", "", s.accounts) as app.leftover.domain.AccountForm.Result.Ok).account
        repo.updateAccount(edited)
        val after = repo.snapshot.first()
        assertEquals("HDFC", after.accounts.single().name)
        assertEquals(rupees(8_000), after.ledger.balance(main.id))
        assertEquals(rupees(8_000), after.ledger.leftToSpend.left)
        assertEquals(1, after.transactions.size)
    }

    // Attack: a hidden account must not quietly receive new money, and its old entries keep its name.
    @Test fun `hiding an account unhooks bills from it and keeps its name on old entries`() = runBlocking {
        val oldId = repo.addAccount(app.leftover.data.db.AccountEntity(name = "Old savings", type = app.leftover.data.db.AccountType.BANK, openingBalance = 0, includeInLeft = true))
        repo.addTransaction(TransactionEntity(type = TxType.INCOME, amount = rupees(100), accountId = oldId, categoryId = food(), occurredAt = now()))
        repo.addTransaction(TransactionEntity(type = TxType.EXPENSE, amount = rupees(100), accountId = oldId, categoryId = food(), occurredAt = now()))
        db.bills().insertAll(listOf(app.leftover.data.db.BillEntity(name = "Wi-Fi", amount = rupees(599), dueDay = 10, icon = "wifi", accountId = oldId)))
        val old = repo.snapshot.first().accounts.single { it.id == oldId }
        repo.setAccountHidden(old, true)
        val s = repo.snapshot.first()
        assertTrue(s.accounts.none { it.id == oldId })
        assertEquals(null, s.bills.single { it.name == "Wi-Fi" }.accountId)
        assertEquals("Old savings", s.ledger.accountName(oldId))
        // The Wi-Fi bill still counts; the hidden account's empty balance changes nothing.
        assertEquals(rupees(10_000 - 599), s.ledger.leftToSpend.left)
        repo.setAccountHidden(s.hiddenAccounts.single(), false)
        assertTrue(repo.snapshot.first().accounts.any { it.id == oldId })
    }

    @Test fun `delete everything returns to first run`() = runBlocking {
        repo.addTransaction(TransactionEntity(type = TxType.EXPENSE, amount = rupees(100), accountId = account(), categoryId = food(), occurredAt = now()))
        repo.deleteEverything()
        val s = repo.snapshot.first()
        assertTrue(s.transactions.isEmpty())
        assertTrue(s.accounts.isEmpty())
        assertTrue(!s.settings.onboarded)
        assertTrue(s.categories.isNotEmpty())
    }

    @Test fun `a monthly spending budget is saved and drives the budget numbers`() = runBlocking {
        val cat = repo.snapshot.first().categories.first { it.name == "Eating out" }
        repo.addTransaction(TransactionEntity(type = TxType.EXPENSE, amount = rupees(1_500), accountId = account(), categoryId = cat.id, occurredAt = now()))
        settings.setSpendingBudget(rupees(12_000))
        val s = repo.snapshot.first()
        assertEquals(rupees(12_000), s.settings.spendingBudget)
        val b = app.leftover.domain.MonthlyBudget.of(s.ledger, s.settings.spendingBudget)!!
        assertEquals(rupees(10_500), b.left)
        settings.setSpendingBudget(0)
        assertNull(app.leftover.domain.MonthlyBudget.of(repo.snapshot.first().ledger, 0))
    }

    // Attack: many writers at once (double taps, a widget, a restore) must leave one of the values written, never junk.
    @Test fun `concurrent budget writes settle on a value that was written`() = runBlocking {
        val values = (1..50).map { rupees(it * 1_000L) }
        coroutineScope { values.forEach { v -> launch(Dispatchers.Default) { settings.setSpendingBudget(v) } } }
        assertTrue(settings.settings.first().spendingBudget in values)
        settings.setSpendingBudget(-5)
        assertEquals(0L, settings.settings.first().spendingBudget)
        settings.setSpendingBudget(Long.MAX_VALUE)
        assertEquals(app.leftover.domain.MonthlyBudget.MAX, settings.settings.first().spendingBudget)
    }

    @Test fun `the profile photo setting is kept, only for its own file names, and cleared with everything`() = runBlocking {
        settings.setProfilePhoto("photo-123.jpg")
        assertEquals("photo-123.jpg", SettingsRepository(ApplicationProvider.getApplicationContext()).settings.first().profilePhoto)
        settings.setProfilePhoto("../databases/leftover.db")
        assertNull(settings.settings.first().profilePhoto)
        settings.setProfilePhoto("photo-456.jpg")
        repo.deleteEverything()
        assertNull(settings.settings.first().profilePhoto)
    }

    @Test fun `theme choices and pin lockout survive a restart`() = runBlocking {
        settings.setAccent(Accent.PEACH)
        settings.setFont(AppFont.JAKARTA)
        settings.setPin("hash", "salt")
        settings.setPinFailures(6, 12_345L)
        val again = SettingsRepository(ApplicationProvider.getApplicationContext()).settings.first()
        assertEquals(Accent.PEACH, again.accent)
        assertEquals(AppFont.JAKARTA, again.font)
        assertEquals(6, again.pinFailures)
        assertEquals(12_345L, again.pinLockedUntil)
        settings.setPin(null, null)
        val cleared = settings.settings.first()
        assertNull(cleared.pinHash)
        assertEquals(0, cleared.pinFailures)
    }
}
