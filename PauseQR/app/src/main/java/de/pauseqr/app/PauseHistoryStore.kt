package de.pauseqr.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Dauerhafte, lokale Speicherung der Pausenhistorie (keine Cloud-Anbindung, kein Tracking).
 * Nutzt denselben SharedPreferences-Mechanismus wie [Prefs]/[FriendsStore] – konsistent mit der
 * bestehenden Architektur, ohne zusätzliche Bibliotheken (z. B. Room).
 *
 * Jede Pause wird genau einmal gespeichert: [startSession] legt beim Pausenstart einen neuen,
 * noch nicht abgeschlossenen Eintrag an; [completeOpenSession] schließt genau diesen Eintrag beim
 * Beenden ab. Historische Einträge werden nie nachträglich erfunden oder verändert – nur ergänzt.
 */
object PauseHistoryStore {
    private const val FILE = "pause_qr"
    private const val KEY_SESSIONS = "pause_sessions_json"

    private fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Alle gespeicherten Pausen, chronologisch nach Startzeit (älteste zuerst). */
    fun getSessions(c: Context): List<PauseSession> {
        val raw = sp(c).getString(KEY_SESSIONS, null) ?: return emptyList()
        val list = try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i -> decodeSession(array.optJSONObject(i)) }
        } catch (e: JSONException) {
            emptyList()
        }
        return list.sortedBy { it.startTime }
    }

    /**
     * Legt beim Pausenstart einen neuen, offenen Eintrag an. Existiert bereits ein offener
     * Eintrag (z. B. nach einem App-Absturz während einer laufenden Pause), wird kein zweiter
     * erzeugt – jede Pause bleibt eindeutig.
     */
    fun startSession(c: Context) {
        val current = getSessions(c)
        if (current.any { !it.completed }) return

        val session = PauseSession(
            id = java.util.UUID.randomUUID().toString(),
            startTime = System.currentTimeMillis(),
            endTime = null,
            endReason = null,
            completed = false
        )
        saveSessions(c, current + session)
    }

    /** Schließt den aktuell offenen Eintrag ab. Ohne offenen Eintrag passiert nichts. */
    fun completeOpenSession(c: Context, reason: PauseEndReason) {
        val current = getSessions(c)
        val openIndex = current.indexOfLast { !it.completed }
        if (openIndex < 0) return

        val now = System.currentTimeMillis()
        val open = current[openIndex]
        val closed = open.copy(
            endTime = now.coerceAtLeast(open.startTime),
            endReason = reason,
            completed = true
        )
        saveSessions(c, current.toMutableList().also { it[openIndex] = closed })
    }

    /** Entfernt ausschließlich die Pausenhistorie (siehe „Statistiken zurücksetzen“). */
    fun clearAll(c: Context) {
        sp(c).edit().remove(KEY_SESSIONS).apply()
    }

    private fun saveSessions(c: Context, sessions: List<PauseSession>) {
        sp(c).edit().putString(KEY_SESSIONS, encodeSessionsJson(sessions)).apply()
    }

    private fun encodeSessionsJson(sessions: List<PauseSession>): String {
        val array = JSONArray()
        sessions.forEach { array.put(encodeSession(it)) }
        return array.toString()
    }

    /**
     * Schreibt [sessions] in den übergebenen, noch nicht committeten [editor] – für den atomaren
     * Restore-Commit in [Prefs.restoreSnapshot]. Ruft selbst niemals `apply()` auf.
     */
    fun putSessions(editor: SharedPreferences.Editor, sessions: List<PauseSession>): SharedPreferences.Editor =
        editor.putString(KEY_SESSIONS, encodeSessionsJson(sessions))

    private fun encodeSession(s: PauseSession): JSONObject = JSONObject().apply {
        put("id", s.id)
        put("startTime", s.startTime)
        put("endTime", s.endTime ?: JSONObject.NULL)
        put("endReason", s.endReason?.name ?: JSONObject.NULL)
        put("completed", s.completed)
    }

    private fun decodeSession(json: JSONObject?): PauseSession? {
        if (json == null) return null
        val id = json.optString("id", "")
        val startTime = json.optLong("startTime", -1L)
        if (id.isBlank() || startTime <= 0) return null

        val endTime = if (json.isNull("endTime")) null else json.optLong("endTime", -1L).takeIf { it >= 0 }
        val reasonRaw = if (json.isNull("endReason")) null else json.optString("endReason", "")
        val endReason = reasonRaw?.let {
            try {
                PauseEndReason.valueOf(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
        val completed = json.optBoolean("completed", false)

        // Konsistenzprüfung: eine als abgeschlossen markierte Pause braucht ein gültiges Ende,
        // das nicht vor dem Start liegt – sonst als unlesbar verwerfen, statt falsche Werte zu zeigen.
        if (completed && (endTime == null || endTime < startTime)) return null

        return PauseSession(
            id = id,
            startTime = startTime,
            endTime = endTime,
            endReason = endReason,
            completed = completed
        )
    }
}
