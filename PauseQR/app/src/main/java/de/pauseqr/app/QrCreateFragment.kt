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
 * ausdrücklicher Bestätigung ersetzt. Ein ScanOff-Passwort ist für jeden neuen Code Pflicht (siehe
 * [PasswordCheckUtil]) und wird ausschließlich als abgeleiteter Prüfwert gespeichert.
 */
class QrCreateFragment : Fragment(R.layout.fragment_qr_create) {

    private lateinit var tilUsername: TextInputLayout
    private lateinit var etUsername: TextInputEditText
    private lateinit var tilNewPassword: TextInputLayout
    private lateinit var etNewPassword: TextInputEditText
    private lateinit var tilNewPasswordConfirm: TextInputLayout
    private lateinit var etNewPasswordConfirm: TextInputEditText
    private lateinit var btnGenerateQr: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tilUsername = view.findViewById(R.id.tilUsername)
        etUsername = view.findViewById(R.id.etUsername)
        tilNewPassword = view.findViewById(R.id.tilNewPassword)
        etNewPassword = view.findViewById(R.id.etNewPassword)
        tilNewPasswordConfirm = view.findViewById(R.id.tilNewPasswordConfirm)
        etNewPasswordConfirm = view.findViewById(R.id.etNewPasswordConfirm)
        btnGenerateQr = view.findViewById(R.id.btnGenerateQr)

        btnGenerateQr.setOnClickListener { onGenerateClicked() }
    }

    private fun onGenerateClicked() {
        val context = context ?: return

        if (Prefs.isLocked(context)) {
            // Während einer laufenden Pause darf der Code nicht gewechselt werden – sonst würde
            // der physisch gesicherte QR-Code nicht mehr zum Entsperren passen.
            AlertDialog.Builder(context)
                .setTitle(R.string.qr_locked_title)
                .setMessage(R.string.qr_locked_message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        val username = etUsername.text.toString().trim().take(Prefs.USERNAME_MAX_LENGTH)
        if (username.isEmpty()) {
            tilUsername.error = getString(R.string.username_required)
            return
        }
        tilUsername.error = null

        tilNewPassword.error = null
        tilNewPasswordConfirm.error = null
        val password = editableToCharArray(etNewPassword)
        val confirm = editableToCharArray(etNewPasswordConfirm)

        when {
            password.size < PasswordCheckUtil.MIN_PASSWORD_LENGTH -> {
                tilNewPassword.error = getString(R.string.password_too_short)
                password.fill('\u0000')
                confirm.fill('\u0000')
                return
            }
            !password.contentEquals(confirm) -> {
                tilNewPasswordConfirm.error = getString(R.string.password_mismatch)
                password.fill('\u0000')
                confirm.fill('\u0000')
                return
            }
        }
        confirm.fill('\u0000')

        if (Prefs.hasQrCode(context)) {
            AlertDialog.Builder(context)
                .setTitle(R.string.replace_qr_title)
                .setMessage(R.string.replace_qr_message)
                .setPositiveButton(R.string.replace_qr_confirm) { _, _ -> createAndReturn(username, password) }
                .setNegativeButton(R.string.replace_qr_cancel) { _, _ -> password.fill('\u0000') }
                .show()
        } else {
            createAndReturn(username, password)
        }
    }

    private fun createAndReturn(username: String, password: CharArray) {
        val context = context ?: run { password.fill('\u0000'); return }
        Prefs.createNewQrCode(context, username, password)
        password.fill('\u0000')
        Toast.makeText(context, R.string.qr_created, Toast.LENGTH_LONG).show()
        (activity as? MainActivity)?.popFragment()
    }

    private fun editableToCharArray(editText: TextInputEditText): CharArray {
        val text = editText.text ?: return CharArray(0)
        val chars = CharArray(text.length)
        text.getChars(0, text.length, chars, 0)
        return chars
    }
}
