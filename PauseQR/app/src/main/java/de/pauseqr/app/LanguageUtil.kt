package de.pauseqr.app

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Sprachumschaltung über den offiziellen AndroidX-Mechanismus (`AppCompatDelegate`), synchron mit
 * den Android-13+-Systemeinstellungen. Die Auswahl wird von AppCompat selbst dauerhaft gespeichert
 * (siehe `autoStoreLocales` im Manifest) – es gibt bewusst keine eigene, parallele Persistenz hier.
 */
object LanguageUtil {
    const val GERMAN = "de"
    const val ENGLISH = "en"

    /** Setzt die App-Sprache sofort und dauerhaft; aktive Activities werden automatisch neu erstellt. */
    fun setLanguage(languageTag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
    }

    /**
     * Die aktuell wirksame Sprache: eine explizite Nutzerauswahl hat Vorrang; ohne eine solche
     * wird die tatsächlich aufgelöste Ressourcensprache verwendet (folgt der Gerätesprache, mit
     * Englisch als Rückfallebene für alle außer Deutsch – siehe values/ bzw. values-de/).
     */
    fun currentLanguage(context: Context): String {
        val explicit = AppCompatDelegate.getApplicationLocales()
        if (!explicit.isEmpty) {
            val tag = explicit[0]?.language
            if (tag == GERMAN || tag == ENGLISH) return tag
        }
        return if (context.resources.configuration.locales[0].language == GERMAN) GERMAN else ENGLISH
    }
}
