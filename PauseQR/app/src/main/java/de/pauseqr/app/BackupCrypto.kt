package de.pauseqr.app

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM mit PBKDF2-HMAC-SHA256-Schlüsselableitung für verschlüsselte ScanOff-Backups.
 * Der Schlüssel entsteht ausschließlich aus dem vom Nutzer vergebenen Backup-Passwort – kein fest
 * hinterlegter und kein gerätegebundener Schlüssel, damit die Datei auf einem anderen Smartphone
 * entschlüsselbar bleibt. Der GCM-Authentifizierungs-Tag prüft Integrität und Authentizität:
 * ein falsches Passwort oder eine manipulierte Datei führen zu einer Exception, nie zu
 * stillschweigend falschen Daten.
 */
object BackupCrypto {
    const val KDF_NAME = "PBKDF2WithHmacSHA256"
    const val KDF_ITERATIONS = 210_000
    private const val KEY_LENGTH_BITS = 256
    const val SALT_LENGTH_BYTES = 16
    const val NONCE_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128

    fun randomSalt(): ByteArray = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
    fun randomNonce(): ByteArray = ByteArray(NONCE_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS)
        try {
            val keyBytes = SecretKeyFactory.getInstance(KDF_NAME).generateSecret(spec).encoded
            return SecretKeySpec(keyBytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun encrypt(plaintext: ByteArray, password: CharArray, salt: ByteArray, nonce: ByteArray, iterations: Int): ByteArray {
        val key = deriveKey(password, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce))
        return cipher.doFinal(plaintext)
    }

    /** Wirft bei falschem Passwort oder manipulierten Daten eine Exception (Auth-Tag-Prüfung). */
    fun decrypt(ciphertext: ByteArray, password: CharArray, salt: ByteArray, nonce: ByteArray, iterations: Int): ByteArray {
        val key = deriveKey(password, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce))
        return cipher.doFinal(ciphertext)
    }
}
