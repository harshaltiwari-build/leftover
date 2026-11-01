package app.leftover.integration

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.leftover.data.db.LeftoverDatabase
import app.leftover.data.db.TxType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Upgrading a phone's database must keep every entry. Builds a real version-8 file and opens it with the current app. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val name = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LeftoverDatabase::class.java)

    @Test fun `version 8 entries survive the upgrade to the current version`() = runBlocking {
        helper.createDatabase(name, 8).apply {
            execSQL("INSERT INTO accounts (id, name, type, openingBalance, includeInLeft, dueDay, archived, createdAt, statementDay) VALUES (1, 'HDFC', 'BANK', 1000000, 1, NULL, 0, 0, NULL)")
            execSQL("INSERT INTO categories (id, name, icon, color, kind, budgetLimit, rollover, hidden, sortOrder) VALUES (1, 'Food', 'food', 'peach', 'EXPENSE', 400000, 0, 0, 0)")
            execSQL(
                "INSERT INTO transactions (id, type, amount, accountId, toAccountId, categoryId, goalId, merchant, note, occurredAt, splitOthers, excludeFromBudgets, source, createdAt) " +
                    "VALUES (7, 'EXPENSE', 61200, 1, NULL, 1, NULL, 'Zepto', 'milk', 1763125320000, 0, 0, 'MANUAL', 1763125320000)",
            )
            close()
        }
        // Room runs every migration up to the current version and checks the result against the exported schema.
        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), LeftoverDatabase::class.java, name).allowMainThreadQueries().build()
        val tx = db.transactions().all().single()
        assertEquals(7L, tx.id)
        assertEquals(TxType.EXPENSE, tx.type)
        assertEquals(61200L, tx.amount)
        assertEquals("Zepto", tx.merchant)
        assertEquals("milk", tx.note)
        assertNull(tx.feeling)
        assertNull(tx.receiptFile)
        assertEquals(400000L, db.categories().all().single().budgetLimit)
        db.close()
    }
}
