package de.pauseqr.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Environment
import android.provider.MediaStore
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.journeyapps.barcodescanner.BarcodeEncoder

/** Erzeugt den Pause-QR (mit optionalem Namen in der Mitte) und speichert ihn in der Galerie. */
object QrUtil {

    fun makeQr(context: Context, size: Int): Bitmap {
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H)
        val qr = BarcodeEncoder().encodeBitmap(Prefs.getSecret(context), BarcodeFormat.QR_CODE, size, size, hints)

        val username = Prefs.getUsername(context)
        if (username.isBlank()) return qr

        val result = qr.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val cx = size / 2f
        val cy = size / 2f

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            textSize = size * 0.09f
        }
        val textBounds = Rect()
        textPaint.getTextBounds(username, 0, username.length, textBounds)

        val boxPadding = size * 0.04f
        val boxWidth = (textBounds.width() + boxPadding * 2).coerceAtMost(size * 0.75f)
        val boxHeight = textBounds.height() + boxPadding * 2
        val box = RectF(cx - boxWidth / 2, cy - boxHeight / 2, cx + boxWidth / 2, cy + boxHeight / 2)
        val cornerRadius = size * 0.02f

        canvas.drawRoundRect(box, cornerRadius, cornerRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        canvas.drawRoundRect(box, cornerRadius, cornerRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = size * 0.006f
        })

        val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(username, cx, textY, textPaint)

        return result
    }

    fun saveToGallery(context: Context, bitmap: Bitmap): Boolean {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "PauseQR_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PauseQR")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            context.contentResolver.openOutputStream(uri)?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
