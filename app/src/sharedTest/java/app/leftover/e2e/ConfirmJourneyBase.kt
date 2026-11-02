package app.leftover.e2e

import app.leftover.domain.ScreenshotStatus
import app.leftover.domain.rupees
import app.leftover.ui.add.EntryDraft
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Shared set-up for the confirm-screen journeys. Each journey is its own class so it starts in a fresh process. */
abstract class ConfirmJourneyBase : JourneyBase() {
    protected fun read(amount: Long = 1_000, status: ScreenshotStatus = ScreenshotStatus.SUCCESS, ok: Boolean = true) = EntryDraft(
        income = false, amount = rupees(amount), merchant = "Rohan Mehta", date = LocalDate.now().minusDays(1), time = LocalTime.of(22, 32),
        note = "UPI ref 123456789012", receipt = null, status = status, read = ok, app = "Google Pay",
    )
    /** A shared screenshot opens a new screen from state, not a tap, so the test clock is moved past its animation. */
    protected fun share(draft: EntryDraft) {
        withViewModel { it.handleScreenshot(draft) }
        // The decision runs on a background thread (database read), then the screen opens: give both real time to finish.
        Thread.sleep(4_000)
        compose.waitForIdle()
    }
    /** Database work finishes in real time, not test-clock time, so wait a moment before looking at the screen. */
    protected fun settle() { Thread.sleep(2_500); compose.waitForIdle() }
    protected fun saved() = runBlocking { app.repository.snapshot.first().transactions }

}
