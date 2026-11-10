package de.pauseqr.app

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/** Ergebnis der Prüfung eines gescannten QR-Codes gegen den gespeicherten ScanOff-Code. */
enum class ScanVerdict { MATCH, WRONG_CODE, INVALID }

/** Speichert den persönlichen ScanOff-Code, Sperrstatus und die Liste der blockierten Apps. */
object Prefs {
    private const val FILE = "pause_qr"
    private const val KEY_LOCKED = "locked"
    private const val KEY_APPS = "blocked_apps"
    private const val KEY_PAUSE_COUNT = "pause_count"
    private const val KEY_QR_RECORD = "qr_record_json"

    // Legacy-Schlüssel aus Versionen vor dem strukturierten QR-Format.
    private const val KEY_LEGACY_SECRET = "secret"
    private const val KEY_LEGACY_USERNAME = "username"
    private const val LEGACY_PREFIX = "PAUSEQR:"

    // Eindeutig identifizierte Platzhalter-Werte – ein Code mit einem dieser Benutzernamen gilt
    // nie als vorhanden, egal woher er stammt (Migration, früherer Default-Fallback usw.).
    private val PLACEHOLDER_USERNAMES = setOf("Unbekannt", "Unknown")

    const val USERNAME_MAX_LENGTH = 10

    private fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---------- QR-Code ----------

    private fun QrCodeRecord.isValid(): Boolean =
        id.isNotBlank() && username.isNotBlank() && username !in PLACEHOLDER_USERNAMES

    fun hasQrCode(c: Context): Boolean = loadQrCodeState(c) is QrCodeState.Existing

    /**
     * Liefert den aktuell gültigen ScanOff-Code oder null (kein Code bzw. Daten nicht lesbar).
     * Erzeugt NIE eine neue ID – reines Lesen verändert den gespeicherten Code nicht.
     */
    fun getQrCode(c: Context): QrCodeRecord? = (loadQrCodeState(c) as? QrCodeState.Existing)?.record

    /**
     * Liest den gespeicherten ScanOff-Code und unterscheidet dabei explizit:
     * - [QrCodeState.Existing]: gültige, vollständige Code-Daten (Benutzername gesetzt, nicht
     *   „Unbekannt“/Platzhalter, gültige ID).
     * - [QrCodeState.Empty]: nie erstellt – oder ein eindeutig identifizierter Platzhalter
     *   (z. B. leerer oder „Unbekannt“-Benutzername). Ein solcher Platzhalter wird dabei bereinigt,
     *   damit er nicht dauerhaft als „vorhanden“ missinterpretiert wird.
     * - [QrCodeState.Error]: gespeicherte Daten sind vorhanden, aber nicht lesbar (z. B. defektes
     *   JSON). Diese Daten werden NICHT automatisch gelöscht oder ersetzt.
     */
    fun loadQrCodeState(c: Context): QrCodeState {
        val rawJson = sp(c).getString(KEY_QR_RECORD, null)
        if (rawJson != null) {
            val decoded = QrPayload.decode(rawJson) ?: return QrCodeState.Error("corrupt_qr_record")
            if (!decoded.isValid()) {
                // Eindeutig identifizierter Platzhalter im neuen Format – bereinigen, nicht als Fehler behandeln.
                sp(c).edit().remove(KEY_QR_RECORD).apply()
                return QrCodeState.Empty
            }
            return QrCodeState.Existing(decoded)
        }

        // Migration: ein vor dem Update erzeugter Code bleibt gültig, bis bewusst ersetzt.
        val legacySecret = sp(c).getString(KEY_LEGACY_SECRET, null) ?: return QrCodeState.Empty
        val legacyUsername = sp(c).getString(KEY_LEGACY_USERNAME, "") ?: ""
        val legacyRecord = QrCodeRecord(id = legacySecret, username = legacyUsername, createdAt = 0L, version = 0)
        if (!legacyRecord.isValid()) {
            // Eindeutig identifizierter Platzhalter aus einer Altversion – nur diesen bereinigen.
            sp(c).edit().remove(KEY_LEGACY_SECRET).remove(KEY_LEGACY_USERNAME).apply()
            return QrCodeState.Empty
        }
        return QrCodeState.Existing(legacyRecord)
    }

    /** Erstellt und speichert einen neuen, eindeutigen ScanOff-Code. Ersetzt einen evtl. bestehenden. */
    fun createNewQrCode(c: Context, username: String): QrCodeRecord {
        val record = QrCodeRecord(
            id = UUID.randomUUID().toString(),
            username = username.trim().take(USERNAME_MAX_LENGTH),
            createdAt = System.currentTimeMillis(),
            version = QrPayload.CURRENT_VERSION
        )
        sp(c).edit()
            .putString(KEY_QR_RECORD, QrPayload.encode(record))
            .remove(KEY_LEGACY_SECRET)
            .remove(KEY_LEGACY_USERNAME)
            .apply()
        return record
    }

    /**
     * Prüft einen gescannten Text gegen den gespeicherten Code. Autorisierung erfolgt
     * ausschließlich über den Abgleich der eindeutigen ID – niemals über Benutzername oder
     * Zeitstempel allein.
     */
    fun verifyScan(c: Context, scannedText: String): ScanVerdict {
        val record = getQrCode(c) ?: return ScanVerdict.INVALID

        if (record.version == 0) {
            if (scannedText == record.id) return ScanVerdict.MATCH
            return if (scannedText.startsWith(LEGACY_PREFIX) || QrPayload.decode(scannedText) != null) {
                ScanVerdict.WRONG_CODE
            } else {
                ScanVerdict.INVALID
            }
        }

        val decoded = QrPayload.decode(scannedText)
            ?: return if (scannedText.startsWith(LEGACY_PREFIX)) ScanVerdict.WRONG_CODE else ScanVerdict.INVALID
        return if (decoded.id == record.id) ScanVerdict.MATCH else ScanVerdict.WRONG_CODE
    }

    // ---------- Sperrstatus ----------

    fun isLocked(c: Context): Boolean = sp(c).getBoolean(KEY_LOCKED, false)

    fun setLocked(c: Context, locked: Boolean) {
        sp(c).edit().putBoolean(KEY_LOCKED, locked).apply()
    }

    fun getBlockedApps(c: Context): Set<String> =
        sp(c).getStringSet(KEY_APPS, emptySet())?.toSet() ?: emptySet()

    fun setBlockedApps(c: Context, apps: Set<String>) {
        sp(c).edit().putStringSet(KEY_APPS, HashSet(apps)).apply()
    }

    // ---------- Statistik ----------

    fun getPauseCount(c: Context): Int = sp(c).getInt(KEY_PAUSE_COUNT, 0)

    fun incrementPauseCount(c: Context) {
        sp(c).edit().putInt(KEY_PAUSE_COUNT, getPauseCount(c) + 1).apply()
    }
}
