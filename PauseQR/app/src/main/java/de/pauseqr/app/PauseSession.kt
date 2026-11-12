package de.pauseqr.app

/**
 * Eine einzelne ScanOff-Pause. [endTime]/[endReason] sind null, solange die Pause noch läuft;
 * [completed] wird erst beim tatsächlichen Abschluss wahr – eine laufende Pause zählt nie in den
 * aggregierten Statistiken (siehe [StatsEngine]) und wird in der Historie separat gekennzeichnet.
 */
data class PauseSession(
    val id: String,
    val startTime: Long,
    val endTime: Long?,
    val endReason: PauseEndReason?,
    val completed: Boolean
) {
    /** Dauer in Millisekunden; 0 für eine noch laufende Pause. */
    val durationMillis: Long
        get() = if (completed && endTime != null) (endTime - startTime).coerceAtLeast(0L) else 0L
}
