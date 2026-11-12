package de.pauseqr.app

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Leitet aus dem ScanOff-Passwort einen Prüfwert ab, der im QR-Code gespeichert werden kann, ohne
 * das Passwort selbst preiszugeben – über PBKDF2-HMAC-SHA256 (etablierte, gut dokumentierte
 * Java-Standard-API, kein eigener Kryptografiealgorithmus), mit einem zufälligen Salt pro
 * Identität und einem ausreichend hohen Arbeitsfaktor.
 *
 * Bewusst getrennt von [BackupCrypto]: der hier erzeugte Prüfwert und ein aus demselben Passwort
 * für die Backup-Verschlüsselung abgeleiteter Schlüssel verwenden unterschiedliche, jeweils
 * eigene zufällige Salts und sind damit kryptografisch unabhängig voneinander – auch bei
 * identischem Passwort entsteht nie derselbe abgeleitete Wert für beide Zwecke.
 *
 * Sicherheitshinweis: Ein öffentlich lesbarer QR-Code mit Passwortprüfwert erlaubt grundsätzlich
 * Offline-Rateversuche gegen diesen Wert. Diese Konstruktion ist kein vollständiger Schutz gegen
 * Brute-Force-Angriffe.
 */
object PasswordCheckUtil {
    const val KDF_NAME = "PBKDF2WithHmacSHA256"
    const val ITERATIONS = 210_000
    private const val SALT_LENGTH_BYTES = 16
    private const val HASH_LENGTH_BITS = 256
    private const val MAX_ITERATIONS = 2_000_000

    const val MIN_PASSWORD_LENGTH = 12
    const val MAX_PASSWORD_LENGTH = 256

    /** Erzeugt frische Passwortprüfdaten für ein neu gesetztes oder geändertes Passwort. */
    fun create(password: CharArray): PasswordCheck {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = derive(password, salt, ITERATIONS)
        return PasswordCheck(
            kdf = KDF_NAME,
            iterations = ITERATIONS,
            salt = Base64.getEncoder().encodeToString(salt),
            hash = Base64.getEncoder().encodeToString(hash)
        )
    }

    /** Prüft [password] zeitkonstant gegen [check]; liefert bei defekten/unbekannten Daten sicher `false`. */
    fun verify(password: CharArray, check: PasswordCheck): Boolean {
        if (check.kdf != KDF_NAME) return false
        val iterations = check.iterations
        if (iterations <= 0 || iterations > MAX_ITERATIONS) return false

        val salt = try {
            Base64.getDecoder().decode(check.salt)
        } catch (e: IllegalArgumentException) {
            return false
        }
        val expected = try {
            Base64.getDecoder().decode(check.hash)
        } catch (e: IllegalArgumentException) {
            return false
        }

        val actual = derive(password, salt, iterations)
        return MessageDigest.isEqual(actual, expected)
    }

    private fun derive(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, HASH_LENGTH_BITS)
        try {
            return SecretKeyFactory.getInstance(KDF_NAME).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
