package app.leftover.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        GoalEntity::class,
        BillEntity::class,
        WishEntity::class,
        PersonEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class,
        SharedExpenseEntity::class,
        SplitShareEntity::class,
        SettlementEntity::class,
        SmsSenderEntity::class,
        PendingSmsEntity::class,
        SmsKeyEntity::class,
        SmsAccountLinkEntity::class,
        EmiEntity::class,
        CaptureRuleEntity::class,
        MandateEntity::class,
        TagEntity::class,
        TransactionTagEntity::class,
        TemplateEntity::class,
        CardPerkEntity::class,
        ChallengeEntity::class,
        RecurringSplitEntity::class,
    ],
    version = 9,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 7), AutoMigration(from = 7, to = 8), AutoMigration(from = 8, to = 9)],
)
abstract class LeftoverDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun categories(): CategoryDao
    abstract fun transactions(): TransactionDao
    abstract fun goals(): GoalDao
    abstract fun bills(): BillDao
    abstract fun wishlist(): WishDao
    abstract fun shared(): SharedDao
    abstract fun sms(): SmsDao
    abstract fun emis(): EmiDao
    abstract fun more(): MoreDao

    companion object {
        fun create(context: Context): LeftoverDatabase =
            Room.databaseBuilder(context, LeftoverDatabase::class.java, "leftover.db").build()
    }
}

/** Starter categories. Each has one colour and icon that the whole app uses. */
object DefaultCategories {
    val all: List<CategoryEntity> = listOf(
        CategoryEntity(name = "Eating out", icon = "food", color = "peach", kind = CategoryKind.EXPENSE, sortOrder = 0),
        CategoryEntity(name = "Coffee", icon = "coffee", color = "sand", kind = CategoryKind.EXPENSE, sortOrder = 1),
        CategoryEntity(name = "Groceries", icon = "groceries", color = "mint", kind = CategoryKind.EXPENSE, sortOrder = 2),
        CategoryEntity(name = "Transport", icon = "transport", color = "sky", kind = CategoryKind.EXPENSE, sortOrder = 3),
        CategoryEntity(name = "Shopping", icon = "shopping", color = "lavender", kind = CategoryKind.EXPENSE, sortOrder = 4),
        CategoryEntity(name = "Snacks", icon = "snacks", color = "pistachio", kind = CategoryKind.EXPENSE, sortOrder = 5),
        CategoryEntity(name = "Fun & subscriptions", icon = "fun", color = "rose", kind = CategoryKind.EXPENSE, sortOrder = 6),
        CategoryEntity(name = "Personal care", icon = "personal", color = "butter", kind = CategoryKind.EXPENSE, sortOrder = 7),
        CategoryEntity(name = "Other", icon = "other", color = "grey", kind = CategoryKind.EXPENSE, sortOrder = 8),
        CategoryEntity(name = "Salary", icon = "income", color = "lime", kind = CategoryKind.INCOME, sortOrder = 20),
        CategoryEntity(name = "Freelance", icon = "income", color = "lime", kind = CategoryKind.INCOME, sortOrder = 21),
        CategoryEntity(name = "Pocket money", icon = "income", color = "lime", kind = CategoryKind.INCOME, sortOrder = 22),
        CategoryEntity(name = "Gift", icon = "income", color = "lime", kind = CategoryKind.INCOME, sortOrder = 23),
        CategoryEntity(name = "Other income", icon = "income", color = "lime", kind = CategoryKind.INCOME, sortOrder = 24),
    )
}
