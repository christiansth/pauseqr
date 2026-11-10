package de.pauseqr.app

/** Sicherungsstatus des aktuell gültigen ScanOff-Codes. Ist immer an eine konkrete QR-Code-ID gebunden. */
enum class QrBackupStatus {
    NOT_BACKED_UP,
    SAVED_TO_DEVICE,
    USER_CONFIRMED_SHARED
}
