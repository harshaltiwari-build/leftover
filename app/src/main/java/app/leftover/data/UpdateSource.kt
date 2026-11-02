package app.leftover.data

import app.leftover.domain.AppUpdate
import app.leftover.domain.ReleaseInfo
import app.leftover.domain.UpdatePolicy
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Something went wrong checking for or downloading an update. The message is for the person, in plain words. */
class UpdateException(message: String) : Exception(message)

/** Where updates come from. A fake one stands in for tests; [HttpUpdateSource] is the real thing. */
interface UpdateSource {
    /** The newest usable release, or null when there isn't one. Throws [UpdateException] when GitHub can't be reached. */
    suspend fun latest(): ReleaseInfo?

    /** Downloads the release file into [dir] and returns it. It only exists afterwards if its size and checksum are right. */
    suspend fun download(info: ReleaseInfo, dir: File, onProgress: (done: Long, total: Long) -> Unit): File
}

/**
 * Talks to GitHub with plain HTTPS and nothing else: no account, no identifiers, no cookies. Every link, including each
 * redirect, must pass the [policy]. A download is written to a temporary file, checked against the promised size and
 * checksum, and only then given its real name; any failure or cancellation deletes it.
 */
class HttpUpdateSource(
    private val latestUrl: String = "https://api.github.com/repos/${AppUpdate.REPO}/releases/latest",
    private val policy: UpdatePolicy = UpdatePolicy.Strict,
    private val timeoutMs: Int = 15_000,
    /** The whole download must finish within this, however slowly the server drips: a quiet connection can't hold it open forever. */
    private val downloadDeadlineMs: Long = 15 * 60_000L,
) : UpdateSource {
    private companion object {
        const val MAX_REDIRECTS = 5
        const val MAX_REPLY_BYTES = 2_000_000
    }

    private fun open(url: String, accept: String): HttpURLConnection {
        if (!policy.allows(url)) throw UpdateException("That address isn't a trusted one, so nothing was contacted.")
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "Leftover")
        }
    }

    private inline fun <T> network(block: () -> T): T = try { block() } catch (e: UpdateException) { throw e } catch (e: SocketTimeoutException) {
        throw UpdateException("The connection timed out. Check your internet and try again.")
    } catch (e: IOException) { throw UpdateException("Couldn't connect. Check your internet and try again.") }

    override suspend fun latest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        network {
            val c = open(latestUrl, "application/vnd.github+json")
            try {
                when (val code = c.responseCode) {
                    200 -> {
                        val bytes = c.inputStream.use { it.readNBytes(MAX_REPLY_BYTES + 1) }
                        if (bytes.size > MAX_REPLY_BYTES) throw UpdateException("GitHub's reply was too large to trust.")
                        AppUpdate.parse(String(bytes, Charsets.UTF_8))
                    }
                    403, 429 -> throw UpdateException("GitHub is limiting requests right now. Try again in a little while.")
                    404 -> null
                    else -> throw UpdateException("GitHub answered with an error ($code). Try again later.")
                }
            } finally { c.disconnect() }
        }
    }

    override suspend fun download(info: ReleaseInfo, dir: File, onProgress: (Long, Long) -> Unit): File = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val final = File(dir, info.apkName)
        val part = File(dir, info.apkName + ".part")
        part.delete(); final.delete()
        val startedAt = System.nanoTime()
        try {
            network {
                var url = info.apkUrl
                var hops = 0
                var c = open(url, "application/octet-stream")
                while (c.responseCode in 301..308 && c.responseCode != 304) {
                    val next = c.getHeaderField("Location")?.let { runCatching { URL(URL(url), it).toString() }.getOrNull() }
                    c.disconnect()
                    if (next == null || ++hops > MAX_REDIRECTS) throw UpdateException("Too many redirects, so the download was stopped.")
                    url = next
                    c = open(url, "application/octet-stream")
                }
                try {
                    if (c.responseCode != 200) throw UpdateException("The download wasn't found (error ${c.responseCode}).")
                    val digest = MessageDigest.getInstance("SHA-256")
                    var done = 0L
                    c.inputStream.use { input ->
                        part.outputStream().use { out ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                ensureActive()
                                if ((System.nanoTime() - startedAt) / 1_000_000 > downloadDeadlineMs) throw UpdateException("The download was taking too long, so it was stopped. Try again on a better connection.")
                                val n = input.read(buffer)
                                if (n < 0) break
                                done += n
                                if (done > info.sizeBytes) throw UpdateException("The file is bigger than the release says, so it was discarded.")
                                digest.update(buffer, 0, n)
                                out.write(buffer, 0, n)
                                onProgress(done, info.sizeBytes)
                            }
                        }
                    }
                    if (done != info.sizeBytes) throw UpdateException("The download was incomplete. Try again.")
                    val hex = digest.digest().joinToString("") { "%02x".format(it) }
                    if (hex != info.sha256) throw UpdateException("The file didn't match its checksum, so it was discarded. Nothing was installed.")
                } finally { c.disconnect() }
            }
            if (!part.renameTo(final)) throw UpdateException("Couldn't save the download.")
            final
        } catch (e: Throwable) {
            part.delete(); final.delete()
            throw e
        }
    }
}
