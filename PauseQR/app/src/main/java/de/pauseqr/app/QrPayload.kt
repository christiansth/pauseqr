package de.pauseqr.app

import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

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

    /**
     * Liefert null bei unbekanntem/beschädigtem Payload (kein gültiges ScanOff-Format). Prüft
     * dabei `type`, unterstützte `version`, eine syntaktisch gültige UUID in `id`, einen nicht
     * leeren `username` und einen plausiblen `createdAt`-Zeitstempel. Die Kennung dient allein
     * der Formaterkennung – sie ist kein kryptografischer Echtheitsnachweis.
     */
    fun decode(payload: String): QrCodeRecord? {
        return try {
            val json = JSONObject(payload)
            if (json.optString("type") != TYPE) return null
            val version = json.optInt("version", -1)
            if (version < 1 || version > CURRENT_VERSION) return null

            val id = json.optString("id", "")
            if (id.isBlank()) return null
            try {
                UUID.fromString(id)
            } catch (e: IllegalArgumentException) {
                return null
            }

            val username = json.optString("username", "")
            if (username.isBlank()) return null

            val createdAt = json.optLong("createdAt", -1L)
            if (createdAt <= 0) return null

            QrCodeRecord(id = id, username = username, createdAt = createdAt, version = version)
        } catch (e: JSONException) {
            null
        }
    }
}
