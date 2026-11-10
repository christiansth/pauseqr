package de.pauseqr.app

import android.content.Context

/**
 * Einzige, zentrale Quelle der Wahrheit dafür, ob eine Pause gestartet werden darf. Jeder
 * Einstiegspunkt, der eine Pause startet, muss [canStartPause] aufrufen – unabhängig davon, ob
 * der Start über das Dashboard oder einen anderen Weg ausgelöst wird. Die Benutzeroberfläche
 * (deaktivierter Button) ist lediglich eine Spiegelung dieser Prüfung, nicht ihre einzige Quelle.
 */
object PauseGate {
    fun canStartPause(
        hasValidQrCode: Boolean,
        isQrBackedUp: Boolean,
        accessibilityEnabled: Boolean,
        selectedAppsCount: Int
    ): Boolean {
        return hasValidQrCode &&
            isQrBackedUp &&
            accessibilityEnabled &&
            selectedAppsCount > 0
    }

    fun canStartPause(context: Context): Boolean = canStartPause(
        hasValidQrCode = Prefs.getQrCode(context) != null,
        isQrBackedUp = Prefs.isQrBackedUp(context),
        accessibilityEnabled = ServiceUtil.isBlockerServiceEnabled(context),
        selectedAppsCount = Prefs.getBlockedApps(context).size
    )
}
