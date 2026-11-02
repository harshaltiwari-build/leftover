package app.leftover.domain

import java.net.URI
import org.json.JSONObject

/** A release Leftover can offer: its version, the plain-text notes, the one file to download and the checksum to verify it by. */
data class ReleaseInfo(val version: String, val notes: String, val apkName: String, val apkUrl: String, val sizeBytes: Long, val sha256: String)

/** Which links an update may touch. [Strict] is GitHub only, over https, on the standard port, with no user-info tricks. */
class UpdatePolicy(private val hosts: Set<String>, private val hostSuffixes: List<String>, private val httpsOnly: Boolean = true, private val ports: Set<Int> = setOf(-1, 443)) {
    fun allows(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        // Real host names are at most 253 characters in labels of at most 63; anything else is an attack, not an address.
        if (url.length > 2_000 || host.length > 253 || host.split('.').any { it.isEmpty() || it.length > 63 }) return false
        if (uri.userInfo != null || uri.port !in ports) return false
        if (httpsOnly && uri.scheme != "https") return false
        if (!httpsOnly && uri.scheme != "https" && uri.scheme != "http") return false
        return host in hosts || hostSuffixes.any { host.endsWith(it) && host.length > it.length }
    }

    companion object {
        val Strict = UpdatePolicy(setOf("github.com", "api.github.com"), listOf(".githubusercontent.com"))
    }
}

/**
 * Reading what GitHub says about the latest release, and deciding if it's newer. A release is only usable when it has
 * exactly the app's own file name, a link to exactly where that file lives, a sane size, and a checksum for it in its
 * notes. Anything else is ignored rather than guessed at.
 */
object AppUpdate {
    const val REPO = "harshaltiwari-build/leftover"
    const val MAX_APK_BYTES = 120L * 1024 * 1024
    const val MAX_NOTES = 1_500
    private const val MAX_JSON_CHARS = 2_000_000
    private val versionPattern = Regex("""^\d+(?:\.\d+){1,3}$""")

    /** The numbers at the start of a version such as "v0.8.2" or "0.8.2-qa", or null when there are none. */
    private fun numbers(version: String): List<Int>? {
        val m = Regex("""^[vV]?(\d+(?:\.\d+){0,3})""").find(version.trim()) ?: return null
        return m.groupValues[1].split('.').map { it.toIntOrNull() ?: return null }
    }

    fun isNewer(installed: String, remote: String): Boolean {
        val a = numbers(installed) ?: return false
        val b = numbers(remote) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (y != x) return y > x
        }
        return false
    }

    fun parse(json: String): ReleaseInfo? = runCatching {
        if (json.length > MAX_JSON_CHARS) return null
        val root = JSONObject(json)
        if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null
        val tag = root.optString("tag_name", "")
        val version = tag.removePrefix("v")
        if (version.length > 20 || !versionPattern.matches(version) || version.split('.').any { it.length > 6 }) return null
        val name = "leftover-$version.apk"
        val asset = (0 until (root.optJSONArray("assets")?.length() ?: 0)).map { root.getJSONArray("assets").getJSONObject(it) }.firstOrNull { it.optString("name") == name } ?: return null
        val url = asset.optString("browser_download_url", "")
        // Exactly where a release file lives, so a doctored release can't point the download somewhere else.
        if (url != "https://github.com/$REPO/releases/download/$tag/$name" || !UpdatePolicy.Strict.allows(url)) return null
        val size = asset.optLong("size", 0)
        if (size !in 1..MAX_APK_BYTES) return null
        val body = root.optString("body", "")
        val sha = Regex("""SHA-256 of `${Regex.escape(name)}`:\s*`([0-9a-fA-F]{64})`""").find(body)?.groupValues?.get(1)?.lowercase() ?: return null
        ReleaseInfo(version, plainNotes(body), name, url, size, sha)
    }.getOrNull()

    /** The notes as plain text: markup removed, the checksum line dropped, capped in length. */
    private fun plainNotes(body: String): String = body.take(20_000).replace(Regex("<[^>]*>"), "").replace("<", "").replace(">", "")
        .lines().filterNot { it.contains("SHA-256 of") }.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim().take(MAX_NOTES)
}
