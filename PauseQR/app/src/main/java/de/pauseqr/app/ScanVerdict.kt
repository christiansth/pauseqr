package de.pauseqr.app

/**
 * Ergebnis der Prüfung eines gescannten QR-Codes gegen den eigenen Code und die gespeicherten
 * ScanOff-Freunde (siehe [Prefs.verifyScan]). Ein Freund-Code autorisiert nur den gespeicherten,
 * eindeutigen Code – niemals allein den Benutzernamen oder Zeitstempel.
 */
sealed class ScanVerdict {
    /** Entspricht dem eigenen, gültigen persönlichen ScanOff-Code. */
    object OwnCodeMatch : ScanVerdict()

    /** Entspricht einem gespeicherten Freund mit aktivierter Entsperrfreigabe. */
    data class FriendMatch(val friendUsername: String) : ScanVerdict()

    /** Entspricht einem gespeicherten Freund, dessen Entsperrfreigabe deaktiviert ist. */
    object FriendDisabled : ScanVerdict()

    /** Strukturell gültiger ScanOff-Code, aber weder der eigene Code noch ein gespeicherter Freund. */
    object Unrecognized : ScanVerdict()

    /** Kein gültiger ScanOff-Code (kein erkennbares Format bzw. beschädigte Payload). */
    object Invalid : ScanVerdict()
}
