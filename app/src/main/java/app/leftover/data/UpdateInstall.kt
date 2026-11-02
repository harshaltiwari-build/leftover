package app.leftover.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import app.leftover.domain.ReleaseInfo
import java.io.File
import java.security.MessageDigest

/** What an app file or an installed app says about itself: who it is, which version, and the fingerprints of its signing keys. */
data class PackageFacts(val packageName: String, val versionName: String, val versionCode: Long, val signerSha256: Set<String>)

/** Checks a downloaded file against the app that's installed, so Android is only asked to install something that will work. */
object UpdateVerifier {
    fun check(archive: PackageFacts?, installed: PackageFacts, info: ReleaseInfo): String? = when {
        archive == null -> "The downloaded file isn't a valid app, so it was discarded."
        archive.packageName != installed.packageName -> "The downloaded file isn't Leftover, so it was discarded."
        archive.versionName != info.version -> "The downloaded file doesn't match the release it came from, so it was discarded."
        archive.versionCode <= installed.versionCode -> "The downloaded version is not newer than the one you have, so it was discarded."
        archive.signerSha256.isEmpty() || archive.signerSha256 != installed.signerSha256 ->
            "This update is signed differently from the Leftover on your phone, so Android would refuse it. Back up your data in Settings → Backup, uninstall Leftover once, then install the new version and restore."
        else -> null
    }

    /** Reads the facts of the file at [apk] and of the installed app, and checks them. */
    @Suppress("DEPRECATION")
    fun check(context: Context, apk: File, info: ReleaseInfo): String? {
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = runCatching { pm.getPackageArchiveInfo(apk.path, flags) }.getOrNull()?.let(::facts)
        val installed = runCatching { pm.getPackageInfo(context.packageName, flags) }.getOrNull()?.let(::facts)
            ?: return "Couldn't read the installed app to compare the update with."
        return check(archive, installed, info)
    }

    private fun facts(p: android.content.pm.PackageInfo): PackageFacts {
        val info = p.signingInfo
        // The key it is signed with now: every signer of a multi-signer app, or the latest key of a rotated one.
        val signers = when {
            info == null -> emptyList()
            info.hasMultipleSigners() -> info.apkContentsSigners.toList()
            else -> info.signingCertificateHistory.orEmpty().takeLast(1)
        }
        return PackageFacts(
            p.packageName.orEmpty(), p.versionName.orEmpty(),
            if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else @Suppress("DEPRECATION") p.versionCode.toLong(),
            signers.map { sha(it.toByteArray()) }.toSet(),
        )
    }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02X".format(it) }
}

/** Hands a verified file to Android's own installer. Leftover never installs anything silently. */
object UpdateInstaller {
    fun intent(context: Context, file: File, canInstall: Boolean): Intent =
        if (!canInstall) Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        else Intent(Intent.ACTION_VIEW)
            .setDataAndType(FileProvider.getUriForFile(context, "${context.packageName}.files", file), "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Starts the installer, or the permission screen first when Android hasn't yet allowed Leftover to install apps. */
    fun start(context: Context, file: File): Boolean {
        val can = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()
        runCatching { context.startActivity(intent(context, file, can)) }.onFailure { return false }
        return can
    }
}
