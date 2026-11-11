package de.pauseqr.app

/**
 * Wie eine Pause beendet wurde. [EMERGENCY] zählt bewusst nicht automatisch als erfolgreicher
 * Streak-Tag (siehe [StatsEngine]) – die Notfall-Entsperrung ist eine zugängliche Ausweichoption,
 * kein regulärer Abschluss. [AUTOMATIC] ist für eine mögliche künftige automatische Beendigung
 * vorgesehen und wird aktuell nirgends ausgelöst.
 */
enum class PauseEndReason {
    QR_CODE,
    EMERGENCY,
    AUTOMATIC
}
