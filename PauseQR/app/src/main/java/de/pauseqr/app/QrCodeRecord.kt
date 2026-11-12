package de.pauseqr.app

/**
 * Kryptografische Passwortprüfdaten einer Version-2-Identität. Enthält niemals das Passwort
 * selbst – nur einen abgeleiteten Prüfwert, mit dem ein später eingegebenes Passwort verifiziert
 * werden kann (siehe [PasswordCheckUtil]). Ein öffentlich lesbarer QR-Code mit Prüfwert erlaubt
 * grundsätzlich Offline-Rateversuche gegen diesen Wert; das ist kein vollständiger Schutz gegen
 * Brute-Force-Angriffe, sondern verhindert lediglich, dass das Passwort selbst sichtbar wird.
 */
data class PasswordCheck(
    val kdf: String,
    val iterations: Int,
    val salt: String,
    val hash: String
)

/**
 * Ein persönlicher ScanOff-Code.
 *
 * [version] 0 markiert einen vor dem strukturierten Format erzeugten Code (reiner
 * "PAUSEQR:<uuid>"-String). [version] 1 ist das strukturierte Format ohne Passwortschutz. Beide
 * bleiben gültig, bis der Nutzer ausdrücklich einen neuen Code erstellt oder ein Passwort
 * einrichtet – sie werden nie automatisch verändert oder gelöscht.
 * [version] 2 verlangt ein gesetztes [passwordCheck] (siehe [QrPayload]).
 */
data class QrCodeRecord(
    val id: String,
    val username: String,
    val createdAt: Long,
    val version: Int,
    val passwordCheck: PasswordCheck? = null
)
