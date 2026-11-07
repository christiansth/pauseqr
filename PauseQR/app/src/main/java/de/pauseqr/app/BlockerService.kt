package de.pauseqr.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.TextView

/**
 * Bedienungshilfe-Dienst: bekommt mit, wenn eine App in den Vordergrund kommt.
 * Ist die Pause aktiv und die App blockiert, wird ein Vollbild-Overlay darübergelegt.
 */
class BlockerService : AccessibilityService() {

    private var overlay: View? = null
    private lateinit var wm: WindowManager

    // Fenster dieser Pakete ändern nichts am Overlay (Statusleiste, Benachrichtigungen …)
    private val ignored = setOf("com.android.systemui")

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg in ignored) return

        if (Prefs.isLocked(this) && pkg in Prefs.getBlockedApps(this)) {
            showOverlay(pkg)
        } else {
            hideOverlay()
        }
    }

    private fun showOverlay(pkg: String) {
        if (!::wm.isInitialized) wm = getSystemService(WINDOW_SERVICE) as WindowManager

        val view = overlay ?: LayoutInflater
            .from(ContextThemeWrapper(this, R.style.Theme_PauseQR))
            .inflate(R.layout.overlay_block, null)
            .also { v ->
                v.findViewById<Button>(R.id.btnOverlayScan).setOnClickListener {
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            .putExtra(MainActivity.EXTRA_SCAN, true)
                    )
                    hideOverlay()
                }
                v.findViewById<Button>(R.id.btnOverlayHome).setOnClickListener {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    hideOverlay()
                }
            }

        view.findViewById<TextView>(R.id.tvOverlayApp).text =
            getString(R.string.overlay_app_blocked, appLabel(pkg))

        if (overlay == null) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            wm.addView(view, params)
            overlay = view
        }
    }

    private fun hideOverlay() {
        overlay?.let {
            try { wm.removeView(it) } catch (_: Exception) { }
        }
        overlay = null
    }

    private fun appLabel(pkg: String): String = try {
        val info = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        pkg
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }
}
