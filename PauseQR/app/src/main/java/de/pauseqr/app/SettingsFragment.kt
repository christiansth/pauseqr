package de.pauseqr.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

/** Einstellungen: Bedienungshilfe-Status, blockierte Apps, QR-Code-Verwaltung, Sprache. */
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private companion object {
        const val DIMMED_ALPHA = 0.38f
        const val NORMAL_ALPHA = 1f
    }

    private lateinit var tvServiceStatus: TextView
    private lateinit var btnAccessibility: Button
    private lateinit var tvAppsCount: TextView
    private lateinit var btnSelectApps: Button
    private lateinit var btnManageQr: Button
    private lateinit var cardLanguage: MaterialCardView
    private lateinit var tvLanguageValue: TextView
    private lateinit var cardEmergencyUnlock: MaterialCardView
    private lateinit var contentEmergencyUnlock: View
    private lateinit var tvAppVersion: TextView
    private lateinit var cardBackup: MaterialCardView
    private lateinit var btnResetStats: Button
    private lateinit var switchAttemptTracking: MaterialSwitch
    private lateinit var btnResetAttempts: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvServiceStatus = view.findViewById(R.id.tvServiceStatus)
        btnAccessibility = view.findViewById(R.id.btnAccessibility)
        tvAppsCount = view.findViewById(R.id.tvAppsCount)
        btnSelectApps = view.findViewById(R.id.btnSelectApps)
        btnManageQr = view.findViewById(R.id.btnManageQr)
        cardLanguage = view.findViewById(R.id.cardLanguage)
        tvLanguageValue = view.findViewById(R.id.tvLanguageValue)
        cardEmergencyUnlock = view.findViewById(R.id.cardEmergencyUnlock)
        contentEmergencyUnlock = view.findViewById(R.id.contentEmergencyUnlock)
        tvAppVersion = view.findViewById(R.id.tvAppVersion)
        // Immer aus der Gradle-Konfiguration gelesen, niemals fest hinterlegt.
        tvAppVersion.text = BuildConfig.VERSION_NAME
        cardBackup = view.findViewById(R.id.cardBackup)
        btnResetStats = view.findViewById(R.id.btnResetStats)
        switchAttemptTracking = view.findViewById(R.id.switchAttemptTracking)
        btnResetAttempts = view.findViewById(R.id.btnResetAttempts)

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
        cardBackup.setOnClickListener {
            (activity as? MainActivity)?.pushFragment(BackupFragment())
        }
        cardEmergencyUnlock.setOnClickListener {
            val context = context ?: return@setOnClickListener
            if (Prefs.isLocked(context)) {
                (activity as? MainActivity)?.pushFragment(EmergencyUnlockFragment())
            } else {
                Toast.makeText(context, R.string.emergency_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
        btnResetStats.setOnClickListener { confirmResetStats() }

        switchAttemptTracking.isChecked = BlockedAttemptStore.isTrackingEnabled(requireContext())
        switchAttemptTracking.setOnCheckedChangeListener { _, isChecked ->
            BlockedAttemptStore.setTrackingEnabled(requireContext(), isChecked)
        }
        btnResetAttempts.setOnClickListener { confirmResetAttempts() }
    }

    /**
     * Entfernt ausschließlich die Statistik gesperrter App-Aufrufe – Pausen, QR-Codes und
     * App-Auswahl bleiben unberührt (BlockedAttemptStore.clearAll fasst nur diese Daten an).
     */
    private fun confirmResetAttempts() {
        val context = context ?: return
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.settings_attempt_tracking_section)
            .setMessage(R.string.attempt_reset_confirm_message)
            .setPositiveButton(R.string.settings_attempt_reset_button) { _, _ ->
                BlockedAttemptStore.clearAll(context)
                Toast.makeText(context, R.string.attempt_reset_success, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Entfernt ausschließlich die Pausenhistorie (und damit die daraus berechneten Erfolge) –
     * persönliche QR-Codes, Freunde, App-Auswahlen und Einstellungen bleiben unberührt. Eine
     * laufende Pause wird dadurch weder beendet noch entsperrt (clearAll fasst KEY_LOCKED nie an).
     */
    private fun confirmResetStats() {
        val context = context ?: return
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.settings_stats_reset_section)
            .setMessage(R.string.stats_reset_confirm_message)
            .setPositiveButton(R.string.stats_reset_confirm_button) { _, _ ->
                PauseHistoryStore.clearAll(context)
                Toast.makeText(context, R.string.stats_reset_success, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
        val hasPause = Prefs.isLocked(context)
        btnSelectApps.isEnabled = !hasPause
        // Die Karte bleibt immer klickbar, damit bei fehlender Pause der erklärende Toast
        // angezeigt werden kann – nur Icon/Beschriftung werden ausgegraut.
        contentEmergencyUnlock.alpha = if (hasPause) NORMAL_ALPHA else DIMMED_ALPHA

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
