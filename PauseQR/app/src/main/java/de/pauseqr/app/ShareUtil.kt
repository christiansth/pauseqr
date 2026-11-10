package de.pauseqr.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File

/**
 * Teilt den QR-Code als Bilddatei über das Android-Sharesheet. Liefert keinerlei Zusage über den
 * Erfolg der Zustellung – ob ein Versand tatsächlich stattfand, kann Android nicht bestätigen.
 * Ein Sicherungsstatus darf deshalb niemals allein aus dem Öffnen dieses Sheets abgeleitet werden.
 */
object ShareUtil {
    fun shareQrCode(context: Context, bitmap: Bitmap) {
        val file = File(context.cacheDir, "scanoff_qr_share.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }
}
