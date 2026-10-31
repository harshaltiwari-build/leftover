package app.leftover.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class AccountType { BANK, CASH, CARD, SAVINGS }

/** Money moves, kept explicit so transfers and refunds never count as spending or income. */
enum class TxType {
    EXPENSE, INCOME, TRANSFER, REFUND,

    /** A friend paying you back. Money in, never income. */
    REPAYMENT,

    /** You paying a friend back. Money out, never spending. */
    SETTLE_OUT,
    GOAL_SAVE, GOAL_WITHDRAW,
}

enum class TxSource { MANUAL, VOICE, SCAN, SMS, IMPORT, WIDGET, NOTIFICATION }

enum class CategoryKind { EXPENSE, INCOME }

/** How a spend felt afterwards. Belongs to the spend, not the category: the same coffee can be needed one day and a regret the next. */
enum class TxFeeling(val label: String) { NEEDED("Needed"), WORTH_IT("Worth it"), REGRET("Regret") }

enum class WishStatus { WAITING, BOUGHT, SKIPPED }

/**
 * Balances are signed: a credit card's balance is negative while money is owed on it,
 * so one formula works for every account type.
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    val openingBalance: Long,
    val includeInLeft: Boolean,
    val dueDay: Int? = null,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** Day of month the card issues its statement, for cards only. */
    val statementDay: Int? = null,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val color: String,
    val kind: CategoryKind,
    val budgetLimit: Long? = null,
    val rollover: Boolean = false,
    val hidden: Boolean = false,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "transactions",
    indices = [Index("occurredAt"), Index("accountId"), Index("categoryId")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TxType,
    val amount: Long,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val goalId: Long? = null,
    val merchant: String = "",
    val note: String = "",
    val occurredAt: Long,
    /** Part of an expense other people owe back; budgets only count [amount] minus this. */
    val splitOthers: Long = 0,
    val excludeFromBudgets: Boolean = false,
    val source: TxSource = TxSource.MANUAL,
    val createdAt: Long = System.currentTimeMillis(),
    val feeling: TxFeeling? = null,
    /** File name of an attached receipt photo inside the app's private `receipts` folder. Photos are not part of backups. */
    val receiptFile: String? = null,
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val target: Long,
    val perCycle: Long,
    val cover: String,
    val targetEpochDay: Long? = null,
    val autoSave: Boolean = true,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "bills")
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Long,
    val dueDay: Int,
    val icon: String,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val remindDaysBefore: Int = 1,
    val isSubscription: Boolean = false,
    val shared: Boolean = false,
    /** Epoch day of the last due date that was paid. */
    val paidThroughEpochDay: Long? = null,
    val archived: Boolean = false,
)

@Entity(tableName = "wishlist")
data class WishEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val price: Long,
    val categoryId: Long? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val decideAt: Long,
    val status: WishStatus = WishStatus.WAITING,
    val resolvedAt: Long? = null,
)
