package app.leftover.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.leftover.domain.PendingReason
import app.leftover.domain.SmsKind

/** A bank or wallet sender header such as `HDFCBK`. [trusted] lets clean debits from it save without asking. */
@Entity(tableName = "sms_senders")
data class SmsSenderEntity(
    @PrimaryKey val sender: String,
    val trusted: Boolean = false,
    val lastSeen: Long,
    val seen: Int = 1,
    /** Texts from an ignored sender are skipped and never saved or queued. */
    @ColumnInfo(defaultValue = "0") val ignored: Boolean = false,
)

/**
 * A recognised bank text waiting for the user. Only what was parsed is kept: the text itself is never stored.
 */
@Entity(tableName = "pending_sms")
data class PendingSmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val kind: SmsKind,
    val amount: Long,
    val merchant: String,
    val last4: String?,
    val isCard: Boolean,
    val occurredAt: Long,
    val reason: PendingReason,
    /** The account the digits matched, when they matched one. */
    val accountId: Long? = null,
    val suggestedCategoryId: Long? = null,
)

/** A fingerprint of a text already handled, so the same message is never added twice. No text is kept. */
@Entity(tableName = "sms_keys", indices = [Index("fingerprint")])
data class SmsKeyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fingerprint: String,
    val at: Long,
)

/** Remembers which of the user's accounts a set of last-four digits belongs to. */
@Entity(tableName = "sms_account_links")
data class SmsAccountLinkEntity(
    @PrimaryKey val accountKey: String,
    val accountId: Long,
)

/** "Anything containing [pattern] belongs in [categoryId]." Beats what the app has learned from past entries. */
@Entity(tableName = "capture_rules")
data class CaptureRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pattern: String,
    val categoryId: Long,
)

enum class MandateStatus { NEW, TRACKED, IGNORED }

/** A recurring UPI debit (AutoPay) seen in a text, for the user to track or dismiss. */
@Entity(tableName = "mandates")
data class MandateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchant: String,
    val amount: Long,
    val frequency: String,
    val nextDueEpochDay: Long? = null,
    val firstSeen: Long,
    val lastSeen: Long,
    val status: MandateStatus = MandateStatus.NEW,
)
