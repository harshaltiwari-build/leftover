package app.leftover.domain

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * An optional app-only PIN, for phones without a screen lock or people who want a second one. Only a salted
 * PBKDF2 hash is kept. After [FREE_TRIES] wrong tries each further one locks the pad for longer, up to [MAX_WAIT_MS].
 */
object Pin {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 6
    const val FREE_TRIES = 5
    private const val FIRST_WAIT_MS = 30_000L
    const val MAX_WAIT_MS = 15 * 60_000L
    private const val ITERATIONS = 120_000
    private val random = SecureRandom()

    fun valid(pin: String): Boolean = pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

    fun newSalt(): String = Base64.getEncoder().encodeToString(ByteArray(16).also(random::nextBytes))

    fun hash(pin: String, salt: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), Base64.getDecoder().decode(salt), ITERATIONS, 256)
        try {
            return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded)
        } finally {
            spec.clearPassword()
        }
    }

    /** Constant-time comparison, so timing says nothing about how close a guess was. */
    fun matches(pin: String, hash: String, salt: String): Boolean =
        valid(pin) && MessageDigest.isEqual(hash(pin, salt).toByteArray(), hash.toByteArray())

    /** How long to wait after [failures] wrong tries in a row: nothing for the first few, then 30 s doubling each time. */
    fun waitAfter(failures: Int): Long {
        if (failures < FREE_TRIES) return 0
        val doublings = (failures - FREE_TRIES).coerceAtMost(10)
        return (FIRST_WAIT_MS shl doublings).coerceAtMost(MAX_WAIT_MS)
    }
}
