package app.leftover.data

import org.json.JSONException
import org.json.JSONObject
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class WrongPassphraseException : Exception("Wrong passphrase, or the file was changed.")

/**
 * Passphrase protection for backup files. The passphrase is stretched into a 256-bit key with PBKDF2-HMAC-SHA256,
 * and the backup is sealed with AES-256-GCM, which also detects any change to the file. Nothing is stored except
 * the salt, the nonce and the sealed data, so without the passphrase the file cannot be read, and Leftover cannot recover it.
 */
object BackupCrypto {
    const val FORMAT = "leftover-backup-encrypted"
    const val VERSION = 1
    const val DEFAULT_ITERATIONS = 310_000
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private val random = SecureRandom()
    private val b64 = Base64.getEncoder()
    private val unb64 = Base64.getDecoder()

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also(random::nextBytes)

    fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Whether [text] is an encrypted backup envelope rather than a plain backup. */
    fun looksEncrypted(text: String): Boolean = try {
        JSONObject(text).optString("format") == FORMAT
    } catch (_: JSONException) {
        false
    }

    /** Seals [plaintext] with an already-derived [key]. The header is bound to the data, so it can't be swapped. */
    fun encrypt(plaintext: String, key: ByteArray, salt: ByteArray, iterations: Int = DEFAULT_ITERATIONS): String {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(header(iterations))
        val sealed = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put("format", FORMAT).put("version", VERSION).put("kdf", "PBKDF2WithHmacSHA256").put("iterations", iterations)
            .put("salt", b64.encodeToString(salt)).put("nonce", b64.encodeToString(nonce)).put("data", b64.encodeToString(sealed))
            .toString()
    }

    fun encrypt(plaintext: String, passphrase: CharArray, iterations: Int = DEFAULT_ITERATIONS): String {
        val salt = newSalt()
        return encrypt(plaintext, deriveKey(passphrase, salt, iterations), salt, iterations)
    }

    /** Returns the backup text, or throws [WrongPassphraseException] when the passphrase is wrong or the file was altered. */
    fun decrypt(envelope: String, passphrase: CharArray): String {
        val json = try {
            JSONObject(envelope)
        } catch (_: JSONException) {
            throw BackupException("That file isn't a Leftover backup.")
        }
        if (json.optString("format") != FORMAT) throw BackupException("That file isn't a Leftover backup.")
        if (json.optInt("version", 0) > VERSION) throw BackupException("This backup is from a newer version of Leftover. Update the app and try again.")
        try {
            val iterations = json.getInt("iterations")
            if (iterations !in 1_000..5_000_000) throw BackupException("This backup is damaged and can't be read.")
            val salt = unb64.decode(json.getString("salt"))
            val nonce = unb64.decode(json.getString("nonce"))
            val data = unb64.decode(json.getString("data"))
            val key = deriveKey(passphrase, salt, iterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(header(iterations))
            return String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            throw WrongPassphraseException()
        } catch (e: JSONException) {
            throw BackupException("This backup is damaged and can't be read.")
        } catch (e: IllegalArgumentException) {
            throw BackupException("This backup is damaged and can't be read.")
        }
    }

    private fun header(iterations: Int): ByteArray = "$FORMAT/$VERSION/PBKDF2WithHmacSHA256/$iterations".toByteArray(Charsets.UTF_8)
}
