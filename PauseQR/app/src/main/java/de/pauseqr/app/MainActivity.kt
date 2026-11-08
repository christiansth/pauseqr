package de.pauseqr.app

import android.content.ComponentName
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SCAN = "extra_scan"
    }

    private lateinit var tvStatus: TextView
    private lateinit var serviceBox: View
    private lateinit var btnAccessibility: Button
    private lateinit var btnSelectApps: Button
    private lateinit var tvAppsCount: TextView
    private lateinit var btnPrimary: Button
    private lateinit var btnScan: Button
    private lateinit var qrSection: View
    private lateinit var ivQr: ImageView
    private lateinit var btnSaveQr: Button
    private lateinit var btnNewQr: Button

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@registerForActivityResult
        handleScan(text)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        serviceBox = findViewById(R.id.serviceBox)
        btnAccessibility = findViewById(R.id.btnAccessibility)
        btnSelectApps = findViewById(R.id.btnSelectApps)
        tvAppsCount = findViewById(R.id.tvAppsCount)
        btnPrimary = findViewById(R.id.btnPrimary)
        btnScan = findViewById(R.id.btnScan)
        qrSection = findViewById(R.id.qrSection)
        ivQr = findViewById(R.id.ivQr)
        btnSaveQr = findViewById(R.id.btnSaveQr)
        btnNewQr = findViewById(R.id.btnNewQr)

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        btnSelectApps.setOnClickListener {
            startActivity(Intent(this, AppSelectActivity::class.java))
        }
        btnPrimary.setOnClickListener {
            if (Prefs.isLocked(this)) startScan() else lock()
        }
        btnScan.setOnClickListener { startScan() }
        btnSaveQr.setOnClickListener { saveQrToGallery() }
        btnNewQr.setOnClickListener { confirmNewQr() }

        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_SCAN, false)) {
            startScan()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_SCAN, false)) startScan()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    // ---------- Logik ----------

    private fun startScan() {
        val options = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt(getString(R.string.scan_prompt))
            .setBeepEnabled(false)
            .setOrientationLocked(false)
        scanLauncher.launch(options)
    }

    /** Erkennt den eigenen QR-Code und schaltet die Pause um. */
    private fun handleScan(text: String) {
        val expected = Prefs.getSecret(this)
        if (text != expected) {
            Toast.makeText(this, "DEBUG:\nGescannt: '$text'\nErwartet: '$expected'", Toast.LENGTH_LONG).show()
            return
        }
        if (Prefs.isLocked(this)) {
            Prefs.setLocked(this, false)
            toast(R.string.unlocked)
        } else {
            lock()
        }
        refresh()
    }

    private fun lock() {
        if (!isServiceEnabled()) {
            toast(R.string.need_service)
            return
        }
        if (Prefs.getBlockedApps(this).isEmpty()) {
            toast(R.string.need_apps)
            return
        }
        Prefs.setLocked(this, true)
        toast(R.string.locked)
        refresh()
    }

    private fun refresh() {
        val locked = Prefs.isLocked(this)
        val apps = Prefs.getBlockedApps(this)

        tvStatus.setText(if (locked) R.string.status_locked else R.string.status_unlocked)
        tvStatus.setTextColor(getColor(if (locked) R.color.red else R.color.green))

        serviceBox.visibility = if (isServiceEnabled()) View.GONE else View.VISIBLE

        tvAppsCount.text = getString(R.string.apps_count, apps.size)
        btnSelectApps.isEnabled = !locked

        btnPrimary.setText(if (locked) R.string.btn_unlock else R.string.btn_lock)
        btnScan.visibility = if (locked) View.GONE else View.VISIBLE

        // Während der Pause wird der QR-Code nicht angezeigt – sonst wäre es zu einfach.
        qrSection.visibility = if (locked) View.GONE else View.VISIBLE
        if (!locked) ivQr.setImageBitmap(makeQr(800))
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = ComponentName(this, BlockerService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    // ---------- QR ----------

    private fun makeQr(size: Int): Bitmap =
        BarcodeEncoder().encodeBitmap(Prefs.getSecret(this), BarcodeFormat.QR_CODE, size, size)

    private fun saveQrToGallery() {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "PauseQR_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PauseQR")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException()
            contentResolver.openOutputStream(uri)?.use {
                makeQr(1024).compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            toast(R.string.qr_saved)
        } catch (e: Exception) {
            toast(R.string.qr_save_failed)
        }
    }

    private fun confirmNewQr() {
        AlertDialog.Builder(this)
            .setTitle(R.string.new_qr_title)
            .setMessage(R.string.new_qr_message)
            .setPositiveButton(R.string.new_qr_confirm) { _, _ ->
                Prefs.newSecret(this)
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_LONG).show()
}
