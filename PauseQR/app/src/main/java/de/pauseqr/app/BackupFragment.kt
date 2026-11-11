package de.pauseqr.app

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import java.io.ByteArrayOutputStream
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * „Backup & Wiederherstellung": verschlüsselte lokale Sicherung des persönlichen ScanOff-Codes,
 * der ScanOff-Freunde und ausgewählter Einstellungen für einen Gerätewechsel – ohne Cloud-
 * Anbindung. Erstellen/Wiederherstellen laufen bewusst auf einem Hintergrund-Thread (PBKDF2 mit
 * hoher Iterationszahl soll die UI nicht blockieren); jeder Zugriff auf Views danach prüft [isAdded].
 */
class BackupFragment : Fragment(R.layout.fragment_backup) {

    private lateinit var btnCreate: Button
    private lateinit var btnRestore: Button

    // Transiente Zwischenzustände zwischen den asynchronen Schritten (Passwort-Dialog, SAF-Dialog,
    // Entschlüsselung) – werden nie dauerhaft gespeichert.
    private var pendingBackupBytes: ByteArray? = null
    private var pendingRestoreFileBytes: ByteArray? = null

    private val createDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            onBackupLocationChosen(uri)
        }

    private val openDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            onBackupFileChosen(uri)
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        btnCreate = view.findViewById(R.id.btnBackupCreate)
        btnRestore = view.findViewById(R.id.btnBackupRestore)

        btnCreate.setOnClickListener { onCreateBackupClicked() }
        btnRestore.setOnClickListener { onRestoreBackupClicked() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val context = context ?: return
        // Bleibt klickbar, damit der erklärende Toast angezeigt werden kann – nur die
        // Beschriftung wird ausgegraut (gleiches Muster wie die Notfall-Entsperrung in den
        // Einstellungen).
        btnRestore.alpha = if (Prefs.isLocked(context)) 0.38f else 1f
    }

    // ---------- Backup erstellen ----------

    private fun onCreateBackupClicked() {
        val context = context ?: return
        if (!Prefs.hasQrCode(context)) {
            toast(R.string.backup_no_identity)
            return
        }
        showCreatePasswordDialog()
    }

    private fun showCreatePasswordDialog() {
        val context = context ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_backup_password_create, null)
        val tilPassword = dialogView.findViewById<TextInputLayout>(R.id.tilBackupPassword)
        val tilConfirm = dialogView.findViewById<TextInputLayout>(R.id.tilBackupPasswordConfirm)
        val etPassword = dialogView.findViewById<EditText>(R.id.etBackupPassword)
        val etConfirm = dialogView.findViewById<EditText>(R.id.etBackupPasswordConfirm)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.backup_password_title)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_backup_password_continue, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                tilPassword.error = null
                tilConfirm.error = null
                val password = editableToCharArray(etPassword)
                val confirm = editableToCharArray(etConfirm)
                when {
                    password.size < BackupManager.MIN_PASSWORD_LENGTH -> {
                        tilPassword.error = getString(R.string.backup_password_too_short)
                        password.fill('\u0000')
                        confirm.fill('\u0000')
                    }
                    password.size > BackupManager.MAX_PASSWORD_LENGTH -> {
                        tilPassword.error = getString(R.string.backup_password_too_short)
                        password.fill('\u0000')
                        confirm.fill('\u0000')
                    }
                    !password.contentEquals(confirm) -> {
                        tilConfirm.error = getString(R.string.backup_password_mismatch)
                        password.fill('\u0000')
                        confirm.fill('\u0000')
                    }
                    else -> {
                        confirm.fill('\u0000')
                        dialog.dismiss()
                        startBackupCreation(password)
                    }
                }
            }
        }
        dialog.show()
    }

    private fun startBackupCreation(password: CharArray) {
        val context = context ?: run { password.fill('\u0000'); return }
        btnCreate.isEnabled = false
        toast(R.string.backup_creating)

        Thread {
            val plaintext = BackupManager.buildSnapshotJson(context)
            val bytes = if (plaintext != null) BackupManager.encryptToFile(plaintext, password) else null
            password.fill('\u0000')

            runOnUiThreadIfAdded {
                btnCreate.isEnabled = true
                if (bytes == null) {
                    toast(R.string.backup_no_identity)
                    return@runOnUiThreadIfAdded
                }
                pendingBackupBytes = bytes
                val fileName = "ScanOff-Backup-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.scanoff"
                createDocumentLauncher.launch(fileName)
            }
        }.start()
    }

    private fun onBackupLocationChosen(uri: Uri?) {
        val context = context ?: return
        val bytes = pendingBackupBytes
        pendingBackupBytes = null
        if (uri == null || bytes == null) return

        val success = try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            true
        } catch (e: Exception) {
            false
        }
        toast(if (success) R.string.backup_success else R.string.backup_save_failed)
    }

    // ---------- Backup wiederherstellen ----------

    private fun onRestoreBackupClicked() {
        val context = context ?: return
        if (Prefs.isLocked(context)) {
            toast(R.string.backup_restore_disabled_hint)
            return
        }
        openDocumentLauncher.launch(arrayOf("*/*"))
    }

    private fun onBackupFileChosen(uri: Uri?) {
        val context = context ?: return
        if (uri == null) return

        Thread {
            val bytes = readFileBounded(uri)
            runOnUiThreadIfAdded {
                if (bytes == null) {
                    toast(R.string.backup_read_failed)
                    return@runOnUiThreadIfAdded
                }
                if (bytes.size > BackupManager.MAX_FILE_SIZE_BYTES) {
                    toast(R.string.backup_file_too_large)
                    return@runOnUiThreadIfAdded
                }
                pendingRestoreFileBytes = bytes
                showRestorePasswordDialog()
            }
        }.start()
    }

    /** Liest höchstens [BackupManager.MAX_FILE_SIZE_BYTES] + 1 Bytes, um überlange Dateien früh zu erkennen. */
    private fun readFileBounded(uri: Uri): ByteArray? {
        val context = context ?: return null
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val limit = BackupManager.MAX_FILE_SIZE_BYTES + 1
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = input.read(chunk)
                    if (read < 0) break
                    total += read
                    if (total > limit) return ByteArray(limit + 1) // Signalisiert "zu groß", ohne die ganze Datei zu lesen.
                    buffer.write(chunk, 0, read)
                }
                buffer.toByteArray()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun showRestorePasswordDialog() {
        val context = context ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_backup_password_enter, null)
        val etPassword = dialogView.findViewById<EditText>(R.id.etBackupPasswordEnter)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.backup_restore_password_title)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_backup_decrypt, null)
            .setNegativeButton(android.R.string.cancel) { _, _ -> pendingRestoreFileBytes = null }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val password = editableToCharArray(etPassword)
                dialog.dismiss()
                startDecryption(password)
            }
        }
        dialog.show()
    }

    private fun startDecryption(password: CharArray) {
        val fileBytes = pendingRestoreFileBytes
        pendingRestoreFileBytes = null
        if (fileBytes == null) {
            password.fill('\u0000')
            return
        }

        Thread {
            val result = BackupManager.decryptAndValidate(fileBytes, password)
            password.fill('\u0000')

            runOnUiThreadIfAdded {
                when (result) {
                    is BackupManager.DecryptResult.Success -> handleDecryptedSnapshot(result.snapshot)
                    BackupManager.DecryptResult.FileTooLarge -> toast(R.string.backup_file_too_large)
                    BackupManager.DecryptResult.MalformedContainer,
                    BackupManager.DecryptResult.InvalidSnapshot -> toast(R.string.backup_invalid_file)
                    BackupManager.DecryptResult.WrongPasswordOrTampered -> toast(R.string.backup_wrong_password)
                }
            }
        }.start()
    }

    private fun handleDecryptedSnapshot(snapshot: BackupManager.BackupSnapshot) {
        val context = context ?: return
        val existing = Prefs.getQrCode(context)

        if (existing != null && existing.id != snapshot.identity.id) {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.backup_identity_conflict_title)
                .setMessage(
                    getString(
                        R.string.backup_identity_conflict_message,
                        existing.username, shortId(existing.id),
                        snapshot.identity.username, shortId(snapshot.identity.id)
                    )
                )
                .setPositiveButton(R.string.backup_identity_replace) { _, _ -> showRestorePreview(snapshot) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            showRestorePreview(snapshot)
        }
    }

    private fun showRestorePreview(snapshot: BackupManager.BackupSnapshot) {
        val context = context ?: return
        val installedApps = BackupManager.filterInstalledApps(context, snapshot.blockedApps)

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.backup_preview_title)
            .setMessage(
                getString(
                    R.string.backup_preview_message,
                    snapshot.identity.username,
                    formatDate(snapshot.exportedAt),
                    snapshot.friends.size,
                    installedApps.size,
                    snapshot.blockedApps.size,
                    snapshot.pauseHistory.size
                )
            )
            .setPositiveButton(R.string.backup_restore_confirm) { _, _ -> performRestore(snapshot, installedApps) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun performRestore(snapshot: BackupManager.BackupSnapshot, installedApps: Set<String>) {
        val context = context ?: return
        // Erneute Prüfung direkt vor dem Schreiben – verhindert eine Wiederherstellung, falls der
        // Pausenstatus sich zwischen Dateiauswahl und Bestätigung geändert haben sollte.
        if (Prefs.isLocked(context)) {
            toast(R.string.backup_restore_disabled_hint)
            return
        }

        val existing = Prefs.getQrCode(context)
        val resetBackupStatus = existing == null || existing.id != snapshot.identity.id
        val mergedFriends = BackupManager.mergeFriends(FriendsStore.getFriends(context), snapshot.friends)
        val mergedSessions = BackupManager.mergeSessions(PauseHistoryStore.getSessions(context), snapshot.pauseHistory)

        Prefs.restoreSnapshot(context, snapshot.identity, installedApps, mergedFriends, mergedSessions, resetBackupStatus)

        if (LanguageUtil.currentLanguage(context) != snapshot.language) {
            LanguageUtil.setLanguage(snapshot.language)
        }

        toast(R.string.backup_restore_success)
        refresh()

        if (!ServiceUtil.isBlockerServiceEnabled(context)) {
            MaterialAlertDialogBuilder(context)
                .setMessage(R.string.backup_accessibility_followup)
                .setPositiveButton(R.string.btn_accessibility) { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    // ---------- Hilfsfunktionen ----------

    private fun shortId(id: String): String = id.take(8)

    private fun formatDate(millis: Long): String =
        if (millis > 0) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis)) else ""

    private fun editableToCharArray(editText: EditText): CharArray {
        val text = editText.text ?: return CharArray(0)
        val chars = CharArray(text.length)
        text.getChars(0, text.length, chars, 0)
        return chars
    }

    private fun runOnUiThreadIfAdded(action: () -> Unit) {
        activity?.runOnUiThread { if (isAdded) action() }
    }

    private fun toast(res: Int) {
        context?.let { Toast.makeText(it, res, Toast.LENGTH_LONG).show() }
    }
}
