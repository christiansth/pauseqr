package de.pauseqr.app

import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast

/**
 * Zeigt nach einer erfolgreichen Entsperrung (QR-Code oder Notfall) optional eine rein
 * informative Zusammenfassung der während der Pause blockierten App-Aufrufe – ohne Wertung,
 * ohne die eigentliche Entsperrung zu beeinflussen. Wird von [DashboardFragment] (QR-Entsperrung)
 * und [EmergencyUnlockFragment] (Notfall-Entsperrung) gleichermaßen verwendet.
 */
object PauseSummaryHelper {

    /** [sessionId] muss VOR dem Aufruf von [Prefs.unlock] gelesen werden (siehe Aufrufer). */
    fun showSummaryIfAny(context: Context, sessionId: String?) {
        if (sessionId == null) return
        val summary = BlockedAttemptStore.summarizeSession(context, sessionId) ?: return

        val message = StringBuilder(
            context.resources.getQuantityString(R.plurals.pause_summary_attempts, summary.totalCount, summary.totalCount)
        )
        val topPackage = summary.topPackage
        if (topPackage != null) {
            val label = try {
                context.packageManager.getApplicationInfo(topPackage, 0).loadLabel(context.packageManager).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                topPackage
            }
            message.append("\n").append(
                context.resources.getQuantityString(R.plurals.pause_summary_top_app, summary.topCount, label, summary.topCount)
            )
        }
        Toast.makeText(context, message.toString(), Toast.LENGTH_LONG).show()
    }
}
