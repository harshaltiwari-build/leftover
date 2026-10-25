package app.leftover.backup

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps the backup key safe on this phone. The key made from the user's passphrase is wrapped with a key that never leaves
 * Android Keystore, so scheduled backups can run without the passphrase and the stored value is useless on its own.
 * The passphrase itself is never stored.
 */
object BackupVault {
    private const val ALIAS = "leftover_backup_wrap"

    private fun wrappingKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    fun wrap(key: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val sealed = cipher.doFinal(key)
        return Base64.getEncoder().encodeToString(cipher.iv + sealed)
    }

    /** The key, or null when it can't be unwrapped (for example after a reinstall wiped Keystore). */
    fun unwrap(text: String): ByteArray? = runCatching {
        val raw = Base64.getDecoder().decode(text)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        cipher.doFinal(raw, 12, raw.size - 12)
    }.getOrNull()
}
