package de.pauseqr.app

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.appcompat.app.AlertDialog

object ServiceUtil {
    fun isBlockerServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = ComponentName(context, BlockerService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    /**
     * Zeigt vor jeder Weiterleitung zu den System-Bedienungshilfen einen eigenständigen
     * Informations- und Zustimmungsdialog (getrennt von der Datenschutzerklärung). Nur nach
     * ausdrücklicher Bestätigung wird [onProceed] aufgerufen.
     */
    fun showAccessibilityConsent(context: Context, onProceed: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle(R.string.accessibility_consent_title)
            .setMessage(R.string.accessibility_consent_message)
            .setPositiveButton(R.string.accessibility_consent_confirm) { _, _ -> onProceed() }
            .setNegativeButton(R.string.accessibility_consent_cancel, null)
            .show()
    }
}
