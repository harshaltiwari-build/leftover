package app.leftover.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class GroupKind { FRIENDS, HOME, TRIP, EVENT }

enum class SettleDirection { YOU_PAID, THEY_PAID }

enum class SettleMethod { UPI, CASH }

/** A friend you share costs with. Ids start at 1; id 0 is reserved for "you". */
@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val upiId: String? = null,
    val color: String = "sky",
    val archived: Boolean = false,
)

/** A set of people who share costs: flatmates, a trip, an event. A budget and dates are optional. */
@Entity(tableName = "friend_groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: GroupKind,
    val budget: Long? = null,
    val startEpochDay: Long? = null,
    val endEpochDay: Long? = null,
    val archived: Boolean = false,
)

@Entity(tableName = "group_members", primaryKeys = ["groupId", "personId"])
data class GroupMemberEntity(val groupId: Long, val personId: Long)

/** One bill that was split. [paidBy] is a person id, or 0 when you paid. */
@Entity(tableName = "shared_expenses", indices = [Index("groupId"), Index("transactionId")])
data class SharedExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val total: Long,
    val paidBy: Long,
    val groupId: Long? = null,
    val categoryId: Long? = null,
    val occurredAt: Long,
    /** The transaction that moved your money, when you paid. Null when a friend paid. */
    val transactionId: Long? = null,
    val note: String = "",
)

/** What one participant owes toward a [SharedExpenseEntity]. [personId] 0 is your own share. */
@Entity(tableName = "split_shares", indices = [Index("expenseId")])
data class SplitShareEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expenseId: Long,
    val personId: Long,
    val amount: Long,
)

/** Money that changed hands to square a balance with one person. */
@Entity(tableName = "settlements", indices = [Index("personId"), Index("transactionId")])
data class SettlementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val amount: Long,
    val direction: SettleDirection,
    val method: SettleMethod,
    val occurredAt: Long,
    val groupId: Long? = null,
    val transactionId: Long? = null,
    val note: String = "",
)
