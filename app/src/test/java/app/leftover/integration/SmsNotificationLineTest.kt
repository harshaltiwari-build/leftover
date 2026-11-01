package app.leftover.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.leftover.data.LeftoverRepository
import app.leftover.data.OnboardingAnswers
import app.leftover.data.SettingsRepository
import app.leftover.data.db.LeftoverDatabase
import app.leftover.data.db.PendingSmsEntity
import app.leftover.domain.rupees
import app.leftover.sms.ReviewAction
import app.leftover.sms.SmsAlerts
import app.leftover.sms.SmsIngestor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** A bank text waiting for review tells you what saving it does to today, and never shows amounts when they're hidden. */
@RunWith(AndroidJUnit4::class)
class SmsNotificationLineTest {
    private lateinit var db: LeftoverDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var repo: LeftoverRepository
    private val lines = mutableListOf<String?>()
    private val alerts = object : SmsAlerts {
        override fun saved(transactionId: Long, amount: Long, merchant: String, categoryName: String?, hideAmounts: Boolean, todayLine: String?) { lines += todayLine }
        override fun review(pending: PendingSmsEntity, actions: List<ReviewAction>, hideAmounts: Boolean, todayLine: String?) { lines += todayLine }
    }

    @Before fun setUp() { runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LeftoverDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsRepository(context)
        settings.clear()
        repo = LeftoverRepository(db, settings)
        repo.completeOnboarding(OnboardingAnswers(null, null, "Salary", rupees(45_000), 1, rupees(10_000), emptyList()))
        settings.setSmsTracking(true)
    } }

    @After fun tearDown() { runBlocking { settings.clear() }; db.close() }

    private val text = "Rs.250.00 debited from A/c XX1234 on 01-11-26 to VPA zepto@ybl. UPI Ref 412345678901"

    @Test fun `a waiting bank text says what saving it does to today`(): Unit = runBlocking {
        SmsIngestor(repo, alerts).handle("VM-HDFCBK", text, System.currentTimeMillis())
        assertEquals(1, lines.size)
        assertTrue(lines.single().toString(), lines.single()!!.startsWith("Save it and today goes ₹"))
    }

    @Test fun `with balances hidden the line is left out`(): Unit = runBlocking {
        settings.setHideBalances(true)
        SmsIngestor(repo, alerts).handle("VM-HDFCBK", text, System.currentTimeMillis())
        assertEquals(listOf<String?>(null), lines)
    }
}
