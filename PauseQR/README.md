# Pause QR – Apps per QR-Code blockieren

## So funktioniert's
1. App öffnen → **Bedienungshilfen öffnen** → „Pause QR“ aktivieren.
2. **Apps zum Blockieren auswählen** (z. B. Instagram, TikTok).
3. **QR-Code in Galerie speichern** und ausdrucken oder auf ein anderes Gerät schicken. Den Code weit weg legen.
4. **Pause starten** → die ausgewählten Apps zeigen jetzt das Overlay „Pause“.
5. Zum Entsperren auf **QR scannen** tippen (im Overlay oder in der App) und den Code scannen.

Während der Pause sind die App-Auswahl und der QR-Code gesperrt bzw. ausgeblendet.

## Bauen
- Ordner in **Android Studio** öffnen (File → Open). Gradle-Sync läuft automatisch.
- Falls Android Studio meldet, dass der Gradle-Wrapper fehlt: Vorschlag annehmen.
- Auf dem Handy starten (USB-Debugging) oder über Build → Build APK(s) eine APK bauen.
- Mindestens Android 10.

## Wichtig
- **Android 13+:** Bei per APK installierten Apps ist der Bedienungshilfe-Schalter zuerst ausgegraut.
  Lösung: Einstellungen → Apps → Pause QR → ⋮ (oben rechts) → **„Eingeschränkte Einstellungen zulassen“**.
- **Notausgang, falls der QR-Code weg ist:** Den Bedienungshilfe-Dienst in den Einstellungen ausschalten
  oder die App deinstallieren. Die App ist ein Werkzeug für Selbstdisziplin, kein Kinderschutz.
- Manche Hersteller (Xiaomi, Huawei, Samsung) beenden Hintergrunddienste aggressiv. Falls die Sperre
  „vergisst“ zu greifen: Akku-Optimierung für Pause QR ausschalten.

## Aufbau
| Datei | Aufgabe |
|---|---|
| `MainActivity.kt` | Status, QR anzeigen/speichern/neu erzeugen, Sperren, Scannen |
| `AppSelectActivity.kt` | Liste der installierten Apps mit Häkchen |
| `BlockerService.kt` | Bedienungshilfe-Dienst, legt das Overlay über blockierte Apps |
| `Prefs.kt` | Speichert Geheimcode, Sperrstatus und App-Liste |
| `res/layout/overlay_block.xml` | Das Pause-Overlay |
