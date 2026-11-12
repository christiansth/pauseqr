package de.pauseqr.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Bedienungshilfe-Dienst: bekommt mit, wenn eine App in den Vordergrund kommt.
 * Ist die Pause aktiv und die App blockiert, legt ScanOff einen vollflächigen, schwarzen
 * Pausenbildschirm darüber. Der Wechsel zum Home-Bildschirm erfolgt ausschließlich, wenn der
 * Nutzer ausdrücklich den Button „Zum Startbildschirm“ antippt – niemals automatisch.
 */
class BlockerService : AccessibilityService() {

    companion object {
        private var instance: BlockerService? = null

        /**
         * Entfernt ein eventuell sichtbares Pausen-Overlay sofort (z. B. nach der
         * Notfall-Entsperrung), statt auf das nächste Accessibility-Event zu warten. Ohne
         * laufenden Dienst oder sichtbares Overlay passiert nichts – kein Absturz.
         */
        fun hideOverlayIfVisible() {
            instance?.hideOverlay()
        }
    }

    private var overlay: View? = null
    private lateinit var wm: WindowManager

    // Fenster dieser Pakete ändern nichts am Overlay (Statusleiste, Benachrichtigungen …)
    private val ignored = setOf("com.android.systemui")

    // Entprellung der Aufrufversuch-Erfassung (siehe recordAttemptIfNew): schützt zusätzlich zur
    // overlay==null-Prüfung gegen kurzzeitiges Event-Flackern für dasselbe Paket.
    private var lastAttemptPackage: String? = null
    private var lastAttemptAtElapsed: Long = 0L
    private val attemptDebounceMs = 1500L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg in ignored) return

        if (Prefs.isLocked(this) && pkg in Prefs.getBlockedApps(this)) {
            // Ein neuer Öffnungsversuch liegt genau dann vor, wenn das Overlay noch nicht sichtbar
            // ist – wiederholte Accessibility-Events bei bereits sichtbarem Overlay lösen hier
            // nichts aus. Verlässt der Nutzer die App (Overlay wird woanders entfernt) und öffnet
            // sie erneut, ist overlay wieder null und ein neuer Versuch zählt erneut.
            if (overlay == null) {
                recordAttemptIfNew(pkg)
            }
            showOverlay()
        } else {
            hideOverlay()
        }
    }

    /** Zusätzliche, zeitbasierte Entprellung über die overlay==null-Prüfung hinaus (siehe Feld-Doku). */
    private fun recordAttemptIfNew(pkg: String) {
        val now = SystemClock.elapsedRealtime()
        if (pkg == lastAttemptPackage && now - lastAttemptAtElapsed < attemptDebounceMs) return
        lastAttemptPackage = pkg
        lastAttemptAtElapsed = now

        val context = applicationContext
        Thread {
            val sessionId = PauseHistoryStore.getOpenSessionId(context) ?: return@Thread
            BlockedAttemptStore.recordAttempt(context, sessionId, pkg)
        }.start()
    }

    private fun showOverlay() {
        // Nur beim ERSTEN Erkennen der gesperrten App ausführen – wiederholte Accessibility-
        // Events für dieselbe gesperrte App dürfen das Overlay nicht erneut aufbauen (kein
        // Flackern, keine doppelten Overlay-Instanzen).
        if (overlay != null) return
        if (!::wm.isInitialized) wm = getSystemService(WINDOW_SERVICE) as WindowManager

        val view = LayoutInflater
            .from(ContextThemeWrapper(this, R.style.Theme_PauseQR))
            .inflate(R.layout.overlay_block, null)
            .also { v ->
                v.findViewById<View>(R.id.pauseScreenRoot).setOnClickListener {
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            .putExtra(MainActivity.EXTRA_SCAN, true)
                    )
                    hideOverlay()
                }
                v.findViewById<View>(R.id.btnOverlayHome).setOnClickListener {
                    // Reihenfolge ist wichtig: zuerst das Overlay sicher entfernen, danach erst
                    // zum Home-Bildschirm wechseln. Die Pause selbst bleibt dabei unangetastet.
                    hideOverlay()
                    try {
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    } catch (_: Exception) {
                        // Kein Absturz, falls die Systemaktion ausnahmsweise nicht verfügbar ist.
                    }
                }
            }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        )
        try {
            wm.addView(view, params)
            overlay = view
        } catch (_: Exception) {
            // Sicherheitsnetz: Fenster konnte nicht hinzugefügt werden (z. B. ungültiger Token) –
            // kein halb-initialisiertes Overlay zurücklassen.
            overlay = null
        }
    }

    private fun hideOverlay() {
        overlay?.let {
            try { wm.removeView(it) } catch (_: Exception) { }
        }
        overlay = null
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        hideOverlay()
        if (instance === this) instance = null
        super.onDestroy()
    }
}
