package app.leftover.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A loan or instalment plan paid monthly: a phone on no-cost EMI, a laptop, a personal loan.
 * [annualRateBps] is the yearly interest in basis points (1200 = 12%); null means no interest or unknown.
 */
@Entity(tableName = "emis")
data class EmiEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val monthlyAmount: Long,
    val totalMonths: Int,
    val paidMonths: Int = 0,
    val dueDay: Int,
    val annualRateBps: Int? = null,
    val accountId: Long? = null,
    /** Due date (epoch day) of the last instalment paid, so the next one is never offered twice. */
    val paidThroughEpochDay: Long? = null,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val remainingMonths: Int get() = (totalMonths - paidMonths).coerceAtLeast(0)
}
