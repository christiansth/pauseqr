package de.pauseqr.app

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Richtet das ScanOff-Passwort erstmals ein (bei einer Version-1-Identität ohne Passwortschutz –
 * Migration) oder ändert ein bestehendes. UUID, Benutzername und `createdAt` bleiben in beiden
 * Fällen unverändert (siehe [Prefs.setOrChangePassword]). Bestehende ScanOff-Freunde sind davon
 * nicht betroffen, da deren Zuordnung ausschließlich über die unveränderte UUID erfolgt.
 */
class ChangePasswordFragment : Fragment(R.layout.fragment_change_password) {

    private lateinit var tvTitle: TextView
    private lateinit var tilCurrentPassword: TextInputLayout
    private lateinit var etCurrentPassword: TextInputEditText
    private lateinit var tilNewPassword: TextInputLayout
    private lateinit var etNewPassword: TextInputEditText
    private lateinit var tilNewPasswordConfirm: TextInputLayout
    private lateinit var etNewPasswordConfirm: TextInputEditText
    private lateinit var tvMigrationWarning: TextView
    private lateinit var btnSubmit: Button

    private var hasExistingPassword = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvTitle = view.findViewById(R.id.tvChangePasswordTitle)
        tilCurrentPassword = view.findViewById(R.id.tilCurrentPassword)
        etCurrentPassword = view.findViewById(R.id.etCurrentPassword)
        tilNewPassword = view.findViewById(R.id.tilNewPassword)
        etNewPassword = view.findViewById(R.id.etNewPassword)
        tilNewPasswordConfirm = view.findViewById(R.id.tilNewPasswordConfirm)
        etNewPasswordConfirm = view.findViewById(R.id.etNewPasswordConfirm)
        tvMigrationWarning = view.findViewById(R.id.tvMigrationWarning)
        btnSubmit = view.findViewById(R.id.btnSubmitPassword)

        val context = context
        val record = context?.let { Prefs.getQrCode(it) }
        if (record == null) {
            // Ohne bestehende Identität gibt es hier nichts zu tun – defensiv, da der
            // Einstiegspunkt (QrFragment/Settings) dies bereits ausschließt.
            (activity as? MainActivity)?.popFragment()
            return
        }

        hasExistingPassword = record.passwordCheck != null
        tilCurrentPassword.visibility = if (hasExistingPassword) View.VISIBLE else View.GONE
        tvMigrationWarning.visibility = if (hasExistingPassword) View.GONE else View.VISIBLE
        tvTitle.text = getString(if (hasExistingPassword) R.string.change_password_title else R.string.setup_password_title)
        btnSubmit.text = getString(if (hasExistingPassword) R.string.change_password_title else R.string.setup_password_title)

        btnSubmit.setOnClickListener { onSubmit() }
    }

    private fun onSubmit() {
        val context = context ?: return

        if (Prefs.isLocked(context)) {
            AlertDialog.Builder(context)
                .setTitle(R.string.qr_locked_title)
                .setMessage(R.string.qr_locked_message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        // Frisch laden statt des beim Öffnen gecachten Stands – vermeidet, auf veralteten
        // Passwortprüfdaten aufzusetzen, falls sich der Code zwischenzeitlich geändert haben sollte.
        val record = Prefs.getQrCode(context) ?: run {
            (activity as? MainActivity)?.popFragment()
            return
        }

        tilCurrentPassword.error = null
        tilNewPassword.error = null
        tilNewPasswordConfirm.error = null

        if (hasExistingPassword) {
            val current = editableToCharArray(etCurrentPassword)
            val valid = record.passwordCheck != null && PasswordCheckUtil.verify(current, record.passwordCheck)
            current.fill('\u0000')
            if (!valid) {
                tilCurrentPassword.error = getString(R.string.wrong_password)
                return
            }
        }

        val newPassword = editableToCharArray(etNewPassword)
        val confirm = editableToCharArray(etNewPasswordConfirm)
        when {
            newPassword.size < PasswordCheckUtil.MIN_PASSWORD_LENGTH -> {
                tilNewPassword.error = getString(R.string.password_too_short)
                newPassword.fill('\u0000')
                confirm.fill('\u0000')
                return
            }
            !newPassword.contentEquals(confirm) -> {
                tilNewPasswordConfirm.error = getString(R.string.password_mismatch)
                newPassword.fill('\u0000')
                confirm.fill('\u0000')
                return
            }
        }
        confirm.fill('\u0000')

        Prefs.setOrChangePassword(context, record, newPassword)
        newPassword.fill('\u0000')

        Toast.makeText(context, R.string.password_updated_resave_qr, Toast.LENGTH_LONG).show()
        (activity as? MainActivity)?.popFragment()
    }

    private fun editableToCharArray(editText: TextInputEditText): CharArray {
        val text = editText.text ?: return CharArray(0)
        val chars = CharArray(text.length)
        text.getChars(0, text.length, chars, 0)
        return chars
    }
}
