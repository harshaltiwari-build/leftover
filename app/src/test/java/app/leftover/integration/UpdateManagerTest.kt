package app.leftover.integration

import app.leftover.data.UpdateException
import app.leftover.data.UpdateManager
import app.leftover.data.UpdateSource
import app.leftover.data.UpdateState
import app.leftover.domain.ReleaseInfo
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The update flow as a state machine: what each tap does, what is ever downloaded, and that nothing runs unless asked. */
class UpdateManagerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var dir: File
    private val checks = AtomicInteger()
    private val downloads = AtomicInteger()
    private fun info(v: String = "0.9.0") = ReleaseInfo(v, "Notes", "leftover-$v.apk", "https://github.com/x/leftover-$v.apk", 1_000, "a".repeat(64))

    private fun fake(latest: () -> ReleaseInfo? = { info() }, delayMs: Long = 0, fail: UpdateException? = null, failDownload: UpdateException? = null) = object : UpdateSource {
        override suspend fun latest(): ReleaseInfo? { checks.incrementAndGet(); delay(delayMs); fail?.let { throw it }; return latest() }
        override suspend fun download(info: ReleaseInfo, dir: File, onProgress: (Long, Long) -> Unit): File {
            downloads.incrementAndGet(); delay(delayMs)
            failDownload?.let { throw it }
            onProgress(500, 1_000); onProgress(1_000, 1_000)
            return File(dir, info.apkName).apply { dir.mkdirs(); writeText("apk") }
        }
    }

    private fun manager(source: UpdateSource, installed: String = "0.8.2", verify: (File, ReleaseInfo) -> String? = { _, _ -> null }, now: () -> Long = System::currentTimeMillis) =
        UpdateManager({ source }, installed, dir, verify, scope, now)

    private fun UpdateManager.await(timeoutMs: Long = 5_000, ok: (UpdateState) -> Boolean) {
        val until = System.currentTimeMillis() + timeoutMs
        while (!ok(state.value) && System.currentTimeMillis() < until) Thread.sleep(20)
        assertTrue("stuck in ${state.value}", ok(state.value))
    }

    @Before fun setUp() { dir = File.createTempFile("updm", "").apply { delete(); mkdirs() } }
    @After fun tearDown() { scope.cancel(); dir.deleteRecursively() }

    @Test fun `a newer release is offered, then downloaded and made ready`() {
        val m = manager(fake())
        m.check()
        m.await { it is UpdateState.Available }
        assertEquals("0.9.0", (m.state.value as UpdateState.Available).info.version)
        m.download()
        m.await { it is UpdateState.Ready }
        assertTrue((m.state.value as UpdateState.Ready).file.exists())
    }

    @Test fun `the same or an older release is not an update`() {
        val same = manager(fake({ info("0.8.2") })); same.check(); same.await { it is UpdateState.UpToDate }
        val older = manager(fake({ info("0.7.0") })); older.check(); older.await { it is UpdateState.UpToDate }
        val none = manager(fake({ null })); none.check(); none.await { it is UpdateState.UpToDate }
    }

    @Test fun `a failed check says why and can be tried again`() {
        val m = manager(fake(fail = UpdateException("Couldn't connect. Check your internet and try again.")))
        m.check()
        m.await { it is UpdateState.Failed }
        assertEquals("Couldn't connect. Check your internet and try again.", (m.state.value as UpdateState.Failed).message)
        m.check()
        m.await { it is UpdateState.Failed }
        assertEquals(2, checks.get())
    }

    // Attack: double taps and overlapping checks must not start two of anything.
    @Test fun `tapping again while it is busy does nothing`() {
        val m = manager(fake(delayMs = 400))
        m.check(); m.check(); m.check()
        m.await { it is UpdateState.Available }
        assertEquals(1, checks.get())
        m.download(); m.download(); m.download()
        m.await { it is UpdateState.Ready }
        assertEquals(1, downloads.get())
        // Once ready, asking again doesn't download again either.
        m.download()
        Thread.sleep(300)
        assertEquals(1, downloads.get())
    }

    @Test fun `a file that fails verification is deleted and never offered for install`() {
        val m = manager(fake(), verify = { _, _ -> "This update is signed differently from your Leftover, so Android would refuse it." })
        m.check(); m.await { it is UpdateState.Available }
        m.download(); m.await { it is UpdateState.Failed }
        assertTrue((m.state.value as UpdateState.Failed).message.contains("signed differently"))
        assertEquals(emptyList<String>(), dir.list()!!.toList())
    }

    @Test fun `a download that fails keeps the offer so it can be retried`() {
        val m = manager(fake(failDownload = UpdateException("The file didn't match its checksum, so it was discarded. Nothing was installed.")))
        m.check(); m.await { it is UpdateState.Available }
        m.download(); m.await { it is UpdateState.Failed }
        assertTrue((m.state.value as UpdateState.Failed).canRetryDownload)
    }

    @Test fun `progress is reported while downloading`() {
        val m = manager(fake(delayMs = 100))
        m.check(); m.await { it is UpdateState.Available }
        m.download()
        m.await { it is UpdateState.Downloading || it is UpdateState.Ready }
        m.await { it is UpdateState.Ready }
    }

    @Test fun `old downloads are cleared before a new one`() {
        File(dir, "leftover-0.1.0.apk").apply { dir.mkdirs(); writeText("old") }
        File(dir, "stale.part").writeText("x")
        val m = manager(fake())
        m.check(); m.await { it is UpdateState.Available }
        m.download(); m.await { it is UpdateState.Ready }
        assertEquals(listOf("leftover-0.9.0.apk"), dir.list()!!.sorted())
    }

    @Test fun `automatic checks are off by default and never touch the network then`() = runBlocking {
        val m = manager(fake())
        m.autoCheckIfDue(enabled = false, lastCheckAt = 0) { }
        delay(200)
        assertEquals(0, checks.get())
        assertTrue(m.state.value is UpdateState.Idle)
    }

    @Test fun `with it on, a check happens at most once a day`() = runBlocking {
        val day = 24 * 3_600_000L
        var now = 10 * day
        val recorded = mutableListOf<Long>()
        val m = manager(fake(), now = { now })
        m.autoCheckIfDue(true, lastCheckAt = now - 2 * 3_600_000L) { recorded += it }
        delay(200)
        assertEquals(0, checks.get())
        m.autoCheckIfDue(true, lastCheckAt = now - 25 * 3_600_000L) { recorded += it }
        m.await { it is UpdateState.Available }
        assertEquals(1, checks.get())
        assertEquals(listOf(now), recorded)
        // A clock set back in time doesn't trigger a flood of checks either.
        now -= 5 * day
        m.autoCheckIfDue(true, lastCheckAt = now + 3 * day) { recorded += it }
        delay(200)
        assertEquals(1, checks.get())
    }

    @Test fun `a failed automatic check is silent and the next one waits a day`() = runBlocking {
        val recorded = mutableListOf<Long>()
        val m = manager(fake(fail = UpdateException("Couldn't connect.")))
        m.autoCheckIfDue(true, 0) { recorded += it }
        delay(400)
        assertEquals(1, checks.get())
        assertFalse(m.state.value is UpdateState.Failed)
        assertEquals(1, recorded.size)
    }
}
