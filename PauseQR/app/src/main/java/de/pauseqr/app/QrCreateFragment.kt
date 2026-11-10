package de.pauseqr.app

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Neuen ScanOff-Code erstellen. Erzeugt bei Bestätigung eine neue, eindeutige ID
 * ([UUID.randomUUID]) und einen neuen Zeitstempel – ein bestehender Code wird dabei nur nach
 * ausdrücklicher Bestätigung ersetzt.
 */
class QrCreateFragment : Fragment(R.layout.fragment_qr_create) {

    private lateinit var tilUsername: TextInputLayout
    private lateinit var etUsername: TextInputEditText
    private lateinit var btnGenerateQr: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tilUsername = view.findViewById(R.id.tilUsername)
        etUsername = view.findViewById(R.id.etUsername)
        btnGenerateQr = view.findViewById(R.id.btnGenerateQr)

        btnGenerateQr.setOnClickListener { onGenerateClicked() }
    }

    private fun onGenerateClicked() {
        val context = context ?: return
        val username = etUsername.text.toString().trim().take(Prefs.USERNAME_MAX_LENGTH)

        if (username.isEmpty()) {
            tilUsername.error = getString(R.string.username_required)
            return
        }
        tilUsername.error = null

        if (Prefs.hasQrCode(context)) {
            AlertDialog.Builder(context)
                .setTitle(R.string.replace_qr_title)
                .setMessage(R.string.replace_qr_message)
                .setPositiveButton(R.string.replace_qr_confirm) { _, _ -> createAndReturn(username) }
                .setNegativeButton(R.string.replace_qr_cancel, null)
                .show()
        } else {
            createAndReturn(username)
        }
    }

    private fun createAndReturn(username: String) {
        val context = context ?: return
        Prefs.createNewQrCode(context, username)
        Toast.makeText(context, R.string.qr_created, Toast.LENGTH_LONG).show()
        (activity as? MainActivity)?.popFragment()
    }
}
