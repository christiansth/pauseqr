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
 * Format (Version 2, zusätzlich passwortgeschützt):
 * {"type":"scanoff","version":2,"username":"...","createdAt":169...,"id":"<uuid>",
 *  "pwd":{"kdf":"PBKDF2WithHmacSHA256","iterations":210000,"salt":"<base64>","hash":"<base64>"}}
 *
 * Die UUID in [QrCodeRecord.id] ist die maßgebliche eindeutige Kennung. Für die Autorisierung
 * (siehe [Prefs.verifyScan]) wird ausschließlich diese ID verglichen – Benutzername und
 * Zeitstempel sind reine Anzeige-Metadaten und begründen allein keine Berechtigung. Das optionale
 * "pwd"-Feld (ab Version 2 Pflicht) dient ausschließlich der Passwortprüfung beim Identitäts-
 * Import/Backup (siehe [PasswordCheckUtil]) – es hat keinen Einfluss auf die normale
 * QR-Code-Entsperrung, die weiterhin ausschließlich die ID vergleicht.
 */
object QrPayload {
    const val TYPE = "scanoff"
    const val MIN_SUPPORTED_VERSION = 1
    const val CURRENT_VERSION = 2
    const val PASSWORD_PROTECTED_VERSION = 2

    fun encode(record: QrCodeRecord): String = JSONObject().apply {
        put("type", TYPE)
        put("version", record.version)
        put("username", record.username)
        put("createdAt", record.createdAt)
        put("id", record.id)
        record.passwordCheck?.let { pwd ->
            put("pwd", JSONObject().apply {
                put("kdf", pwd.kdf)
                put("iterations", pwd.iterations)
                put("salt", pwd.salt)
                put("hash", pwd.hash)
            })
        }
    }.toString()

    /**
     * Liefert null bei unbekanntem/beschädigtem Payload (kein gültiges ScanOff-Format). Prüft
     * dabei `type`, unterstützte `version`, eine syntaktisch gültige UUID in `id`, einen nicht
     * leeren `username`, einen plausiblen `createdAt`-Zeitstempel und – ab Version 2 verpflichtend
     * – vollständige Passwortprüfdaten. Die Kennung dient allein der Formaterkennung – sie ist
     * kein kryptografischer Echtheitsnachweis für die Identität selbst (das übernimmt bei Bedarf
     * erst [PasswordCheckUtil.verify]).
     */
    fun decode(payload: String): QrCodeRecord? {
        return try {
            val json = JSONObject(payload)
            if (json.optString("type") != TYPE) return null
            val version = json.optInt("version", -1)
            if (version < MIN_SUPPORTED_VERSION || version > CURRENT_VERSION) return null

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

            val passwordCheck = if (version >= PASSWORD_PROTECTED_VERSION) {
                decodePasswordCheck(json.optJSONObject("pwd")) ?: return null
            } else {
                null
            }

            QrCodeRecord(id = id, username = username, createdAt = createdAt, version = version, passwordCheck = passwordCheck)
        } catch (e: JSONException) {
            null
        }
    }

    private fun decodePasswordCheck(json: JSONObject?): PasswordCheck? {
        if (json == null) return null
        val kdf = json.optString("kdf", "")
        val iterations = json.optInt("iterations", -1)
        val salt = json.optString("salt", "")
        val hash = json.optString("hash", "")
        if (kdf.isBlank() || iterations <= 0 || salt.isBlank() || hash.isBlank()) return null
        return PasswordCheck(kdf = kdf, iterations = iterations, salt = salt, hash = hash)
    }
}
