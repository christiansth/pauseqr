package de.pauseqr.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment

/** Einstellungen: Bedienungshilfe-Status, blockierte Apps, Zugang zur QR-Code-Verwaltung. */
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private lateinit var tvServiceStatus: TextView
    private lateinit var btnAccessibility: Button
    private lateinit var tvAppsCount: TextView
    private lateinit var btnSelectApps: Button
    private lateinit var btnManageQr: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvServiceStatus = view.findViewById(R.id.tvServiceStatus)
        btnAccessibility = view.findViewById(R.id.btnAccessibility)
        tvAppsCount = view.findViewById(R.id.tvAppsCount)
        btnSelectApps = view.findViewById(R.id.btnSelectApps)
        btnManageQr = view.findViewById(R.id.btnManageQr)

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        btnSelectApps.setOnClickListener {
            startActivity(Intent(requireContext(), AppSelectActivity::class.java))
        }
        btnManageQr.setOnClickListener {
            (activity as? MainActivity)?.pushFragment(QrFragment())
        }
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
    }
}
