package de.pauseqr.app

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment

/** Statistiken: einfache Kennzahlen zur bisherigen Nutzung. */
class StatsFragment : Fragment(R.layout.fragment_stats) {

    private lateinit var tvPauseCount: TextView
    private lateinit var tvAppsCount: TextView
    private lateinit var tvServiceStatus: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvPauseCount = view.findViewById(R.id.tvPauseCount)
        tvAppsCount = view.findViewById(R.id.tvAppsCount)
        tvServiceStatus = view.findViewById(R.id.tvServiceStatus)
    }

    override fun onResume() {
        super.onResume()
        val context = context ?: return
        tvPauseCount.text = Prefs.getPauseCount(context).toString()
        tvAppsCount.text = Prefs.getBlockedApps(context).size.toString()

        val serviceEnabled = ServiceUtil.isBlockerServiceEnabled(context)
        tvServiceStatus.text = getString(
            if (serviceEnabled) R.string.status_active else R.string.status_inactive
        )
        tvServiceStatus.setTextColor(
            resources.getColor(if (serviceEnabled) R.color.green else R.color.red, null)
        )
    }
}
