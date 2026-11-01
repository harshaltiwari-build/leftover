package app.leftover.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.leftover.domain.Paise

/**
 * How numbers move. The headline amount counts to its new value in whole rupees and lands exactly on the real figure;
 * with Android's "Remove animations" on, nothing moves.
 */
object Motion {
    private const val COUNT_MS = 600

    fun durationMs(reduce: Boolean): Int = if (reduce) 0 else COUNT_MS

    /** The value shown [progress] (0..1) of the way from [from] to [to]: whole rupees on the way, exactly [to] at the end. */
    fun countValue(from: Paise, to: Paise, progress: Float): Paise {
        if (progress >= 1f) return to
        if (progress <= 0f) return from
        val step = ((to - from).toDouble() * progress).toLong()
        return (from + step) / 100 * 100
    }
}

/** True when the phone's animations are switched off (Settings → Accessibility → Remove animations). */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
}
