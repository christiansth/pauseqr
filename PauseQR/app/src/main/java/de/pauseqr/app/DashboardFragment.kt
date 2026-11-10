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
    private lateinit var statusBadge: FrameLayout
    private lateinit var ivStatusIcon: ImageView
    private lateinit var tvStatus: TextView
    private lateinit var tvStatusDesc: TextView
    private lateinit var cardAccessibility: MaterialCardView
    private lateinit var cardSelectApps: MaterialCardView
    private lateinit var cardScanTest: MaterialCardView
    private lateinit var btnPrimary: Button
    private lateinit var tvAppsCount: TextView
    private lateinit var qrSection: View
    private lateinit var qrExistsGroup: View
    private lateinit var qrEmptyGroup: View
    private lateinit var qrErrorGroup: View
    private lateinit var tvUsernameReadonly: TextView
    private lateinit var ivQr: ImageView
    private lateinit var qrBackupPendingGroup: View
    private lateinit var qrBackupDoneGroup: View
    private lateinit var btnSaveQr: Button
    private lateinit var btnShareQr: Button
    private lateinit var btnConfirmBackup: Button
    private lateinit var btnRegenerateQr: Button
    private lateinit var btnCreateQr: Button
    private lateinit var btnRetryQr: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        serviceBox = view.findViewById(R.id.serviceBox)
        btnAccessibility = view.findViewById(R.id.btnAccessibility)
        statusBadge = view.findViewById(R.id.statusBadge)
        ivStatusIcon = view.findViewById(R.id.ivStatusIcon)
        tvStatus = view.findViewById(R.id.tvStatus)
        tvStatusDesc = view.findViewById(R.id.tvStatusDesc)
        cardAccessibility = view.findViewById(R.id.cardAccessibility)
        cardSelectApps = view.findViewById(R.id.cardSelectApps)
        cardScanTest = view.findViewById(R.id.cardScanTest)
        btnPrimary = view.findViewById(R.id.btnPrimary)
        tvAppsCount = view.findViewById(R.id.tvAppsCount)
        qrSection = view.findViewById(R.id.qrSection)
        qrExistsGroup = view.findViewById(R.id.qrExistsGroup)
        qrEmptyGroup = view.findViewById(R.id.qrEmptyGroup)
        qrErrorGroup = view.findViewById(R.id.qrErrorGroup)
        tvUsernameReadonly = view.findViewById(R.id.tvUsernameReadonly)
        ivQr = view.findViewById(R.id.ivQr)
        qrBackupPendingGroup = view.findViewById(R.id.qrBackupPendingGroup)
        qrBackupDoneGroup = view.findViewById(R.id.qrBackupDoneGroup)
        btnSaveQr = view.findViewById(R.id.btnSaveQr)
        btnShareQr = view.findViewById(R.id.btnShareQr)
        btnConfirmBackup = view.findViewById(R.id.btnConfirmBackup)
        btnRegenerateQr = view.findViewById(R.id.btnRegenerateQr)
        btnCreateQr = view.findViewById(R.id.btnCreateQr)
        btnRetryQr = view.findViewById(R.id.btnRetryQr)

        btnAccessibility.setOnClickListener { openAccessibilitySettingsWithConsent() }
        cardAccessibility.setOnClickListener { openAccessibilitySettingsWithConsent() }
        cardSelectApps.setOnClickListener { openAppSelect() }
        cardScanTest.setOnClickListener { startScan() }
        btnPrimary.setOnClickListener {
            if (Prefs.isLocked(requireContext())) startScan() else lock()
        }
        btnSaveQr.setOnClickListener { saveQrToGallery() }
        btnShareQr.setOnClickListener { shareQrCode() }
        btnConfirmBackup.setOnClickListener { confirmBackup() }
        btnRegenerateQr.setOnClickListener { openQrCreate() }
        btnCreateQr.setOnClickListener { openQrCreate() }
        btnRetryQr.setOnClickListener { refresh() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    fun startScanFromHost() = startScan()

    private fun openAccessibilitySettingsWithConsent() {
        val context = context ?: return
        ServiceUtil.showAccessibilityConsent(context) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

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
        when (val verdict = Prefs.verifyScan(context, text)) {
            ScanVerdict.Invalid -> toast(R.string.scan_invalid)
            ScanVerdict.Unrecognized -> toast(R.string.scan_unrecognized)
            ScanVerdict.FriendDisabled -> toast(R.string.scan_friend_disabled)
            ScanVerdict.OwnCodeMatch -> {
                if (Prefs.isLocked(context)) {
                    Prefs.unlock(context)
                    toast(R.string.unlocked)
                } else {
                    lock()
                }
                refresh()
            }
            is ScanVerdict.FriendMatch -> {
                // Ein Freund-Code darf eine Pause nur beenden, niemals starten – das bleibt dem
                // eigenen Code mit der vollständigen PauseGate-Prüfung vorbehalten.
                if (Prefs.isLocked(context)) {
                    Prefs.unlock(context)
                    Toast.makeText(
                        context,
                        getString(R.string.unlocked_by_friend, verdict.friendUsername),
                        Toast.LENGTH_LONG
                    ).show()
                    refresh()
                } else {
                    toast(R.string.friend_cannot_start_pause)
                }
            }
        }
    }

    /**
     * Zentrale Startlogik: Eine Pause wird nur gestartet, wenn [PauseGate.canStartPause] zustimmt.
     * Die UI (deaktivierter Button) spiegelt diese Prüfung nur – hier wird sie unabhängig davon
     * erneut ausgeführt, bevor tatsächlich gesperrt wird.
     */
    private fun lock() {
        val context = context ?: return

        if (!PauseGate.canStartPause(context)) {
            when {
                Prefs.getQrCode(context) == null -> toast(R.string.need_qr_code)
                !Prefs.isQrBackedUp(context) -> toast(R.string.need_qr_backup)
                !ServiceUtil.isBlockerServiceEnabled(context) -> toast(R.string.need_service)
                else -> toast(R.string.need_apps)
            }
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
            statusBadge.setBackgroundResource(R.drawable.bg_circle_red)
            ivStatusIcon.setImageResource(R.drawable.ic_lock_closed)
            tvStatus.text = getString(R.string.status_locked)
            tvStatusDesc.text = getString(R.string.status_locked_desc)
        } else {
            statusBadge.setBackgroundResource(R.drawable.bg_circle_green)
            ivStatusIcon.setImageResource(R.drawable.ic_check)
            tvStatus.text = getString(R.string.status_unlocked)
            tvStatusDesc.text = getString(R.string.status_unlocked_desc)
        }

        cardSelectApps.alpha = if (locked) 0.5f else 1f
        cardSelectApps.isEnabled = !locked

        btnPrimary.text = getString(if (locked) R.string.btn_pause_unlock else R.string.btn_pause_start)
        val canStart = PauseGate.canStartPause(context)
        btnPrimary.isEnabled = locked || canStart
        tvAppsCount.text = if (!locked && !canStart) {
            getString(R.string.pause_disabled_hint)
        } else {
            getString(R.string.apps_count, apps.size)
        }

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

                val backedUp = Prefs.isQrBackedUp(context)
                qrBackupPendingGroup.visibility = if (backedUp) View.GONE else View.VISIBLE
                qrBackupDoneGroup.visibility = if (backedUp) View.VISIBLE else View.GONE
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

    private fun saveQrToGallery() {
        val context = context ?: return
        val record = Prefs.getQrCode(context) ?: return
        val success = QrUtil.saveToGallery(context, QrUtil.makeQr(record, 1024), record.username)
        if (success) {
            Prefs.markQrBackedUp(context, QrBackupStatus.SAVED_TO_DEVICE)
            toast(R.string.qr_saved)
            refresh()
        } else {
            toast(R.string.qr_save_failed)
        }
    }

    private fun shareQrCode() {
        val context = context ?: return
        val record = Prefs.getQrCode(context) ?: return
        // Öffnet nur das Sharesheet – dies allein zählt noch nicht als Sicherung.
        ShareUtil.shareQrCode(context, QrUtil.makeQr(record, 1024))
    }

    private fun confirmBackup() {
        val context = context ?: return
        if (Prefs.getQrCode(context) == null) return
        Prefs.markQrBackedUp(context, QrBackupStatus.USER_CONFIRMED_SHARED)
        toast(R.string.qr_backup_confirmed)
        refresh()
    }

    private fun toast(res: Int) {
        context?.let { Toast.makeText(it, res, Toast.LENGTH_LONG).show() }
    }
}
