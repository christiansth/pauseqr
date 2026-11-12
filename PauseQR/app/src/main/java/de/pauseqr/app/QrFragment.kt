package de.pauseqr.app

import android.content.DialogInterface
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.text.DateFormat
import java.util.Date

/**
 * „Mein ScanOff-Code“: zeigt den bestehenden ScanOff-Code oder bietet die Ersterstellung bzw. den
 * passwortgeprüften Import einer bereits bestehenden eigenen Identität an (Kamera-Scan oder
 * Galerie-Bild). Speichern und Teilen sind hier immer verfügbar, solange ein gültiger Code
 * existiert – unabhängig vom Sicherungsstatus und unabhängig davon, ob gerade eine Pause aktiv ist.
 */
class QrFragment : Fragment(R.layout.fragment_qr) {

    private lateinit var qrExistsGroup: View
    private lateinit var qrEmptyGroup: View
    private lateinit var tvUsernameReadonly: TextView
    private lateinit var ivQr: ImageView
    private lateinit var tvCreatedAt: TextView
    private lateinit var cardPasswordMigration: View
    private lateinit var btnSetupPassword: Button
    private lateinit var btnSaveQr: Button
    private lateinit var btnShareQr: Button
    private lateinit var btnNewQr: Button
    private lateinit var btnCreateQr: Button
    private lateinit var btnImportScanOwn: Button
    private lateinit var btnImportGalleryOwn: Button

    private val scanOwnLauncher = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { handleImportPayload(it) }
    }

    private val galleryOwnLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { handleImportedOwnImage(it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        qrExistsGroup = view.findViewById(R.id.qrExistsGroup)
        qrEmptyGroup = view.findViewById(R.id.qrEmptyGroup)
        tvUsernameReadonly = view.findViewById(R.id.tvUsernameReadonly)
        ivQr = view.findViewById(R.id.ivQr)
        tvCreatedAt = view.findViewById(R.id.tvCreatedAt)
        cardPasswordMigration = view.findViewById(R.id.cardPasswordMigration)
        btnSetupPassword = view.findViewById(R.id.btnSetupPassword)
        btnSaveQr = view.findViewById(R.id.btnSaveQr)
        btnShareQr = view.findViewById(R.id.btnShareQr)
        btnNewQr = view.findViewById(R.id.btnNewQr)
        btnCreateQr = view.findViewById(R.id.btnCreateQr)
        btnImportScanOwn = view.findViewById(R.id.btnImportScanOwn)
        btnImportGalleryOwn = view.findViewById(R.id.btnImportGalleryOwn)

        btnSaveQr.setOnClickListener { saveQrToGallery() }
        btnShareQr.setOnClickListener { shareQrCode() }
        btnNewQr.setOnClickListener { openCreateScreen() }
        btnCreateQr.setOnClickListener { openCreateScreen() }
        btnSetupPassword.setOnClickListener { openChangePasswordScreen() }
        btnImportScanOwn.setOnClickListener { startScanForImport() }
        btnImportGalleryOwn.setOnClickListener {
            galleryOwnLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun openCreateScreen() {
        (activity as? MainActivity)?.pushFragment(QrCreateFragment())
    }

    private fun openChangePasswordScreen() {
        (activity as? MainActivity)?.pushFragment(ChangePasswordFragment())
    }

    private fun refresh() {
        val context = context ?: return
        // Error wird hier bewusst wie Empty behandelt: Prefs.loadQrCodeState löscht in beiden
        // Fällen keine Daten – bei Error bleiben die nicht lesbaren Rohdaten unangetastet, bis
        // der Nutzer hier ausdrücklich einen neuen Code erstellt.
        val record = (Prefs.loadQrCodeState(context) as? QrCodeState.Existing)?.record
        if (record == null) {
            qrExistsGroup.visibility = View.GONE
            qrEmptyGroup.visibility = View.VISIBLE
            return
        }

        qrExistsGroup.visibility = View.VISIBLE
        qrEmptyGroup.visibility = View.GONE
        tvUsernameReadonly.text = record.username
        ivQr.setImageBitmap(QrUtil.makeQr(record, 900))
        tvCreatedAt.text = if (record.createdAt > 0) {
            getString(R.string.created_at_label, formatDate(record.createdAt))
        } else {
            getString(R.string.created_at_unknown)
        }
        // Version-1-Identitäten (vor diesem Update erzeugt) sind nie stillschweigend
        // passwortgeschützt – stattdessen wird die Einrichtung hier ausdrücklich angeboten.
        cardPasswordMigration.visibility = if (record.passwordCheck == null) View.VISIBLE else View.GONE
    }

    private fun formatDate(millis: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

    private fun saveQrToGallery() {
        val context = context ?: return
        val record = Prefs.getQrCode(context) ?: return
        val success = QrUtil.saveToGallery(context, QrUtil.makeQr(record, 1024), record.username)
        if (success) {
            Prefs.markQrBackedUp(context, QrBackupStatus.SAVED_TO_DEVICE)
            toast(R.string.qr_saved)
        } else {
            toast(R.string.qr_save_failed)
        }
    }

    private fun shareQrCode() {
        val context = context ?: return
        val record = Prefs.getQrCode(context) ?: return
        // Öffnet nur das Sharesheet – dies allein zählt noch nicht als Sicherung (siehe Dashboard
        // für die ausdrückliche Bestätigung "Ich habe den QR-Code gesichert.").
        ShareUtil.shareQrCode(context, QrUtil.makeQr(record, 1024))
    }

    // ---------- Eigene Identität importieren (Kamera/Galerie) ----------

    private fun startScanForImport() {
        val context = context ?: return
        if (Prefs.isLocked(context)) {
            showLockedDialog()
            return
        }
        val options = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt(getString(R.string.scan_prompt))
            .setBeepEnabled(false)
            .setOrientationLocked(false)
        scanOwnLauncher.launch(options)
    }

    private fun handleImportedOwnImage(uri: Uri) {
        val context = context ?: return
        if (Prefs.isLocked(context)) {
            showLockedDialog()
            return
        }
        val bitmap = try {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        }
        val payload = bitmap?.let { QrUtil.decodeFromBitmap(it) }
        if (payload == null) {
            toast(R.string.import_not_found)
            return
        }
        handleImportPayload(payload)
    }

    private fun showLockedDialog() {
        val context = context ?: return
        AlertDialog.Builder(context)
            .setTitle(R.string.qr_locked_title)
            .setMessage(R.string.qr_locked_message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /** Derselbe Prüfablauf für beide Importwege: Format/Version prüfen, dann Passwort verlangen. */
    private fun handleImportPayload(payload: String) {
        val context = context ?: return
        if (Prefs.isLocked(context)) {
            showLockedDialog()
            return
        }
        val decoded = QrPayload.decode(payload)
        val passwordCheck = decoded?.passwordCheck
        if (decoded == null || passwordCheck == null) {
            toast(R.string.qr_import_unsupported_version)
            return
        }
        showImportPasswordDialog(decoded, passwordCheck)
    }

    private fun showImportPasswordDialog(decoded: QrCodeRecord, passwordCheck: PasswordCheck) {
        val context = context ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_password_enter, null)
        val tilPassword = dialogView.findViewById<TextInputLayout>(R.id.tilPasswordEnter)
        val etPassword = dialogView.findViewById<EditText>(R.id.etPasswordEnter)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.password_enter_title)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_password_continue, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                tilPassword.error = null
                val password = editableToCharArray(etPassword)
                val valid = PasswordCheckUtil.verify(password, passwordCheck)
                password.fill('\u0000')
                if (!valid) {
                    toast(R.string.qr_import_wrong_password)
                    return@setOnClickListener
                }
                dialog.dismiss()
                showImportPreview(decoded)
            }
        }
        dialog.show()
    }

    private fun showImportPreview(decoded: QrCodeRecord) {
        val context = context ?: return
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.qr_import_preview_title)
            .setMessage(getString(R.string.qr_import_preview_message, decoded.username, formatDate(decoded.createdAt)))
            .setPositiveButton(R.string.qr_import_confirm) { _, _ -> confirmImport(decoded) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmImport(decoded: QrCodeRecord) {
        val context = context ?: return
        val existing = Prefs.getQrCode(context)
        if (existing != null && existing.id != decoded.id) {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.backup_identity_conflict_title)
                .setMessage(
                    getString(
                        R.string.qr_import_identity_conflict_message,
                        existing.username, existing.id.take(8),
                        decoded.username, decoded.id.take(8)
                    )
                )
                .setPositiveButton(R.string.backup_identity_replace) { _, _ -> performImport(context, decoded) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            performImport(context, decoded)
        }
    }

    private fun performImport(context: android.content.Context, decoded: QrCodeRecord) {
        Prefs.importOwnIdentity(context, decoded)
        toast(R.string.qr_import_success)
        refresh()
    }

    private fun editableToCharArray(editText: EditText): CharArray {
        val text = editText.text ?: return CharArray(0)
        val chars = CharArray(text.length)
        text.getChars(0, text.length, chars, 0)
        return chars
    }

    private fun toast(res: Int) {
        context?.let { Toast.makeText(it, res, Toast.LENGTH_LONG).show() }
    }
}
