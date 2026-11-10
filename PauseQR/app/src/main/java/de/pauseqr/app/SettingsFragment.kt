package de.pauseqr.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Einstellungen: Bedienungshilfe-Status, blockierte Apps, QR-Code-Verwaltung, Sprache. */
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private lateinit var tvServiceStatus: TextView
    private lateinit var btnAccessibility: Button
    private lateinit var tvAppsCount: TextView
    private lateinit var btnSelectApps: Button
    private lateinit var btnManageQr: Button
    private lateinit var cardLanguage: MaterialCardView
    private lateinit var tvLanguageValue: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvServiceStatus = view.findViewById(R.id.tvServiceStatus)
        btnAccessibility = view.findViewById(R.id.btnAccessibility)
        tvAppsCount = view.findViewById(R.id.tvAppsCount)
        btnSelectApps = view.findViewById(R.id.btnSelectApps)
        btnManageQr = view.findViewById(R.id.btnManageQr)
        cardLanguage = view.findViewById(R.id.cardLanguage)
        tvLanguageValue = view.findViewById(R.id.tvLanguageValue)

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        btnSelectApps.setOnClickListener {
            startActivity(Intent(requireContext(), AppSelectActivity::class.java))
        }
        btnManageQr.setOnClickListener {
            (activity as? MainActivity)?.pushFragment(QrFragment())
        }
        cardLanguage.setOnClickListener { showLanguagePicker() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val context = context ?: return
        val serviceEnabled = ServiceUtil.isBlockerServiceEnabled(context)
        tvServiceStatus.text = getString(
            if (serviceEnabled) R.string.settings_service_desc_on else R.string.settings_service_desc_off
        )
        tvAppsCount.text = getString(R.string.apps_count, Prefs.getBlockedApps(context).size)
        btnSelectApps.isEnabled = !Prefs.isLocked(context)

        tvLanguageValue.text = languageLabel(LanguageUtil.currentLanguage(context))
    }

    private fun languageLabel(languageTag: String): String =
        getString(if (languageTag == LanguageUtil.GERMAN) R.string.language_german else R.string.language_english)

    private fun showLanguagePicker() {
        val context = context ?: return
        val codes = arrayOf(LanguageUtil.GERMAN, LanguageUtil.ENGLISH)
        val labels = codes.map { languageLabel(it) }.toTypedArray()
        val selectedIndex = codes.indexOf(LanguageUtil.currentLanguage(context)).coerceAtLeast(0)

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.settings_language_section)
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                LanguageUtil.setLanguage(codes[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
