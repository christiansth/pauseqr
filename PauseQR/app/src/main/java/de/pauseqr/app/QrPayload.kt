package de.pauseqr.app

import org.json.JSONException
import org.json.JSONObject

/**
 * Codiert/decodiert das strukturierte, versionierte QR-Code-Payload.
 *
 * Format (Version 1):
 * {"type":"scanoff","version":1,"username":"...","createdAt":169...,"id":"<uuid>"}
 *
 * Die UUID in [QrCodeRecord.id] ist die maßgebliche eindeutige Kennung. Für die Autorisierung
 * (siehe [Prefs.verifyScan]) wird ausschließlich diese ID verglichen – Benutzername und
 * Zeitstempel sind reine Anzeige-Metadaten und begründen allein keine Berechtigung.
 */
object QrPayload {
    const val TYPE = "scanoff"
    const val CURRENT_VERSION = 1

    fun encode(record: QrCodeRecord): String = JSONObject().apply {
        put("type", TYPE)
        put("version", record.version)
        put("username", record.username)
        put("createdAt", record.createdAt)
        put("id", record.id)
    }.toString()

    /** Liefert null bei unbekanntem/beschädigtem Payload (kein gültiges ScanOff-Format). */
    fun decode(payload: String): QrCodeRecord? {
        return try {
            val json = JSONObject(payload)
            if (json.optString("type") != TYPE) return null
            val version = json.optInt("version", -1)
            if (version < 1 || version > CURRENT_VERSION) return null
            val id = json.optString("id", "")
            if (id.isBlank()) return null
            QrCodeRecord(
                id = id,
                username = json.optString("username", ""),
                createdAt = json.optLong("createdAt", 0L),
                version = version
            )
        } catch (e: JSONException) {
            null
        }
    }
}
