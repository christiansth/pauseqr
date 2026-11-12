package de.pauseqr.app

/**
 * Ein einzelner, erkannter Versuch, eine gesperrte App während einer aktiven Pause zu öffnen.
 * Enthält bewusst ausschließlich den Paketnamen und den Zeitpunkt – keine Bildschirminhalte, keine
 * Texteingaben, keine Nachrichten. [pauseSessionId] verknüpft den Versuch eindeutig mit der
 * zugehörigen Pause (siehe [PauseSession]).
 */
data class BlockedAppAttempt(
    val id: String,
    val pauseSessionId: String,
    val packageName: String,
    val timestamp: Long
)
