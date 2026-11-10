package de.pauseqr.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Environment
import android.provider.MediaStore
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.journeyapps.barcodescanner.BarcodeEncoder

/**
 * Reine QR-Code-Erzeugung und -Darstellung (ScanOff-Branding). Enthält bewusst keinen Zugriff
 * auf [Prefs] – Generierung/Darstellung und Persistenz sind getrennt.
 */
object QrUtil {

    private const val SUBTITLE_COLOR = "#5F6368"

    /** Codiert [record] als QR-Bitmap und zeichnet das ScanOff-Branding in die Mitte. */
    fun makeQr(record: QrCodeRecord, size: Int): Bitmap {
        val payload = if (record.version == 0) record.id else QrPayload.encode(record)

        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H)
        val qr = BarcodeEncoder().encodeBitmap(payload, BarcodeFormat.QR_CODE, size, size, hints)
        return applyBranding(qr, record.username, size, payload.length)
    }

    private fun applyBranding(qr: Bitmap, username: String, size: Int, payloadLength: Int): Bitmap {
        val result = qr.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val cx = size / 2f
        val cy = size / 2f

        // Bei langen Payloads (dichtere Module) die Branding-Fläche automatisch verkleinern,
        // damit die Scanbarkeit Vorrang vor dem Branding behält.
        val densityShrink = (120f / payloadLength.toFloat()).coerceIn(0.55f, 1f)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            textSize = size * 0.075f * densityShrink
        }
        val hasUsername = username.isNotBlank()
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(SUBTITLE_COLOR)
            textAlign = Paint.Align.CENTER
            textSize = size * 0.045f * densityShrink
        }

        val title = "ScanOff"
        val titleWidth = titlePaint.measureText(title)
        val subWidth = if (hasUsername) subPaint.measureText(username) else 0f

        val titleAscent = -titlePaint.ascent()
        val titleDescent = titlePaint.descent()
        val subAscent = if (hasUsername) -subPaint.ascent() else 0f
        val subDescent = if (hasUsername) subPaint.descent() else 0f
        val lineGap = size * 0.012f

        val contentWidth = maxOf(titleWidth, subWidth)
        val contentHeight = titleAscent + titleDescent +
            (if (hasUsername) lineGap + subAscent + subDescent else 0f)

        val hPadding = size * 0.045f
        val vPadding = size * 0.025f
        // Zentrale Fläche bewusst klein halten – weit innerhalb der 30%-Toleranz von Level H
        // und deutlich von den drei Positionsmarkierungen in den Ecken entfernt.
        val boxWidth = (contentWidth + hPadding * 2).coerceAtMost(size * 0.42f)
        val boxHeight = contentHeight + vPadding * 2

        val box = RectF(cx - boxWidth / 2, cy - boxHeight / 2, cx + boxWidth / 2, cy + boxHeight / 2)
        val radius = size * 0.025f
        canvas.drawRoundRect(box, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })

        val titleBaseline = cy - contentHeight / 2f + titleAscent
        canvas.drawText(title, cx, titleBaseline, titlePaint)

        if (hasUsername) {
            val subBaseline = titleBaseline + titleDescent + lineGap + subAscent
            canvas.drawText(username, cx, subBaseline, subPaint)
        }

        return result
    }

    fun saveToGallery(context: Context, bitmap: Bitmap): Boolean {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "ScanOff_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ScanOff")
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
