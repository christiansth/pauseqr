package de.pauseqr.app

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment

/** Über die App: statische Infoseite. Versionsnummer stammt aus der Gradle-Konfiguration. */
class AboutFragment : Fragment(R.layout.fragment_about) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.tvAboutVersion).text =
            getString(R.string.about_version, BuildConfig.VERSION_NAME)
    }
}
