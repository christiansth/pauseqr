package de.pauseqr.app

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/** Speichert geheimen QR-Inhalt, Sperrstatus und die Liste der blockierten Apps. */
object Prefs {
    private const val FILE = "pause_qr"
    private const val KEY_SECRET = "secret"
    private const val KEY_LOCKED = "locked"
    private const val KEY_APPS = "blocked_apps"
    private const val PREFIX = "PAUSEQR:"

    private fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Inhalt des QR-Codes. Wird beim ersten Start zufällig erzeugt. */
    fun getSecret(c: Context): String {
        sp(c).getString(KEY_SECRET, null)?.let { return it }
        return newSecret(c)
    }

    /** Erzeugt einen neuen QR-Code – der alte ist danach ungültig. */
    fun newSecret(c: Context): String {
        val s = PREFIX + UUID.randomUUID().toString()
        sp(c).edit().putString(KEY_SECRET, s).apply()
        return s
    }

    fun isLocked(c: Context): Boolean = sp(c).getBoolean(KEY_LOCKED, false)

    fun setLocked(c: Context, locked: Boolean) {
        sp(c).edit().putBoolean(KEY_LOCKED, locked).apply()
    }

    fun getBlockedApps(c: Context): Set<String> =
        sp(c).getStringSet(KEY_APPS, emptySet())?.toSet() ?: emptySet()

    fun setBlockedApps(c: Context, apps: Set<String>) {
        sp(c).edit().putStringSet(KEY_APPS, HashSet(apps)).apply()
    }
}
