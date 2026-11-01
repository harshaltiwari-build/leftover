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
import app.leftover.domain.SafeToday
import app.leftover.domain.TagTotals
import app.leftover.domain.rupees
import kotlinx.coroutines.flow.first
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

    @Test fun `delete everything returns to first run`() = runBlocking {
        repo.addTransaction(TransactionEntity(type = TxType.EXPENSE, amount = rupees(100), accountId = account(), categoryId = food(), occurredAt = now()))
        repo.deleteEverything()
        val s = repo.snapshot.first()
        assertTrue(s.transactions.isEmpty())
        assertTrue(s.accounts.isEmpty())
        assertTrue(!s.settings.onboarded)
        assertTrue(s.categories.isNotEmpty())
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
