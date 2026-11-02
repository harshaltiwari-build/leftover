package app.leftover.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Leftover declares the INTERNET permission for one reason: the update check you start. Anything that still says it has
 * no internet access, or can't connect, is a false privacy claim, so the app, the website and the docs are scanned for it.
 */
class PrivacyClaimsTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val files: List<File> = buildList {
        add(File(root, "README.md")); add(File(root, "CONTRIBUTING.md")); add(File(root, "PRIVACY.md")); add(File(root, "docs/index.html"))
        addAll(File(root, "app/src/main").walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "xml") })
    }
    private val falseClaims = listOf(
        "no internet permission", "does not declare the `internet`", "doesn't declare the internet", "no internet access", "can't connect to the internet",
        "cannot connect to the internet", "doesn't even ask for internet", "never goes online", "can't send anything anywhere", "no network calls",
        "talks to the internet", "none. leftover can't send", "leftover can't connect",
    )

    @Test fun `nothing claims the app has no internet access`() {
        val offenders = files.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                val l = line.lowercase()
                falseClaims.firstOrNull { it in l && !l.contains("claims_ok") }?.let { "${f.relativeTo(root)}:${i + 1}: $it" }
            }
        }
        assertTrue("Out-of-date privacy claims:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test fun `the permission and the promise agree`() {
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.permission.INTERNET"))
        val about = File(root, "app/src/main/java/app/leftover/ui/settings/SettingsSubScreens.kt").readText()
        assertTrue("the Privacy screen must list the Internet permission as updates-only", about.contains("Updates only"))
        assertTrue(File(root, "docs/index.html").readText().contains("update"))
    }
}
