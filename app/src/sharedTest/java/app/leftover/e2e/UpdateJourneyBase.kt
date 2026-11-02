package app.leftover.e2e

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.leftover.data.UpdateException
import app.leftover.data.UpdateSource
import app.leftover.domain.ReleaseInfo
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Shared set-up for the update journeys: a fake release source stands in for GitHub, so no journey touches the network. */
abstract class UpdateJourneyBase : JourneyBase() {
    protected val checks = AtomicInteger()
    protected val downloads = AtomicInteger()
    protected val release = ReleaseInfo("9.9.9", "A new look for the timeline.", "leftover-9.9.9.apk", "https://github.com/x/leftover-9.9.9.apk", 1_000, "a".repeat(64))

    protected fun useSource(latest: ReleaseInfo? = release, failWith: String? = null) {
        app.updateSource = object : UpdateSource {
            override suspend fun latest(): ReleaseInfo? { checks.incrementAndGet(); failWith?.let { throw UpdateException(it) }; return latest }
            override suspend fun download(info: ReleaseInfo, dir: File, onProgress: (Long, Long) -> Unit): File {
                downloads.incrementAndGet(); dir.mkdirs(); onProgress(1_000, 1_000)
                return File(dir, info.apkName).apply { writeText("apk") }
            }
        }
        // The fake file isn't a real app, so skip the app-signature check that a real download goes through.
        app.updateVerify = { _, _ -> null }
    }

    /** Settings → About Leftover. */
    protected fun openAbout() {
        navigate { compose.onNodeWithContentDescription("Your profile").performClick() }
        navigate { compose.onNodeWithContentDescription("Settings").performClick() }
        navigate { compose.onNodeWithText("About Leftover").performScrollTo().performClick() }
        waitForText("Check for updates")
    }

    /** Update work finishes on background threads, in real time. */
    protected fun settle() { Thread.sleep(2_500); compose.waitForIdle() }
}
