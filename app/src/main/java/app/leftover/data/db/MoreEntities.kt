package app.leftover.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A label for spending that cuts across categories: a trip, a wedding, "birthday". A budget is optional. */
@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: String = "lavender",
    val budget: Long? = null,
)

/** Which tags a transaction carries. A transaction can have several. */
@Entity(tableName = "transaction_tags", primaryKeys = ["transactionId", "tagId"], indices = [Index("tagId")])
data class TransactionTagEntity(val transactionId: Long, val tagId: Long)

/** A spend you repeat, so one tap fills in the amount, category and merchant. */
@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Long,
    val categoryId: Long?,
    val merchant: String = "",
    val accountId: Long? = null,
)

/**
 * Things worth knowing about a credit card, entered by hand: the annual fee and the spend that waives it,
 * and a rewards balance with its expiry.
 */
@Entity(tableName = "card_perks")
data class CardPerkEntity(
    @PrimaryKey val accountId: Long,
    val annualFee: Long? = null,
    val waiverSpend: Long? = null,
    /** Start of the year the waiver is measured over (usually the card anniversary). */
    val yearStartEpochDay: Long? = null,
    val points: Long = 0,
    val pointsExpiryEpochDay: Long? = null,
    val creditLimit: Long? = null,
)

/** A personal challenge the user set: spend nothing, or nothing in one category, between two dates. There are no streaks. */
@Entity(tableName = "challenges")
data class ChallengeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Null means no spending at all; otherwise only this category counts. */
    val categoryId: Long? = null,
    val startEpochDay: Long,
    val endEpochDay: Long,
)

/**
 * A shared bill that repeats every month, such as rent. On each due day Leftover adds the split by itself.
 * [participants] are friend ids separated by commas; [includesMe] says whether you share the cost.
 */
@Entity(tableName = "recurring_splits")
data class RecurringSplitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val total: Long,
    /** A friend id, or 0 when you pay. */
    val paidBy: Long,
    val participants: String,
    val includesMe: Boolean = true,
    val groupId: Long? = null,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val dueDay: Int,
    /** The due date (epoch day) of the last split created, so each month happens once. */
    val lastCreatedEpochDay: Long? = null,
    val archived: Boolean = false,
) {
    val participantIds: List<Long> get() = participants.split(',').mapNotNull { it.trim().toLongOrNull() }
}
