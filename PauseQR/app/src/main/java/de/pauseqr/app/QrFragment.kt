package de.pauseqr.app

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText

/** Eigenständige Ansicht des persönlichen Pause-QR zum Scannen, Speichern oder Ausdrucken. */
class QrFragment : Fragment(R.layout.fragment_qr) {

    private lateinit var etUsername: TextInputEditText
    private lateinit var btnGenerateQr: Button
    private lateinit var ivQr: ImageView
    private lateinit var btnSaveQr: Button
    private lateinit var btnNewQr: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etUsername = view.findViewById(R.id.etUsername)
        btnGenerateQr = view.findViewById(R.id.btnGenerateQr)
        ivQr = view.findViewById(R.id.ivQr)
        btnSaveQr = view.findViewById(R.id.btnSaveQr)
        btnNewQr = view.findViewById(R.id.btnNewQr)

        etUsername.setText(Prefs.getUsername(requireContext()))

        btnGenerateQr.setOnClickListener {
            Prefs.setUsername(requireContext(), etUsername.text.toString().trim())
            refresh()
        }
        btnSaveQr.setOnClickListener { saveQrToGallery() }
        btnNewQr.setOnClickListener { confirmNewQr() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val context = context ?: return
        ivQr.setImageBitmap(QrUtil.makeQr(context, 900))
    }

    private fun saveQrToGallery() {
        val context = context ?: return
        val success = QrUtil.saveToGallery(context, QrUtil.makeQr(context, 1024))
        toast(if (success) R.string.qr_saved else R.string.qr_save_failed)
    }

    private fun confirmNewQr() {
        val context = context ?: return
        AlertDialog.Builder(context)
            .setTitle(R.string.new_qr_title)
            .setMessage(R.string.new_qr_message)
            .setPositiveButton(R.string.new_qr_confirm) { _, _ ->
                Prefs.newSecret(context)
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toast(res: Int) {
        context?.let { Toast.makeText(it, res, Toast.LENGTH_LONG).show() }
    }
}
