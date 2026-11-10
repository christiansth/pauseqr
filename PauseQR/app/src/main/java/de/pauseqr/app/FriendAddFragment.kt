package de.pauseqr.app

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.text.DateFormat
import java.util.Date

/**
 * „Freund hinzufügen“: erkennt einen fremden ScanOff-Code per Kamera-Scan oder Galerie-Import,
 * zeigt eine Vorschau und speichert ihn erst nach ausdrücklicher Bestätigung dauerhaft.
 */
class FriendAddFragment : Fragment(R.layout.fragment_friend_add) {

    private lateinit var invalidGroup: View
    private lateinit var tvInvalidMessage: TextView
    private lateinit var previewGroup: View
    private lateinit var tvPreviewUsername: TextView
    private lateinit var tvPreviewCreatedAt: TextView
    private lateinit var ivPreviewQr: ImageView
    private lateinit var tvPreviewNotice: TextView
    private lateinit var btnScanFriend: Button
    private lateinit var btnImportFriend: Button
    private lateinit var btnSaveAsFriend: Button

    private var pendingPayload: String? = null

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@registerForActivityResult
        handleScannedPayload(text)
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) handleImportedImage(uri)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        invalidGroup = view.findViewById(R.id.invalidGroup)
        tvInvalidMessage = view.findViewById(R.id.tvInvalidMessage)
        previewGroup = view.findViewById(R.id.previewGroup)
        tvPreviewUsername = view.findViewById(R.id.tvPreviewUsername)
        tvPreviewCreatedAt = view.findViewById(R.id.tvPreviewCreatedAt)
        ivPreviewQr = view.findViewById(R.id.ivPreviewQr)
        tvPreviewNotice = view.findViewById(R.id.tvPreviewNotice)
        btnScanFriend = view.findViewById(R.id.btnScanFriend)
        btnImportFriend = view.findViewById(R.id.btnImportFriend)
        btnSaveAsFriend = view.findViewById(R.id.btnSaveAsFriend)

        btnScanFriend.setOnClickListener { startScan() }
        btnImportFriend.setOnClickListener {
            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        btnSaveAsFriend.setOnClickListener { saveFriend() }
    }

    private fun startScan() {
        val options = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt(getString(R.string.scan_prompt))
            .setBeepEnabled(false)
            .setOrientationLocked(false)
        scanLauncher.launch(options)
    }

    private fun handleImportedImage(uri: Uri) {
        val context = context ?: return
        val bitmap = try {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        }
        val payload = bitmap?.let { QrUtil.decodeFromBitmap(it) }
        if (payload == null) {
            showInvalid(getString(R.string.import_not_found))
            return
        }
        handleScannedPayload(payload)
    }

    /** Zentrale Erkennung: prüft Format/Version/UUID/Benutzername/Zeitstempel über [QrPayload]. */
    private fun handleScannedPayload(payload: String) {
        val context = context ?: return
        val decoded = QrPayload.decode(payload)
        if (decoded == null) {
            showInvalid(getString(R.string.scan_not_valid))
            return
        }

        pendingPayload = payload
        tvPreviewUsername.text = decoded.username
        tvPreviewCreatedAt.text = getString(R.string.created_at_label, formatDate(decoded.createdAt))
        ivPreviewQr.setImageBitmap(QrUtil.makeQr(decoded, 800))

        val own = Prefs.getQrCode(context)
        val isOwn = own != null && own.version != 0 && own.id == decoded.id
        val isDuplicate = !isOwn && FriendsStore.findById(context, decoded.id) != null

        when {
            isOwn -> showNotice(R.string.friend_own_code, enableSave = false)
            isDuplicate -> showNotice(R.string.friend_duplicate, enableSave = false)
            else -> showNotice(null, enableSave = true)
        }

        invalidGroup.visibility = View.GONE
        previewGroup.visibility = View.VISIBLE
    }

    private fun showNotice(messageRes: Int?, enableSave: Boolean) {
        if (messageRes != null) {
            tvPreviewNotice.text = getString(messageRes)
            tvPreviewNotice.visibility = View.VISIBLE
        } else {
            tvPreviewNotice.visibility = View.GONE
        }
        btnSaveAsFriend.isEnabled = enableSave
    }

    private fun showInvalid(message: String) {
        pendingPayload = null
        previewGroup.visibility = View.GONE
        tvInvalidMessage.text = message
        invalidGroup.visibility = View.VISIBLE
    }

    private fun saveFriend() {
        val context = context ?: return
        val payload = pendingPayload ?: return
        when (FriendsStore.addFriend(context, payload)) {
            AddFriendResult.SAVED -> {
                Toast.makeText(context, R.string.friend_added, Toast.LENGTH_LONG).show()
                (activity as? MainActivity)?.popFragment()
            }
            AddFriendResult.DUPLICATE -> toast(R.string.friend_duplicate)
            AddFriendResult.OWN_CODE -> toast(R.string.friend_own_code)
            AddFriendResult.INVALID -> toast(R.string.scan_not_valid)
        }
    }

    private fun formatDate(millis: Long): String =
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

    private fun toast(res: Int) {
        context?.let { Toast.makeText(it, res, Toast.LENGTH_LONG).show() }
    }
}
