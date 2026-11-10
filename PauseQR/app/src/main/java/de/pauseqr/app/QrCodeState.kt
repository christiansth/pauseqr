package de.pauseqr.app

/**
 * Zustand des persönlichen ScanOff-Codes, wie er aus dem Speicher gelesen wurde.
 *
 * [Empty] bedeutet ausdrücklich "kein gültiger Code" – weder ein nie erstellter Code noch ein
 * erkannter Platzhalter (siehe [Prefs.loadQrCodeState]) rechtfertigen eine automatische
 * Neuerstellung. [Error] bedeutet, dass gespeicherte Daten vorhanden, aber nicht lesbar sind;
 * in diesem Fall werden die Daten NICHT gelöscht oder ersetzt.
 */
sealed class QrCodeState {
    object Empty : QrCodeState()
    data class Existing(val record: QrCodeRecord) : QrCodeState()
    data class Error(val reason: String) : QrCodeState()
}
