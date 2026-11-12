package de.pauseqr.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Ergebnis eines Versuchs, einen gescannten/importierten Code als Freund zu speichern. */
enum class AddFriendResult { SAVED, DUPLICATE, OWN_CODE, INVALID }

/**
 * Dauerhafte, lokale Speicherung von ScanOff-Freunden (keine automatische Cloud-Synchronisierung).
 * Nutzt denselben SharedPreferences-Mechanismus wie [Prefs] – konsistent mit der bestehenden
 * Architektur, ohne zusätzliche Bibliotheken (z. B. Room).
 */
object FriendsStore {
    private const val FILE = "pause_qr"
    private const val KEY_FRIENDS = "scanoff_friends_json"

    private fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Neueste zuerst (nach Hinzufügezeitpunkt). */
    fun getFriends(c: Context): List<ScanOffFriend> {
        val raw = sp(c).getString(KEY_FRIENDS, null) ?: return emptyList()
        val list = try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i -> decodeFriend(array.optJSONObject(i)) }
        } catch (e: JSONException) {
            emptyList()
        }
        return list.sortedByDescending { it.addedAt }
    }

    fun findById(c: Context, id: String): ScanOffFriend? = getFriends(c).find { it.id == id }

    /**
     * Fügt einen aus [payload] decodierten Code als Freund hinzu. Lehnt ungültige Codes, den
     * eigenen Code und bereits gespeicherte UUIDs ab – bestehende Freunde werden dabei nie
     * überschrieben.
     */
    fun addFriend(c: Context, payload: String): AddFriendResult {
        val decoded = QrPayload.decode(payload) ?: return AddFriendResult.INVALID

        val own = Prefs.getQrCode(c)
        if (own != null && own.version != 0 && own.id == decoded.id) return AddFriendResult.OWN_CODE

        val current = getFriends(c)
        if (current.any { it.id == decoded.id }) return AddFriendResult.DUPLICATE

        val friend = ScanOffFriend(
            id = decoded.id,
            username = decoded.username,
            createdAt = decoded.createdAt,
            addedAt = System.currentTimeMillis(),
            qrPayload = payload,
            unlockEnabled = false
        )
        saveFriends(c, current + friend)
        return AddFriendResult.SAVED
    }

    /** Ändert die Entsperrfreigabe sofort und dauerhaft. */
    fun setUnlockEnabled(c: Context, id: String, enabled: Boolean) {
        val updated = getFriends(c).map { if (it.id == id) it.copy(unlockEnabled = enabled) else it }
        saveFriends(c, updated)
    }

    fun removeFriend(c: Context, id: String) {
        saveFriends(c, getFriends(c).filterNot { it.id == id })
    }

    private fun saveFriends(c: Context, friends: List<ScanOffFriend>) {
        sp(c).edit().putString(KEY_FRIENDS, encodeFriendsJson(friends)).apply()
    }

    private fun encodeFriendsJson(friends: List<ScanOffFriend>): String {
        val array = JSONArray()
        friends.forEach { array.put(encodeFriend(it)) }
        return array.toString()
    }

    /**
     * Schreibt [friends] in den übergebenen, noch nicht committeten [editor] – für den atomaren
     * Restore-Commit in [Prefs.restoreSnapshot]. Ruft selbst niemals `apply()` auf.
     */
    fun putFriends(editor: SharedPreferences.Editor, friends: List<ScanOffFriend>): SharedPreferences.Editor =
        editor.putString(KEY_FRIENDS, encodeFriendsJson(friends))

    private fun encodeFriend(f: ScanOffFriend): JSONObject = JSONObject().apply {
        put("id", f.id)
        put("username", f.username)
        put("createdAt", f.createdAt)
        put("addedAt", f.addedAt)
        put("qrPayload", f.qrPayload)
        put("unlockEnabled", f.unlockEnabled)
    }

    private fun decodeFriend(json: JSONObject?): ScanOffFriend? {
        if (json == null) return null
        val id = json.optString("id", "")
        val username = json.optString("username", "")
        val qrPayload = json.optString("qrPayload", "")
        if (id.isBlank() || username.isBlank() || qrPayload.isBlank()) return null
        return ScanOffFriend(
            id = id,
            username = username,
            createdAt = json.optLong("createdAt", 0L),
            addedAt = json.optLong("addedAt", 0L),
            qrPayload = qrPayload,
            unlockEnabled = json.optBoolean("unlockEnabled", false)
        )
    }
}
