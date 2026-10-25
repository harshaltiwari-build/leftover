package app.leftover.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {
    private val fast = 2_000
    private val text = "{\"format\":\"leftover-backup\",\"note\":\"₹1,200 · Blue Tokai\"}"

    @Test fun aPassphraseOpensWhatItSealed() {
        val sealed = BackupCrypto.encrypt(text, "correct horse".toCharArray(), fast)
        assertEquals(text, BackupCrypto.decrypt(sealed, "correct horse".toCharArray()))
    }

    @Test fun theSealedFileHidesTheContents() {
        val sealed = BackupCrypto.encrypt(text, "correct horse".toCharArray(), fast)
        assertFalse(sealed.contains("Blue Tokai"))
        assertTrue(BackupCrypto.looksEncrypted(sealed))
        assertFalse(BackupCrypto.looksEncrypted(text))
        assertFalse(BackupCrypto.looksEncrypted("not json"))
    }

    @Test fun aWrongPassphraseIsRefused() {
        val sealed = BackupCrypto.encrypt(text, "correct horse".toCharArray(), fast)
        assertThrows(WrongPassphraseException::class.java) { BackupCrypto.decrypt(sealed, "wrong".toCharArray()) }
    }

    @Test fun anyChangeToTheFileIsDetected() {
        val sealed = BackupCrypto.encrypt(text, "correct horse".toCharArray(), fast)
        val data = Regex("\"data\":\"([^\"]+)\"").find(sealed)!!.groupValues[1]
        val flipped = data.replaceRange(0, 1, if (data[0] == 'A') "B" else "A")
        assertThrows(WrongPassphraseException::class.java) { BackupCrypto.decrypt(sealed.replace(data, flipped), "correct horse".toCharArray()) }
    }

    @Test fun theHeaderCannotBeSwappedForAWeakerOne() {
        val sealed = BackupCrypto.encrypt(text, "correct horse".toCharArray(), fast)
        val weaker = sealed.replace("\"iterations\":$fast", "\"iterations\":1000")
        assertThrows(WrongPassphraseException::class.java) { BackupCrypto.decrypt(weaker, "correct horse".toCharArray()) }
    }

    @Test fun everySealingUsesAFreshNonce() {
        val key = BackupCrypto.deriveKey("pw12345678".toCharArray(), BackupCrypto.newSalt(), fast)
        val salt = BackupCrypto.newSalt()
        assertNotEquals(BackupCrypto.encrypt(text, key, salt, fast), BackupCrypto.encrypt(text, key, salt, fast))
    }

    @Test fun aStoredKeyCanSealAndThePassphraseStillOpens() {
        val salt = BackupCrypto.newSalt()
        val key = BackupCrypto.deriveKey("my passphrase".toCharArray(), salt, fast)
        val sealed = BackupCrypto.encrypt(text, key, salt, fast)
        assertEquals(text, BackupCrypto.decrypt(sealed, "my passphrase".toCharArray()))
    }

    @Test fun notABackupAtAllGivesAClearMessage() {
        assertThrows(BackupException::class.java) { BackupCrypto.decrypt("hello", "x".toCharArray()) }
        assertThrows(BackupException::class.java) { BackupCrypto.decrypt("{\"format\":\"other\"}", "x".toCharArray()) }
    }
}
