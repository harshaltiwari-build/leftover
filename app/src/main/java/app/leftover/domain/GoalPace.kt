package app.leftover.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class GoalStatus { DONE, ON_TRACK, BEHIND, STEADY }

data class GoalProgress(
    val saved: Paise,
    val target: Paise,
    val perCycle: Paise,
    val finishDate: LocalDate?,
    val status: GoalStatus,
    /** How much more per cycle would be needed to hit the target date; 0 when on track or no date. */
    val shortfallPerCycle: Paise,
) {
    val fraction: Float get() = if (target <= 0) 0f else (saved.toFloat() / target).coerceIn(0f, 1f)
    val percent: Int get() = (fraction * 100).toInt()
}

/** How fast a goal fills up when [perCycle] is set aside on every payday. */
object GoalPace {
    /** Paydays still needed to cover [remaining], or null when nothing is being set aside. */
    fun cyclesNeeded(remaining: Paise, perCycle: Paise): Int? = when {
        remaining <= 0 -> 0
        perCycle <= 0 -> null
        else -> ((remaining + perCycle - 1) / perCycle).toInt()
    }

    /** The first deposit lands on [nextPayday]; each further one a month later. */
    fun finishDate(nextPayday: LocalDate, cycles: Int): LocalDate = nextPayday.plusMonths((cycles - 1).coerceAtLeast(0).toLong())

    fun paydaysUntil(nextPayday: LocalDate, targetDate: LocalDate): Int {
        if (targetDate.isBefore(nextPayday)) return 0
        return (ChronoUnit.MONTHS.between(nextPayday, targetDate) + 1).toInt()
    }

    fun progress(saved: Paise, target: Paise, perCycle: Paise, targetDate: LocalDate?, nextPayday: LocalDate): GoalProgress {
        val remaining = (target - saved).coerceAtLeast(0)
        if (remaining == 0L) return GoalProgress(saved, target, perCycle, null, GoalStatus.DONE, 0)
        val cycles = cyclesNeeded(remaining, perCycle)
        val finish = cycles?.let { finishDate(nextPayday, it) }
        if (targetDate == null) return GoalProgress(saved, target, perCycle, finish, GoalStatus.STEADY, 0)
        val available = paydaysUntil(nextPayday, targetDate).coerceAtLeast(1)
        val neededPerCycle = (remaining + available - 1) / available
        val behind = finish == null || finish.isAfter(targetDate)
        return GoalProgress(saved, target, perCycle, finish, if (behind) GoalStatus.BEHIND else GoalStatus.ON_TRACK, if (behind) (neededPerCycle - perCycle).coerceAtLeast(0) else 0)
    }
}
