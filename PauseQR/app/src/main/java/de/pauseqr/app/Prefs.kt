package de.pauseqr.app

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/** Speichert den persönlichen ScanOff-Code, Sperrstatus und die Liste der blockierten Apps. */
object Prefs {
    private const val FILE = "pause_qr"
    private const val KEY_LOCKED = "locked"
    private const val KEY_APPS = "blocked_apps"
    private const val KEY_PAUSE_COUNT = "pause_count"
    private const val KEY_QR_RECORD = "qr_record_json"
    private const val KEY_BACKUP_STATUS = "qr_backup_status"
    private const val KEY_BACKUP_QR_ID = "qr_backup_id"
    private const val KEY_BACKUP_TIMESTAMP = "qr_backup_timestamp"

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

    /**
     * Erstellt und speichert einen neuen, eindeutigen ScanOff-Code. Ersetzt einen evtl.
     * bestehenden und setzt dessen Sicherungsstatus zurück – eine Sicherung des alten Codes gilt
     * nie automatisch für den neuen.
     */
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
            .remove(KEY_BACKUP_STATUS)
            .remove(KEY_BACKUP_QR_ID)
            .remove(KEY_BACKUP_TIMESTAMP)
            .apply()
        return record
    }

    // ---------- Sicherungsstatus ----------

    /**
     * Sicherungsstatus des AKTUELLEN Codes. Ist der gespeicherte Status an eine andere (ältere)
     * QR-Code-ID gebunden, gilt er nicht – so kann die Sicherung eines alten Codes niemals
     * fälschlich für einen neuen übernommen werden.
     */
    fun getBackupStatus(c: Context): QrBackupStatus {
        val record = getQrCode(c) ?: return QrBackupStatus.NOT_BACKED_UP
        if (sp(c).getString(KEY_BACKUP_QR_ID, null) != record.id) return QrBackupStatus.NOT_BACKED_UP
        val raw = sp(c).getString(KEY_BACKUP_STATUS, null) ?: return QrBackupStatus.NOT_BACKED_UP
        return try {
            QrBackupStatus.valueOf(raw)
        } catch (e: IllegalArgumentException) {
            QrBackupStatus.NOT_BACKED_UP
        }
    }

    fun isQrBackedUp(c: Context): Boolean = getBackupStatus(c) != QrBackupStatus.NOT_BACKED_UP

    /** Markiert den aktuellen Code als gesichert. Wird nur nach einem bestätigten Erfolg aufgerufen. */
    fun markQrBackedUp(c: Context, status: QrBackupStatus) {
        val record = getQrCode(c) ?: return
        sp(c).edit()
            .putString(KEY_BACKUP_STATUS, status.name)
            .putString(KEY_BACKUP_QR_ID, record.id)
            .putLong(KEY_BACKUP_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    /**
     * Zentrale Entsperrprüfung: ein gescannter Code darf eine Pause beenden, wenn er entweder dem
     * eigenen gültigen ScanOff-Code entspricht, oder einem gespeicherten ScanOff-Freund mit
     * aktivierter Entsperrfreigabe. Autorisierung erfolgt dabei ausschließlich über den Abgleich
     * der eindeutigen ID – niemals über Benutzername oder Zeitstempel allein.
     */
    fun verifyScan(c: Context, scannedText: String): ScanVerdict {
        val own = getQrCode(c)

        // Eigener Code im Legacy-Format (reiner Secret-String, kein JSON).
        if (own != null && own.version == 0 && scannedText == own.id) {
            return ScanVerdict.OwnCodeMatch
        }

        val decoded = QrPayload.decode(scannedText)
            ?: return if (scannedText.startsWith(LEGACY_PREFIX)) ScanVerdict.Unrecognized else ScanVerdict.Invalid

        if (own != null && own.version != 0 && decoded.id == own.id) {
            return ScanVerdict.OwnCodeMatch
        }

        val friend = FriendsStore.findById(c, decoded.id) ?: return ScanVerdict.Unrecognized
        return if (friend.unlockEnabled) ScanVerdict.FriendMatch(friend.username) else ScanVerdict.FriendDisabled
    }

    // ---------- Sperrstatus ----------

    fun isLocked(c: Context): Boolean = sp(c).getBoolean(KEY_LOCKED, false)

    fun setLocked(c: Context, locked: Boolean) {
        sp(c).edit().putBoolean(KEY_LOCKED, locked).apply()
    }

    /**
     * Zentrale Startfunktion: beginnt eine neue Pause und legt zugleich den zugehörigen
     * Historien-Eintrag an (siehe [PauseHistoryStore.startSession]).
     */
    fun startPause(c: Context) {
        setLocked(c, true)
        incrementPauseCount(c)
        PauseHistoryStore.startSession(c)
    }

    /**
     * Zentrale Entsperrfunktion: beendet die aktuell laufende Pause und schließt zugleich den
     * zugehörigen Historien-Eintrag mit [reason] ab. Wird sowohl nach erfolgreichem QR-Code-Scan
     * als auch bei der Notfall-Entsperrung verwendet – die Liste der ausgewählten Apps bleibt
     * dabei unverändert erhalten.
     */
    fun unlock(c: Context, reason: PauseEndReason) {
        setLocked(c, false)
        PauseHistoryStore.completeOpenSession(c, reason)
    }

    fun getBlockedApps(c: Context): Set<String> =
        sp(c).getStringSet(KEY_APPS, emptySet())?.toSet() ?: emptySet()

    fun setBlockedApps(c: Context, apps: Set<String>) {
        sp(c).edit().putStringSet(KEY_APPS, HashSet(apps)).apply()
    }

    // ---------- Wiederherstellung (Backup) ----------

    /**
     * Stellt einen zuvor vollständig validierten Backup-Snapshot wieder her. ScanOff-Code,
     * blockierte Apps und ScanOff-Freunde werden in einem einzigen Editor-Commit geschrieben, damit
     * bei einem Fehler niemals nur ein Teil der Daten übernommen wird. [identity] wird unverändert
     * als [QrCodeRecord] gespeichert – diese Funktion erzeugt selbst NIE eine neue UUID oder einen
     * neuen Zeitstempel. Validierung muss vor dem Aufruf bereits abgeschlossen sein.
     */
    fun restoreSnapshot(
        c: Context,
        identity: QrCodeRecord,
        blockedApps: Set<String>,
        friends: List<ScanOffFriend>,
        pauseHistory: List<PauseSession>,
        blockedAttempts: List<BlockedAppAttempt>,
        resetBackupStatus: Boolean
    ) {
        var editor = sp(c).edit()
            .putString(KEY_QR_RECORD, QrPayload.encode(identity))
            .putStringSet(KEY_APPS, HashSet(blockedApps))
            .remove(KEY_LEGACY_SECRET)
            .remove(KEY_LEGACY_USERNAME)
        if (resetBackupStatus) {
            editor = editor.remove(KEY_BACKUP_STATUS).remove(KEY_BACKUP_QR_ID).remove(KEY_BACKUP_TIMESTAMP)
        }
        editor = FriendsStore.putFriends(editor, friends)
        editor = PauseHistoryStore.putSessions(editor, pauseHistory)
        BlockedAttemptStore.putAttempts(editor, blockedAttempts).apply()
    }

    // ---------- Statistik ----------

    fun getPauseCount(c: Context): Int = sp(c).getInt(KEY_PAUSE_COUNT, 0)

    fun incrementPauseCount(c: Context) {
        sp(c).edit().putInt(KEY_PAUSE_COUNT, getPauseCount(c) + 1).apply()
    }
}
