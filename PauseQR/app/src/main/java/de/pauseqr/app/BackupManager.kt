package de.pauseqr.app

import android.content.Context
import android.content.pm.PackageManager
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

/**
 * Baut, verschlüsselt, entschlüsselt und validiert ScanOff-Backups. Enthält bewusst keine
 * UI-Logik und führt selbst keine Schreibvorgänge in [Prefs]/[FriendsStore] aus – das Schreiben
 * geschieht erst nach vollständiger Validierung und ausdrücklicher Bestätigung durch
 * [Prefs.restoreSnapshot], damit ein fehlgeschlagener Import niemals bestehende Daten verändert.
 */
object BackupManager {
    private const val CONTAINER_MAGIC = "scanoff-backup"
    private const val CONTAINER_VERSION = 1
    const val BACKUP_FORMAT_VERSION = 1

    const val MIN_PASSWORD_LENGTH = 12
    const val MAX_PASSWORD_LENGTH = 256
    const val MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024
    private const val MAX_FRIENDS = 1000
    private const val MAX_BLOCKED_APPS = 1000
    private const val MAX_STRING_FIELD_LENGTH = 500
    private const val MAX_SESSIONS = 20_000
    private const val MAX_ITERATIONS = 2_000_000

    data class BackupSnapshot(
        val exportedAt: Long,
        val identity: QrCodeRecord,
        val language: String,
        val blockedApps: Set<String>,
        val friends: List<ScanOffFriend>,
        val pauseHistory: List<PauseSession>
    )

    sealed class DecryptResult {
        data class Success(val snapshot: BackupSnapshot) : DecryptResult()
        object FileTooLarge : DecryptResult()
        object MalformedContainer : DecryptResult()
        object WrongPasswordOrTampered : DecryptResult()
        object InvalidSnapshot : DecryptResult()
    }

    /** Liefert null, wenn kein persönlicher ScanOff-Code existiert – erstellt dabei nie einen neuen. */
    fun buildSnapshotJson(context: Context): String? {
        val identity = Prefs.getQrCode(context) ?: return null
        val friends = FriendsStore.getFriends(context)
        val blockedApps = Prefs.getBlockedApps(context)
        val language = LanguageUtil.currentLanguage(context)

        val json = JSONObject().apply {
            put("backupVersion", BACKUP_FORMAT_VERSION)
            put("app", "ScanOff")
            put("exportedAt", System.currentTimeMillis())
            put("identity", JSONObject().apply {
                put("type", QrPayload.TYPE)
                put("version", identity.version)
                put("id", identity.id)
                put("username", identity.username)
                put("createdAt", identity.createdAt)
            })
            put("settings", JSONObject().apply {
                put("language", language)
                put("blockedApps", JSONArray(blockedApps.toList()))
            })
            put("friends", JSONArray().apply {
                friends.forEach { f ->
                    put(JSONObject().apply {
                        put("id", f.id)
                        put("username", f.username)
                        put("createdAt", f.createdAt)
                        put("addedAt", f.addedAt)
                        put("qrPayload", f.qrPayload)
                        put("unlockEnabled", f.unlockEnabled)
                    })
                }
            })
            // Nur abgeschlossene Pausen werden gesichert – eine aktuell laufende Pause soll nie
            // automatisch wiederhergestellt werden (siehe Prefs.restoreSnapshot: KEY_LOCKED wird
            // dort ohnehin nie angefasst, diese Auswahl ist eine zusätzliche, bewusste Absicherung).
            put("pauseHistory", JSONArray().apply {
                PauseHistoryStore.getSessions(context).filter { it.completed }.forEach { s ->
                    put(JSONObject().apply {
                        put("id", s.id)
                        put("startTime", s.startTime)
                        put("endTime", s.endTime ?: JSONObject.NULL)
                        put("endReason", s.endReason?.name ?: JSONObject.NULL)
                        put("completed", true)
                    })
                }
            })
        }
        return json.toString()
    }

    /** Verschlüsselt [plaintextJson] mit [password] und liefert den fertigen Dateiinhalt. */
    fun encryptToFile(plaintextJson: String, password: CharArray): ByteArray {
        val salt = BackupCrypto.randomSalt()
        val nonce = BackupCrypto.randomNonce()
        val ciphertext = BackupCrypto.encrypt(
            plaintextJson.toByteArray(StandardCharsets.UTF_8), password, salt, nonce, BackupCrypto.KDF_ITERATIONS
        )
        val container = JSONObject().apply {
            put("container", CONTAINER_MAGIC)
            put("containerVersion", CONTAINER_VERSION)
            put("kdf", BackupCrypto.KDF_NAME)
            put("kdfIterations", BackupCrypto.KDF_ITERATIONS)
            put("salt", Base64.getEncoder().encodeToString(salt))
            put("nonce", Base64.getEncoder().encodeToString(nonce))
            put("ciphertext", Base64.getEncoder().encodeToString(ciphertext))
        }
        return container.toString().toByteArray(StandardCharsets.UTF_8)
    }

    /** Entschlüsselt und validiert eine `.scanoff`-Datei vollständig, ohne irgendetwas zu speichern. */
    fun decryptAndValidate(fileBytes: ByteArray, password: CharArray): DecryptResult {
        if (fileBytes.isEmpty() || fileBytes.size > MAX_FILE_SIZE_BYTES) return DecryptResult.FileTooLarge

        val container = try {
            JSONObject(String(fileBytes, StandardCharsets.UTF_8))
        } catch (e: Exception) {
            return DecryptResult.MalformedContainer
        }

        if (container.optString("container") != CONTAINER_MAGIC) return DecryptResult.MalformedContainer
        if (container.optInt("containerVersion", -1) != CONTAINER_VERSION) return DecryptResult.MalformedContainer

        val iterations = container.optInt("kdfIterations", -1)
        if (iterations <= 0 || iterations > MAX_ITERATIONS) return DecryptResult.MalformedContainer

        val salt = decodeFixedLengthBase64(container.optString("salt"), BackupCrypto.SALT_LENGTH_BYTES)
            ?: return DecryptResult.MalformedContainer
        val nonce = decodeFixedLengthBase64(container.optString("nonce"), BackupCrypto.NONCE_LENGTH_BYTES)
            ?: return DecryptResult.MalformedContainer
        val ciphertext = try {
            Base64.getDecoder().decode(container.optString("ciphertext"))
        } catch (e: Exception) {
            return DecryptResult.MalformedContainer
        }
        if (ciphertext.isEmpty() || ciphertext.size > MAX_FILE_SIZE_BYTES) return DecryptResult.MalformedContainer

        val plaintext = try {
            BackupCrypto.decrypt(ciphertext, password, salt, nonce, iterations)
        } catch (e: Exception) {
            // Deckt sowohl ein falsches Passwort als auch eine manipulierte Datei ab: die
            // GCM-Tag-Prüfung schlägt in beiden Fällen gleichermaßen fehl.
            return DecryptResult.WrongPasswordOrTampered
        }

        val snapshot = try {
            parseSnapshot(String(plaintext, StandardCharsets.UTF_8))
        } catch (e: Exception) {
            null
        } ?: return DecryptResult.InvalidSnapshot

        return DecryptResult.Success(snapshot)
    }

    private fun decodeFixedLengthBase64(value: String, expectedLength: Int): ByteArray? {
        return try {
            val bytes = Base64.getDecoder().decode(value)
            if (bytes.size != expectedLength) null else bytes
        } catch (e: Exception) {
            null
        }
    }

    private fun isValidUuid(value: String): Boolean = try {
        UUID.fromString(value)
        true
    } catch (e: IllegalArgumentException) {
        false
    }

    private fun parseSnapshot(json: String): BackupSnapshot? {
        val root = JSONObject(json)
        val backupVersion = root.optInt("backupVersion", -1)
        if (backupVersion < 1 || backupVersion > BACKUP_FORMAT_VERSION) return null

        val identityJson = root.optJSONObject("identity") ?: return null
        if (identityJson.optString("type") != QrPayload.TYPE) return null
        val identityVersion = identityJson.optInt("version", -1)
        if (identityVersion < 1) return null
        val id = identityJson.optString("id", "")
        val username = identityJson.optString("username", "").take(MAX_STRING_FIELD_LENGTH)
        val createdAt = identityJson.optLong("createdAt", -1L)
        if (id.isBlank() || username.isBlank() || createdAt <= 0 || !isValidUuid(id)) return null
        val identity = QrCodeRecord(id = id, username = username, createdAt = createdAt, version = identityVersion)

        val settingsJson = root.optJSONObject("settings") ?: JSONObject()
        val language = settingsJson.optString("language", LanguageUtil.ENGLISH)
            .let { if (it == LanguageUtil.GERMAN) LanguageUtil.GERMAN else LanguageUtil.ENGLISH }

        val blockedAppsArray = settingsJson.optJSONArray("blockedApps") ?: JSONArray()
        if (blockedAppsArray.length() > MAX_BLOCKED_APPS) return null
        val blockedApps = (0 until blockedAppsArray.length())
            .mapNotNull { blockedAppsArray.optString(it, null) }
            .filter { it.isNotBlank() }
            .map { it.take(MAX_STRING_FIELD_LENGTH) }
            .toSet()

        val friendsArray = root.optJSONArray("friends") ?: JSONArray()
        if (friendsArray.length() > MAX_FRIENDS) return null
        val friends = (0 until friendsArray.length()).mapNotNull { i ->
            val f = friendsArray.optJSONObject(i) ?: return@mapNotNull null
            val fid = f.optString("id", "")
            val fusername = f.optString("username", "").take(MAX_STRING_FIELD_LENGTH)
            val fqrPayload = f.optString("qrPayload", "").take(MAX_STRING_FIELD_LENGTH * 4)
            if (fid.isBlank() || fusername.isBlank() || fqrPayload.isBlank() || !isValidUuid(fid)) {
                return@mapNotNull null
            }
            ScanOffFriend(
                id = fid,
                username = fusername,
                createdAt = f.optLong("createdAt", 0L),
                addedAt = f.optLong("addedAt", 0L),
                qrPayload = fqrPayload,
                unlockEnabled = f.optBoolean("unlockEnabled", false)
            )
        }

        val sessionsArray = root.optJSONArray("pauseHistory") ?: JSONArray()
        val pauseHistory = if (sessionsArray.length() > MAX_SESSIONS) {
            emptyList()
        } else {
            (0 until sessionsArray.length()).mapNotNull { i -> parseSession(sessionsArray.optJSONObject(i)) }
        }

        return BackupSnapshot(
            exportedAt = root.optLong("exportedAt", 0L),
            identity = identity,
            language = language,
            blockedApps = blockedApps,
            friends = friends,
            pauseHistory = pauseHistory
        )
    }

    private fun parseSession(json: JSONObject?): PauseSession? {
        if (json == null) return null
        val id = json.optString("id", "")
        val startTime = json.optLong("startTime", -1L)
        val endTime = if (json.isNull("endTime")) null else json.optLong("endTime", -1L).takeIf { it >= 0 }
        val reasonRaw = if (json.isNull("endReason")) null else json.optString("endReason", "")
        val endReason = reasonRaw?.let {
            try {
                PauseEndReason.valueOf(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
        // Nur vollständige, in sich konsistente abgeschlossene Pausen werden übernommen – niemals
        // eine laufende Pause (siehe buildSnapshotJson, das solche gar nicht erst exportiert).
        if (id.isBlank() || !isValidUuid(id) || startTime <= 0 || endTime == null || endTime < startTime) return null
        return PauseSession(id = id, startTime = startTime, endTime = endTime, endReason = endReason, completed = true)
    }

    /** Filtert auf tatsächlich auf diesem Gerät installierte Pakete – verhindert Abstürze durch
     * nicht mehr vorhandene Apps und stellt nie eine Auswahl für fremde/gelöschte Apps her. */
    fun filterInstalledApps(context: Context, blockedApps: Set<String>): Set<String> {
        val pm = context.packageManager
        return blockedApps.filter { pkg ->
            try {
                pm.getApplicationInfo(pkg, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }.toSet()
    }

    /**
     * Freunde-Merge-Strategie: bestehende Einträge (inkl. ihrer Entsperrfreigabe) bleiben
     * unverändert erhalten; neue Freunde aus dem Backup werden anhand ihrer UUID ergänzt. Es
     * entstehen nie doppelte Einträge und bestehende Freigaben werden nie durch den Import
     * überschrieben.
     */
    fun mergeFriends(existing: List<ScanOffFriend>, imported: List<ScanOffFriend>): List<ScanOffFriend> {
        val existingIds = existing.map { it.id }.toSet()
        return existing + imported.filter { it.id !in existingIds }
    }

    /**
     * Pausenhistorie-Merge: eindeutige Session-IDs verhindern doppelte Einträge. Bestehende
     * Einträge bleiben unverändert; nur neue, bisher unbekannte Pausen aus dem Backup werden
     * ergänzt. Erfolge werden daraus anschließend immer neu berechnet (siehe [AchievementEngine]),
     * nie separat gespeichert oder übernommen.
     */
    fun mergeSessions(existing: List<PauseSession>, imported: List<PauseSession>): List<PauseSession> {
        val existingIds = existing.map { it.id }.toSet()
        return existing + imported.filter { it.id !in existingIds }
    }
}
