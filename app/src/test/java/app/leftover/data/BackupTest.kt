package app.leftover.data

import app.leftover.data.db.AccountEntity
import app.leftover.data.db.AccountType
import app.leftover.data.db.BillEntity
import app.leftover.data.db.CaptureRuleEntity
import app.leftover.data.db.CardPerkEntity
import app.leftover.data.db.ChallengeEntity
import app.leftover.data.db.EmiEntity
import app.leftover.data.db.RecurringSplitEntity
import app.leftover.data.db.TagEntity
import app.leftover.data.db.TemplateEntity
import app.leftover.data.db.TransactionTagEntity
import app.leftover.data.db.CategoryEntity
import app.leftover.data.db.CategoryKind
import app.leftover.data.db.GoalEntity
import app.leftover.data.db.GroupEntity
import app.leftover.data.db.GroupKind
import app.leftover.data.db.GroupMemberEntity
import app.leftover.data.db.PersonEntity
import app.leftover.data.db.SettleDirection
import app.leftover.data.db.SettleMethod
import app.leftover.data.db.SettlementEntity
import app.leftover.data.db.SharedExpenseEntity
import app.leftover.data.db.SplitShareEntity
import app.leftover.data.db.TransactionEntity
import app.leftover.data.db.TxSource
import app.leftover.data.db.TxType
import app.leftover.data.db.WishEntity
import app.leftover.data.db.WishStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupTest {
    private val sample = BackupData(
        settings = BackupSettings("Asha", 5, "Salary", 4_200_000, hideBalances = true, themeMode = ThemeMode.DARK, billReminders = false),
        accounts = listOf(
            AccountEntity(1, "HDFC", AccountType.BANK, 1_293_000, true, null, false, 1000),
            AccountEntity(2, "ICICI card", AccountType.CARD, 0, true, 28, false, 1001),
        ),
        categories = listOf(CategoryEntity(1, "Coffee", "coffee", "sand", CategoryKind.EXPENSE, 150_000, true, false, 3)),
        transactions = listOf(
            TransactionEntity(1, TxType.EXPENSE, 18_000, 1, null, 1, null, "Blue Tokai", "with \"quotes\" and ₹", 5000, 6_000, true, TxSource.SMS, 5001),
            TransactionEntity(2, TxType.TRANSFER, 250_000, 1, 2, null, null, "", "", 6000, 0, false, TxSource.MANUAL, 6001),
        ),
        goals = listOf(GoalEntity(1, "Phone", 6_000_000, 200_000, "lavender", 20_000, true, false, 7000)),
        bills = listOf(BillEntity(1, "Rent", 900_000, 1, "home", null, 1, 3, false, false, 19_000, false)),
        wishes = listOf(WishEntity(1, "Earbuds", 299_900, 1, 8000, 9000, WishStatus.SKIPPED, 9500)),
        people = listOf(PersonEntity(1, "Rohan", "rohan@okhdfc", "sky", false), PersonEntity(2, "Meera", null, "mint", false)),
        groups = listOf(GroupEntity(1, "Goa", GroupKind.TRIP, 5_000_000, 20_000, 20_005, false)),
        members = listOf(GroupMemberEntity(1, 1), GroupMemberEntity(1, 2)),
        expenses = listOf(SharedExpenseEntity(1, "Dinner", 120_000, 0, 1, 1, 5000, 1, "")),
        shares = listOf(SplitShareEntity(1, 1, 0, 60_000), SplitShareEntity(2, 1, 1, 60_000)),
        settlements = listOf(SettlementEntity(1, 1, 60_000, SettleDirection.THEY_PAID, SettleMethod.UPI, 7000, 1, 2, "thanks")),
        emis = listOf(EmiEntity(1, "Phone", 200_000, 12, 4, 27, 1200, 1, 19_000, false, 9000)),
        rules = listOf(CaptureRuleEntity(1, "swiggy", 1)),
        tags = listOf(TagEntity(1, "Goa", "sky", 5_000_000)),
        txTags = listOf(TransactionTagEntity(1, 1)),
        templates = listOf(TemplateEntity(1, "Chai", 2_000, 1, "Tea Point", 1)),
        perks = listOf(CardPerkEntity(2, 99_900, 20_000_000, 19_000, 1200, 20_000)),
        challenges = listOf(ChallengeEntity(1, "No eating out", 1, 20_000, 20_007)),
        recurring = listOf(RecurringSplitEntity(1, "Rent", 3_000_000, 0, "1,2", true, 1, 1, 1, 1, 20_000, false)),
    )

    @Test fun everythingSurvivesAWriteAndRead() {
        val back = BackupCodec.decode(BackupCodec.encode(sample, 123))
        assertEquals(sample, back)
    }

    @Test fun missingOptionalFieldsStayNull() {
        val back = BackupCodec.decode(BackupCodec.encode(sample, 1))
        assertNull(back.accounts[0].dueDay)
        assertNull(back.people[1].upiId)
        assertNull(back.transactions[1].categoryId)
    }

    @Test fun aFileThatIsNotABackupIsRefused() {
        assertThrows(BackupException::class.java) { BackupCodec.decode("hello") }
        assertThrows(BackupException::class.java) { BackupCodec.decode("{\"format\":\"something-else\"}") }
        assertThrows(BackupException::class.java) { BackupCodec.decode("[]") }
    }

    @Test fun aNewerBackupIsRefusedWithAClearMessage() {
        val newer = BackupCodec.encode(sample, 1).replace("\"version\":1", "\"version\":99")
        val e = assertThrows(BackupException::class.java) { BackupCodec.decode(newer) }
        assertEquals("This backup is from a newer version of Leftover. Update the app and try again.", e.message)
    }

    @Test fun aBackupWithoutAccountsIsRefused() {
        assertThrows(BackupException::class.java) { BackupCodec.decode(BackupCodec.encode(sample.copy(accounts = emptyList()), 1)) }
    }

    @Test fun aDamagedBackupIsRefusedNotHalfRead() {
        val text = BackupCodec.encode(sample, 1).replace("\"amount\":18000", "\"amount\":\"lots\"")
        assertThrows(BackupException::class.java) { BackupCodec.decode(text) }
    }
}
