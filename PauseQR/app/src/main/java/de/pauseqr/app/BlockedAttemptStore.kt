package de.pauseqr.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

/**
 * Dauerhafte, lokale Speicherung erkannter Versuche, eine gesperrte App während einer aktiven
 * Pause zu öffnen. Nutzt denselben SharedPreferences-Mechanismus wie [Prefs]/[PauseHistoryStore] –
 * keine Cloud-Anbindung, kein Tracking-Dienst, keine Datenübertragung an externe Server.
 *
 * Die Erfassung kann in den Einstellungen deaktiviert werden ([isTrackingEnabled]); dabei bleiben
 * bereits gespeicherte Daten unangetastet – nur [recordAttempt] prüft das Flag.
 */
object BlockedAttemptStore {
    private const val FILE = "pause_qr"
    private const val KEY_ATTEMPTS = "blocked_attempts_json"
    private const val KEY_TRACKING_ENABLED = "blocked_attempts_tracking_enabled"

    data class SessionSummary(val totalCount: Int, val topPackage: String?, val topCount: Int)

    private fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Standardmäßig aktiviert (siehe Einstellungen „Gesperrte App-Aufrufe erfassen“). */
    fun isTrackingEnabled(c: Context): Boolean = sp(c).getBoolean(KEY_TRACKING_ENABLED, true)

    fun setTrackingEnabled(c: Context, enabled: Boolean) {
        sp(c).edit().putBoolean(KEY_TRACKING_ENABLED, enabled).apply()
    }

    fun getAttempts(c: Context): List<BlockedAppAttempt> {
        val raw = sp(c).getString(KEY_ATTEMPTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i -> decodeAttempt(array.optJSONObject(i)) }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    /**
     * Zeichnet einen einzelnen Öffnungsversuch auf. Wird nur von [BlockerService] aufgerufen,
     * nachdem dieser bereits zuverlässig einen tatsächlich NEUEN Versuch erkannt hat (siehe dort) –
     * diese Funktion selbst entscheidet nicht über Entprellung, nur über das Datenschutz-Flag.
     */
    fun recordAttempt(c: Context, pauseSessionId: String, packageName: String) {
        if (!isTrackingEnabled(c)) return
        val attempt = BlockedAppAttempt(
            id = UUID.randomUUID().toString(),
            pauseSessionId = pauseSessionId,
            packageName = packageName,
            timestamp = System.currentTimeMillis()
        )
        saveAttempts(c, getAttempts(c) + attempt)
    }

    /** Entfernt ausschließlich die Aufrufstatistik – Pausen, QR-Codes und App-Auswahl bleiben erhalten. */
    fun clearAll(c: Context) {
        sp(c).edit().remove(KEY_ATTEMPTS).apply()
    }

    /** Alle Versuche, die zur Pause [sessionId] gehören. */
    fun attemptsForSession(c: Context, sessionId: String): List<BlockedAppAttempt> =
        getAttempts(c).filter { it.pauseSessionId == sessionId }

    /** Kompakte Zusammenfassung für die Anzeige direkt nach Pausenende; null, wenn keine Versuche vorliegen. */
    fun summarizeSession(c: Context, sessionId: String): SessionSummary? {
        val attempts = attemptsForSession(c, sessionId)
        if (attempts.isEmpty()) return null
        val top = attempts.groupingBy { it.packageName }.eachCount().maxByOrNull { it.value }
        return SessionSummary(totalCount = attempts.size, topPackage = top?.key, topCount = top?.value ?: 0)
    }

    private fun saveAttempts(c: Context, attempts: List<BlockedAppAttempt>) {
        sp(c).edit().putString(KEY_ATTEMPTS, encodeAttemptsJson(attempts)).apply()
    }

    private fun encodeAttemptsJson(attempts: List<BlockedAppAttempt>): String {
        val array = JSONArray()
        attempts.forEach { array.put(encodeAttempt(it)) }
        return array.toString()
    }

    /**
     * Schreibt [attempts] in den übergebenen, noch nicht committeten [editor] – für den atomaren
     * Restore-Commit in [Prefs.restoreSnapshot]. Ruft selbst niemals `apply()` auf.
     */
    fun putAttempts(editor: SharedPreferences.Editor, attempts: List<BlockedAppAttempt>): SharedPreferences.Editor =
        editor.putString(KEY_ATTEMPTS, encodeAttemptsJson(attempts))

    private fun encodeAttempt(a: BlockedAppAttempt): JSONObject = JSONObject().apply {
        put("id", a.id)
        put("pauseSessionId", a.pauseSessionId)
        put("packageName", a.packageName)
        put("timestamp", a.timestamp)
    }

    private fun decodeAttempt(json: JSONObject?): BlockedAppAttempt? {
        if (json == null) return null
        val id = json.optString("id", "")
        val pauseSessionId = json.optString("pauseSessionId", "")
        val packageName = json.optString("packageName", "")
        val timestamp = json.optLong("timestamp", -1L)
        if (id.isBlank() || pauseSessionId.isBlank() || packageName.isBlank() || timestamp <= 0) return null
        return BlockedAppAttempt(id = id, pauseSessionId = pauseSessionId, packageName = packageName, timestamp = timestamp)
    }
}
