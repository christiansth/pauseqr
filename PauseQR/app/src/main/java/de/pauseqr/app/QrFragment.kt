package de.pauseqr.app

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import java.text.DateFormat
import java.util.Date

/**
 * „Mein ScanOff-Code“: zeigt den bestehenden ScanOff-Code oder bietet die Ersterstellung an.
 * Speichern und Teilen sind hier immer verfügbar, solange ein gültiger Code existiert – unabhängig
 * vom Sicherungsstatus und unabhängig davon, ob gerade eine Pause aktiv ist.
 */
class QrFragment : Fragment(R.layout.fragment_qr) {

    private lateinit var qrExistsGroup: View
    private lateinit var qrEmptyGroup: View
    private lateinit var tvUsernameReadonly: TextView
    private lateinit var ivQr: ImageView
    private lateinit var tvCreatedAt: TextView
    private lateinit var btnSaveQr: Button
    private lateinit var btnShareQr: Button
    private lateinit var btnNewQr: Button
    private lateinit var btnCreateQr: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        qrExistsGroup = view.findViewById(R.id.qrExistsGroup)
        qrEmptyGroup = view.findViewById(R.id.qrEmptyGroup)
        tvUsernameReadonly = view.findViewById(R.id.tvUsernameReadonly)
        ivQr = view.findViewById(R.id.ivQr)
        tvCreatedAt = view.findViewById(R.id.tvCreatedAt)
        btnSaveQr = view.findViewById(R.id.btnSaveQr)
        btnShareQr = view.findViewById(R.id.btnShareQr)
        btnNewQr = view.findViewById(R.id.btnNewQr)
        btnCreateQr = view.findViewById(R.id.btnCreateQr)

        btnSaveQr.setOnClickListener { saveQrToGallery() }
        btnShareQr.setOnClickListener { shareQrCode() }
        btnNewQr.setOnClickListener { openCreateScreen() }
        btnCreateQr.setOnClickListener { openCreateScreen() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun openCreateScreen() {
        (activity as? MainActivity)?.pushFragment(QrCreateFragment())
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

    private fun toast(res: Int) {
        context?.let { Toast.makeText(it, res, Toast.LENGTH_LONG).show() }
    }
}
