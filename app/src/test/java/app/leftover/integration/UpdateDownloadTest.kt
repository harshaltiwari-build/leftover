package app.leftover.integration

import app.leftover.data.HttpUpdateSource
import app.leftover.data.UpdateException
import app.leftover.domain.ReleaseInfo
import app.leftover.domain.UpdatePolicy
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The real download code against a small local web server that sometimes misbehaves. A file only survives if it is
 * exactly the size promised and matches its checksum; everything else is deleted and explained.
 */
class UpdateDownloadTest {
    private lateinit var server: HttpServer
    private lateinit var dir: File
    private val port get() = server.address.port
    private val good = ByteArray(300_000) { (it * 31).toByte() }
    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    private fun policy() = UpdatePolicy(setOf("127.0.0.1"), emptyList(), httpsOnly = false, ports = setOf(port))
    private fun source(timeout: Int = 3_000, deadline: Long = 60_000) = HttpUpdateSource("http://127.0.0.1:$port/api", policy(), timeout, deadline)
    private fun info(bytes: ByteArray = good, path: String = "/apk", sha: String = sha(bytes), size: Long = bytes.size.toLong()) =
        ReleaseInfo("0.9.0", "Notes", "leftover-0.9.0.apk", "http://127.0.0.1:$port$path", size, sha)
    private fun failure(block: () -> Unit): String = try { block(); "no error" } catch (e: UpdateException) { e.message.orEmpty() }

    @Before fun start() {
        dir = File.createTempFile("upd", "").apply { delete(); mkdirs() }
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply { executor = Executors.newFixedThreadPool(24) }
        fun reply(path: String, code: Int, body: ByteArray, headers: Map<String, String> = emptyMap(), declare: Long = body.size.toLong()) =
            server.createContext(path) { ex -> headers.forEach { (k, v) -> ex.responseHeaders.add(k, v) }; ex.sendResponseHeaders(code, if (code in 300..399) -1 else declare); if (code !in 300..399) ex.responseBody.use { it.write(body) } else ex.close() }
        reply("/apk", 200, good)
        reply("/tampered", 200, good.copyOf().also { it[10] = 1 })
        reply("/short", 200, good.copyOf(100_000), declare = good.size.toLong())
        reply("/long", 200, ByteArray(good.size + 5_000), declare = (good.size + 5_000).toLong())
        reply("/missing", 404, ByteArray(0))
        reply("/boom", 500, ByteArray(0))
        reply("/redirect-ok", 302, ByteArray(0), mapOf("Location" to "http://127.0.0.1:$port/apk"))
        reply("/redirect-evil", 302, ByteArray(0), mapOf("Location" to "http://evil.example/apk"))
        reply("/redirect-loop", 302, ByteArray(0), mapOf("Location" to "http://127.0.0.1:$port/redirect-loop"))
        reply("/api", 200, """{"tag_name":"v0.9.0","draft":false,"prerelease":false,"body":"x","assets":[]}""".toByteArray())
        reply("/api-limited", 403, """{"message":"API rate limit exceeded"}""".toByteArray())
        reply("/api-huge", 200, ByteArray(5_000_000) { 'a'.code.toByte() })
        server.createContext("/hang") { Thread.sleep(8_000) }
        server.start()
    }

    @After fun stop() { server.stop(0); dir.deleteRecursively() }

    @Test fun `a good file is saved only after its size and checksum match, with progress reported`() = runBlocking {
        val seen = mutableListOf<Long>()
        val file = source().download(info(), dir) { done, _ -> seen += done }
        assertEquals("leftover-0.9.0.apk", file.name)
        assertTrue(file.readBytes().contentEquals(good))
        assertEquals(good.size.toLong(), seen.last())
        assertTrue(seen.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(listOf("leftover-0.9.0.apk"), dir.list()!!.toList())
    }

    @Test fun `a file that doesn't match its checksum is deleted and never offered`() {
        val msg = failure { runBlocking { source().download(info(path = "/tampered"), dir) { _, _ -> } } }
        assertTrue(msg, msg.contains("checksum"))
        assertEquals(emptyList<String>(), dir.list()!!.toList())
    }

    @Test fun `a file shorter or longer than promised is rejected and removed`() {
        assertTrue(failure { runBlocking { source().download(info(path = "/short"), dir) { _, _ -> } } }.isNotEmpty())
        val longMsg = failure { runBlocking { source().download(info(bytes = ByteArray(good.size + 5_000), path = "/long", size = good.size.toLong()), dir) { _, _ -> } } }
        assertTrue(longMsg, longMsg.contains("bigger", true) || longMsg.contains("size", true))
        assertEquals(emptyList<String>(), dir.list()!!.toList())
    }

    @Test fun `a redirect to a trusted place is followed and to anywhere else is refused`() {
        runBlocking { source().download(info(path = "/redirect-ok"), dir) { _, _ -> } }
        assertEquals(1, dir.list()!!.size)
        dir.deleteRecursively(); dir.mkdirs()
        val msg = failure { runBlocking { source().download(info(path = "/redirect-evil"), dir) { _, _ -> } } }
        assertTrue(msg, msg.contains("trusted", true))
        assertTrue(failure { runBlocking { source().download(info(path = "/redirect-loop"), dir) { _, _ -> } } }.contains("redirect", true))
        assertEquals(emptyList<String>(), dir.list()!!.toList())
    }

    @Test fun `server errors and missing files give a plain message`() {
        assertTrue(failure { runBlocking { source().download(info(path = "/missing"), dir) { _, _ -> } } }.contains("404"))
        assertTrue(failure { runBlocking { source().download(info(path = "/boom"), dir) { _, _ -> } } }.contains("500"))
    }

    @Test fun `a connection that goes quiet times out instead of hanging forever`() {
        val started = System.currentTimeMillis()
        val msg = failure { runBlocking { source(timeout = 700).download(info(path = "/hang"), dir) { _, _ -> } } }
        assertTrue(msg, msg.contains("connect", true) || msg.contains("time", true))
        assertTrue("took ${System.currentTimeMillis() - started} ms", System.currentTimeMillis() - started < 6_000)
    }

    @Test fun `the release check reads GitHub's answer, copes with rate limits and refuses oversized replies`() {
        // This API answer has no usable asset, so nothing is offered: that's "no update", not an error.
        assertNull(runBlocking { source().latest() })
        val limited = HttpUpdateSource("http://127.0.0.1:$port/api-limited", policy(), 3_000)
        assertTrue(failure { runBlocking { limited.latest() } }.contains("limit", true))
        val huge = HttpUpdateSource("http://127.0.0.1:$port/api-huge", policy(), 3_000)
        assertTrue(failure { runBlocking { huge.latest() } }.isNotEmpty())
    }

    @Test fun `an address outside the trusted list is never contacted`() {
        val stranger = HttpUpdateSource("http://127.0.0.1:$port/api", UpdatePolicy.Strict, 3_000)
        assertTrue(failure { runBlocking { stranger.latest() } }.contains("trusted", true))
    }

    @Test fun `cancelling mid-download leaves nothing behind`() = runBlocking {
        server.createContext("/slow") { ex ->
            ex.sendResponseHeaders(200, good.size.toLong())
            ex.responseBody.use { out -> good.toList().chunked(10_000).forEach { out.write(it.toByteArray()); out.flush(); Thread.sleep(150) } }
        }
        val scope = CoroutineScope(Dispatchers.IO)
        val job = scope.launch { runCatching { source().download(info(path = "/slow"), dir) { _, _ -> } } }
        delay(400)
        job.cancel(); job.join()
        delay(300)
        assertEquals(emptyList<String>(), dir.list()!!.toList())
        scope.cancel()
    }

    // Attack: many phones updating at once must each get exactly the right file, with no mix-ups or leftovers.
    @Test fun `twenty downloads at once all come out right`() = runBlocking {
        val dirs = (1..20).map { File(dir, "u$it").apply { mkdirs() } }
        val files = dirs.map { d -> async(Dispatchers.IO) { source().download(info(), d) { _, _ -> } } }.awaitAll()
        files.forEach { assertTrue(it.readBytes().contentEquals(good)) }
        dirs.forEach { assertEquals(1, it.list()!!.size) }
        assertNotNull(files)
        assertFalse(files.isEmpty())
    }

    // Attack: a server that dribbles a byte every so often never trips the per-read timeout. The whole download has a deadline.
    @Test fun `a slow-drip server can't hold a download open forever`() {
        server.createContext("/drip") { ex ->
            ex.sendResponseHeaders(200, good.size.toLong())
            ex.responseBody.use { out -> try { repeat(good.size) { out.write(1); out.flush(); Thread.sleep(200) } } catch (_: Exception) { } }
        }
        val started = System.currentTimeMillis()
        val msg = failure { runBlocking { source(timeout = 3_000, deadline = 1_500).download(info(path = "/drip"), dir) { _, _ -> } } }
        assertTrue(msg, msg.contains("too long", true))
        assertTrue("took ${System.currentTimeMillis() - started} ms", System.currentTimeMillis() - started < 5_000)
        assertEquals(emptyList<String>(), dir.list()!!.toList())
    }
}
