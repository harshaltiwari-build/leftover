package app.leftover.data

import app.leftover.domain.AppUpdate
import app.leftover.domain.ReleaseInfo
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where the update flow stands, for the About screen and the Home banner. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    data class Downloading(val info: ReleaseInfo, val done: Long, val total: Long) : UpdateState
    data class Ready(val info: ReleaseInfo, val file: File) : UpdateState
    /** [info] is kept when the download can simply be tried again. */
    data class Failed(val message: String, val info: ReleaseInfo? = null) : UpdateState { val canRetryDownload: Boolean get() = info != null }
}

/**
 * The update flow: check, offer, download, verify. It only does anything when asked (a tap, or the optional daily check),
 * it ignores a tap while it's already busy, and a downloaded file is deleted unless it passes [verify].
 */
class UpdateManager(
    private val source: () -> UpdateSource,
    private val installedVersion: String,
    private val dir: File,
    private val verify: (File, ReleaseInfo) -> String?,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private val lock = Any()

    /** Moves to [to] only from one of the [from] states; false (and nothing changes) otherwise. Makes double taps harmless. */
    private fun move(from: (UpdateState) -> Boolean, to: UpdateState): Boolean = synchronized(lock) {
        if (!from(_state.value)) false else { _state.value = to; true }
    }

    /** The "Check for updates" button. */
    fun check() {
        if (!move({ it is UpdateState.Idle || it is UpdateState.UpToDate || it is UpdateState.Failed || it is UpdateState.Available }, UpdateState.Checking)) return
        scope.launch { runCheck(silent = false) }
    }

    /** The once-a-day check, if switched on. Quiet: nothing is shown unless there's an update. */
    suspend fun autoCheckIfDue(enabled: Boolean, lastCheckAt: Long, recordCheck: suspend (Long) -> Unit) {
        if (!enabled) return
        val t = now()
        // A clock set back in time shouldn't mean checking all the time.
        if (lastCheckAt > t || t - lastCheckAt < DAY_MS) return
        if (!move({ it is UpdateState.Idle }, UpdateState.Checking)) return
        recordCheck(t)
        runCheck(silent = true)
    }

    private suspend fun runCheck(silent: Boolean) {
        try {
            val release = source().latest()
            _state.value = when {
                release != null && AppUpdate.isNewer(installedVersion, release.version) -> UpdateState.Available(release)
                silent -> UpdateState.Idle
                else -> UpdateState.UpToDate
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            _state.value = if (silent) UpdateState.Idle else UpdateState.Failed((e as? UpdateException)?.message ?: "Couldn't check for updates.")
        }
    }

    /** "Download and install": fetches the offered release, verifies it, and makes it ready to install. */
    fun download() {
        val offered = synchronized(lock) {
            val s = _state.value
            val info = (s as? UpdateState.Available)?.info ?: (s as? UpdateState.Failed)?.info ?: return
            _state.value = UpdateState.Downloading(info, 0, info.sizeBytes)
            info
        }
        scope.launch {
            try {
                // Only ever one downloaded file around: clear anything from before.
                dir.listFiles()?.forEach { it.delete() }
                val file = source().download(offered, dir) { done, total -> _state.value = UpdateState.Downloading(offered, done, total) }
                val problem = verify(file, offered)
                if (problem != null) {
                    file.delete()
                    _state.value = UpdateState.Failed(problem)
                } else _state.value = UpdateState.Ready(offered, file)
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                _state.value = UpdateState.Failed((e as? UpdateException)?.message ?: "The download failed.", offered)
            }
        }
    }

    private companion object { const val DAY_MS = 24L * 3_600_000L }
}
