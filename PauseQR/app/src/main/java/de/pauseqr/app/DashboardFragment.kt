package de.pauseqr.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/** Übersicht: Status, Schnellzugriff, Pause starten/beenden und der eigene QR-Code. */
class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@registerForActivityResult
        handleScan(text)
    }

    private lateinit var serviceBox: View
    private lateinit var btnAccessibility: Button
    private lateinit var statusCard: MaterialCardView
    private lateinit var ivStatusIcon: ImageView
    private lateinit var tvStatus: TextView
    private lateinit var tvStatusDesc: TextView
    private lateinit var cardAccessibility: MaterialCardView
    private lateinit var cardSelectApps: MaterialCardView
    private lateinit var cardScanTest: MaterialCardView
    private lateinit var tvPauseTitle: TextView
    private lateinit var tvPauseDesc: TextView
    private lateinit var btnPrimary: Button
    private lateinit var tvAppsCount: TextView
    private lateinit var qrSection: View
    private lateinit var etUsername: TextInputEditText
    private lateinit var btnGenerateQr: Button
    private lateinit var ivQr: ImageView
    private lateinit var btnSaveQr: Button
    private lateinit var btnNewQr: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        serviceBox = view.findViewById(R.id.serviceBox)
        btnAccessibility = view.findViewById(R.id.btnAccessibility)
        statusCard = view.findViewById(R.id.statusCard)
        ivStatusIcon = view.findViewById(R.id.ivStatusIcon)
        tvStatus = view.findViewById(R.id.tvStatus)
        tvStatusDesc = view.findViewById(R.id.tvStatusDesc)
        cardAccessibility = view.findViewById(R.id.cardAccessibility)
        cardSelectApps = view.findViewById(R.id.cardSelectApps)
        cardScanTest = view.findViewById(R.id.cardScanTest)
        tvPauseTitle = view.findViewById(R.id.tvPauseTitle)
        tvPauseDesc = view.findViewById(R.id.tvPauseDesc)
        btnPrimary = view.findViewById(R.id.btnPrimary)
        tvAppsCount = view.findViewById(R.id.tvAppsCount)
        qrSection = view.findViewById(R.id.qrSection)
        etUsername = view.findViewById(R.id.etUsername)
        btnGenerateQr = view.findViewById(R.id.btnGenerateQr)
        ivQr = view.findViewById(R.id.ivQr)
        btnSaveQr = view.findViewById(R.id.btnSaveQr)
        btnNewQr = view.findViewById(R.id.btnNewQr)

        etUsername.setText(Prefs.getUsername(requireContext()))

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        cardAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        cardSelectApps.setOnClickListener {
            if (!Prefs.isLocked(requireContext())) {
                startActivity(Intent(requireContext(), AppSelectActivity::class.java))
            }
        }
        cardScanTest.setOnClickListener { startScan() }
        btnPrimary.setOnClickListener {
            if (Prefs.isLocked(requireContext())) startScan() else lock()
        }
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

    fun startScanFromHost() = startScan()

    private fun startScan() {
        val options = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt(getString(R.string.scan_prompt))
            .setBeepEnabled(false)
            .setOrientationLocked(false)
        scanLauncher.launch(options)
    }

    private fun handleScan(text: String) {
        val context = context ?: return
        if (text != Prefs.getSecret(context)) {
            toast(R.string.wrong_qr)
            return
        }
        if (Prefs.isLocked(context)) {
            Prefs.setLocked(context, false)
            toast(R.string.unlocked)
        } else {
            lock()
        }
        refresh()
    }

    private fun lock() {
        val context = context ?: return
        if (!ServiceUtil.isBlockerServiceEnabled(context)) {
            toast(R.string.need_service)
            return
        }
        if (Prefs.getBlockedApps(context).isEmpty()) {
            toast(R.string.need_apps)
            return
        }
        Prefs.setLocked(context, true)
        toast(R.string.locked)
        refresh()
    }

    private fun refresh() {
        val context = context ?: return
        val locked = Prefs.isLocked(context)
        val apps = Prefs.getBlockedApps(context)
        val serviceEnabled = ServiceUtil.isBlockerServiceEnabled(context)

        serviceBox.visibility = if (serviceEnabled) View.GONE else View.VISIBLE

        if (locked) {
            statusCard.setCardBackgroundColor(resources.getColor(R.color.red_bg, null))
            ivStatusIcon.setImageResource(R.drawable.ic_lock_closed)
            ivStatusIcon.setColorFilter(resources.getColor(R.color.red, null))
            tvStatus.text = getString(R.string.status_locked)
            tvStatus.setTextColor(resources.getColor(R.color.red, null))
            tvStatusDesc.text = getString(R.string.status_locked_desc)
        } else {
            statusCard.setCardBackgroundColor(resources.getColor(R.color.green_bg, null))
            ivStatusIcon.setImageResource(R.drawable.ic_lock_open)
            ivStatusIcon.setColorFilter(resources.getColor(R.color.green, null))
            tvStatus.text = getString(R.string.status_unlocked)
            tvStatus.setTextColor(resources.getColor(R.color.green, null))
            tvStatusDesc.text = getString(R.string.status_unlocked_desc)
        }

        cardSelectApps.alpha = if (locked) 0.5f else 1f
        cardSelectApps.isEnabled = !locked

        if (locked) {
            tvPauseTitle.text = getString(R.string.pause_end_title)
            tvPauseDesc.text = getString(R.string.pause_end_desc)
            btnPrimary.text = getString(R.string.btn_pause_unlock)
        } else {
            tvPauseTitle.text = getString(R.string.pause_start_title)
            tvPauseDesc.text = getString(R.string.pause_start_desc)
            btnPrimary.text = getString(R.string.btn_pause_start)
        }
        tvAppsCount.text = getString(R.string.apps_count, apps.size)

        qrSection.visibility = if (locked) View.GONE else View.VISIBLE
        if (!locked) ivQr.setImageBitmap(QrUtil.makeQr(context, 800))
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
