package de.pauseqr.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/** Übersicht: Status, Schnellzugriff, Pause starten/beenden und der eigene ScanOff-Code. */
class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@registerForActivityResult
        handleScan(text)
    }

    private lateinit var serviceBox: View
    private lateinit var btnAccessibility: Button
    private lateinit var statusCard: MaterialCardView
    private lateinit var statusBadge: FrameLayout
    private lateinit var ivStatusIcon: ImageView
    private lateinit var tvStatus: TextView
    private lateinit var tvStatusDesc: TextView
    private lateinit var cardAccessibility: MaterialCardView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var cardSelectApps: MaterialCardView
    private lateinit var cardScanTest: MaterialCardView
    private lateinit var btnPrimary: Button
    private lateinit var rowAppsCount: View
    private lateinit var tvAppsCount: TextView
    private lateinit var qrSection: View
    private lateinit var qrExistsGroup: View
    private lateinit var qrEmptyGroup: View
    private lateinit var qrErrorGroup: View
    private lateinit var tvUsernameReadonly: TextView
    private lateinit var ivQr: ImageView
    private lateinit var btnRegenerateQr: Button
    private lateinit var btnCreateQr: Button
    private lateinit var btnRetryQr: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        serviceBox = view.findViewById(R.id.serviceBox)
        btnAccessibility = view.findViewById(R.id.btnAccessibility)
        statusCard = view.findViewById(R.id.statusCard)
        statusBadge = view.findViewById(R.id.statusBadge)
        ivStatusIcon = view.findViewById(R.id.ivStatusIcon)
        tvStatus = view.findViewById(R.id.tvStatus)
        tvStatusDesc = view.findViewById(R.id.tvStatusDesc)
        cardAccessibility = view.findViewById(R.id.cardAccessibility)
        tvAccessibilityStatus = view.findViewById(R.id.tvAccessibilityStatus)
        cardSelectApps = view.findViewById(R.id.cardSelectApps)
        cardScanTest = view.findViewById(R.id.cardScanTest)
        btnPrimary = view.findViewById(R.id.btnPrimary)
        rowAppsCount = view.findViewById(R.id.rowAppsCount)
        tvAppsCount = view.findViewById(R.id.tvAppsCount)
        qrSection = view.findViewById(R.id.qrSection)
        qrExistsGroup = view.findViewById(R.id.qrExistsGroup)
        qrEmptyGroup = view.findViewById(R.id.qrEmptyGroup)
        qrErrorGroup = view.findViewById(R.id.qrErrorGroup)
        tvUsernameReadonly = view.findViewById(R.id.tvUsernameReadonly)
        ivQr = view.findViewById(R.id.ivQr)
        btnRegenerateQr = view.findViewById(R.id.btnRegenerateQr)
        btnCreateQr = view.findViewById(R.id.btnCreateQr)
        btnRetryQr = view.findViewById(R.id.btnRetryQr)

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        cardAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        cardSelectApps.setOnClickListener { openAppSelect() }
        rowAppsCount.setOnClickListener { openAppSelect() }
        cardScanTest.setOnClickListener { startScan() }
        btnPrimary.setOnClickListener {
            if (Prefs.isLocked(requireContext())) startScan() else lock()
        }
        btnRegenerateQr.setOnClickListener { openQrCreate() }
        btnCreateQr.setOnClickListener { openQrCreate() }
        btnRetryQr.setOnClickListener { refresh() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    fun startScanFromHost() = startScan()

    private fun openAppSelect() {
        if (!Prefs.isLocked(requireContext())) {
            startActivity(Intent(requireContext(), AppSelectActivity::class.java))
        }
    }

    private fun openQrCreate() {
        (activity as? MainActivity)?.pushFragment(QrCreateFragment())
    }

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
        when (Prefs.verifyScan(context, text)) {
            ScanVerdict.INVALID -> {
                toast(R.string.scan_invalid)
                return
            }
            ScanVerdict.WRONG_CODE -> {
                toast(R.string.wrong_qr)
                return
            }
            ScanVerdict.MATCH -> {
                if (Prefs.isLocked(context)) {
                    Prefs.setLocked(context, false)
                    toast(R.string.unlocked)
                } else {
                    lock()
                }
                refresh()
            }
        }
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
        Prefs.incrementPauseCount(context)
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
            statusBadge.setBackgroundResource(R.drawable.bg_circle_red)
            ivStatusIcon.setImageResource(R.drawable.ic_lock_closed)
            tvStatus.text = getString(R.string.status_locked)
            tvStatusDesc.text = getString(R.string.status_locked_desc)
        } else {
            statusCard.setCardBackgroundColor(resources.getColor(R.color.green_bg, null))
            statusBadge.setBackgroundResource(R.drawable.bg_circle_green)
            ivStatusIcon.setImageResource(R.drawable.ic_check)
            tvStatus.text = getString(R.string.status_unlocked)
            tvStatusDesc.text = getString(R.string.status_unlocked_desc)
        }

        tvAccessibilityStatus.text = getString(
            if (serviceEnabled) R.string.status_active else R.string.status_inactive
        )
        tvAccessibilityStatus.setTextColor(
            resources.getColor(if (serviceEnabled) R.color.green else R.color.red, null)
        )

        cardSelectApps.alpha = if (locked) 0.5f else 1f
        cardSelectApps.isEnabled = !locked
        rowAppsCount.alpha = if (locked) 0.5f else 1f
        rowAppsCount.isEnabled = !locked

        btnPrimary.text = getString(if (locked) R.string.btn_pause_unlock else R.string.btn_pause_start)
        tvAppsCount.text = getString(R.string.apps_count, apps.size)

        qrSection.visibility = if (locked) View.GONE else View.VISIBLE
        if (!locked) refreshQrCard(context)
    }

    private fun refreshQrCard(context: android.content.Context) {
        when (val state = Prefs.loadQrCodeState(context)) {
            is QrCodeState.Existing -> {
                qrExistsGroup.visibility = View.VISIBLE
                qrEmptyGroup.visibility = View.GONE
                qrErrorGroup.visibility = View.GONE
                tvUsernameReadonly.text = state.record.username
                ivQr.setImageBitmap(QrUtil.makeQr(state.record, 800))
            }
            is QrCodeState.Empty -> {
                qrExistsGroup.visibility = View.GONE
                qrEmptyGroup.visibility = View.VISIBLE
                qrErrorGroup.visibility = View.GONE
            }
            is QrCodeState.Error -> {
                qrExistsGroup.visibility = View.GONE
                qrEmptyGroup.visibility = View.GONE
                qrErrorGroup.visibility = View.VISIBLE
            }
        }
    }

    private fun toast(res: Int) {
        context?.let { Toast.makeText(it, res, Toast.LENGTH_LONG).show() }
    }
}
