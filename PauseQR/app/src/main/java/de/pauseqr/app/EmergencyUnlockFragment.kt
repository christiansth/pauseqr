package de.pauseqr.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator

/**
 * Notfall-Entsperrung: separate Ansicht (erreichbar nur über die Einstellungen, nie über das
 * Dashboard oder den Sperrbildschirm), die eine aktive Pause nach 10 Sekunden ununterbrochenem
 * Gedrückthalten beendet – ohne den persönlichen QR-Code zu scannen. Nutzt dieselbe zentrale
 * Entsperrfunktion ([Prefs.unlock]) wie ein erfolgreicher QR-Code-Scan.
 */
class EmergencyUnlockFragment : Fragment(R.layout.fragment_emergency_unlock) {

    private companion object {
        const val HOLD_DURATION_MS = 10_000L
        const val TICK_INTERVAL_MS = 50L
    }

    private lateinit var tvNoPauseMessage: TextView
    private lateinit var btnHold: MaterialButton
    private lateinit var progress: LinearProgressIndicator
    private lateinit var tvCountdown: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var pressStartTime: Long = -1L

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (pressStartTime < 0) return
            val elapsed = SystemClock.elapsedRealtime() - pressStartTime
            val percent = ((elapsed.toFloat() / HOLD_DURATION_MS) * 100f).coerceIn(0f, 100f)
            progress.setProgressCompat(percent.toInt(), true)

            if (elapsed >= HOLD_DURATION_MS) {
                onHoldCompleted()
            } else {
                val remainingSeconds = ((HOLD_DURATION_MS - elapsed) / 1000L + 1).coerceIn(1, 10)
                tvCountdown.text = getString(R.string.emergency_countdown, remainingSeconds)
                handler.postDelayed(this, TICK_INTERVAL_MS)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvNoPauseMessage = view.findViewById(R.id.tvNoPauseMessage)
        btnHold = view.findViewById(R.id.btnEmergencyHold)
        progress = view.findViewById(R.id.progressEmergency)
        tvCountdown = view.findViewById(R.id.tvCountdown)

        // Bewusst per OnTouchListener statt OnClickListener umgesetzt: ein einfacher Klick darf
        // niemals entsperren, nur ein ununterbrochenes 10-Sekunden-Halten.
        btnHold.setOnTouchListener { v, event ->
            if (!btnHold.isEnabled) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startHold()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val withinBounds = event.x >= 0 && event.x <= v.width && event.y >= 0 && event.y <= v.height
                    if (!withinBounds) cancelHold()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.performClick()
                    cancelHold()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    cancelHold()
                    true
                }
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onPause() {
        super.onPause()
        // Deckt zugleich "App in den Hintergrund", "Bildschirm gesperrt" und "Ansicht verlassen"
        // ab: in all diesen Fällen ruft Android onPause() auf der Fragment-Hierarchie auf.
        cancelHold()
    }

    private fun refresh() {
        val context = context ?: return
        val hasPause = Prefs.isLocked(context)
        tvNoPauseMessage.visibility = if (hasPause) View.GONE else View.VISIBLE
        btnHold.isEnabled = hasPause
        cancelHold()
    }

    private fun startHold() {
        val context = context ?: return
        if (!btnHold.isEnabled || !Prefs.isLocked(context)) return
        if (pressStartTime >= 0) return
        pressStartTime = SystemClock.elapsedRealtime()
        handler.removeCallbacks(tickRunnable)
        handler.post(tickRunnable)
    }

    private fun cancelHold() {
        pressStartTime = -1L
        handler.removeCallbacks(tickRunnable)
        if (isAdded) {
            progress.setProgressCompat(0, false)
            tvCountdown.text = ""
        }
    }

    private fun onHoldCompleted() {
        val context = context ?: return
        pressStartTime = -1L
        handler.removeCallbacks(tickRunnable)

        // Button sofort sperren, damit die Entsperraktion nicht mehrfach ausgelöst werden kann.
        btnHold.isEnabled = false

        Prefs.unlock(context)
        BlockerService.hideOverlayIfVisible()

        tvCountdown.text = ""
        progress.setProgressCompat(0, false)
        Toast.makeText(context, R.string.emergency_success, Toast.LENGTH_LONG).show()

        refresh()
    }
}
