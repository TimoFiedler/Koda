# SkodaDash Ultra v5 - Creme Beige Edition

## Highlights v5

### Design
- Komplett in Creme Beige (#FFF8E7 / #FFFEF9 / #8B7355 / #3E2723)
- Keine Emojis, keine Debug-Anzeigen
- Material3 Cards mit 16-20dp Radius, feine Border #E8DDC7
- Elegante Typografie, uppercase Labels, sanfte Farben

### Widget vollständig konfigurierbar
- 6 Schalter: Name, Batterie %, Reichweite, Kilometerstand, Verriegelung, Ladestatus
- 3 Hintergründe: Hell Creme, Dunkel Braun, Transparent
- Jedes Widget speichert eigene Config (widget_config_<id>)
- Schöne Darstellung ohne Emojis

### GitHub Actions Fix
Fehler `Failed to find package 'tools'` kam von `android-actions/setup-android@v3`.
Fix: Action entfernt. ubuntu-latest hat Android SDK bereits vorinstalliert.
Workflow jetzt nur:
- actions/checkout@v4
- actions/setup-java@v4 (JDK 17)
- gradle/actions/setup-gradle@v3

### API Fix aus v4
Korrektes nested Parsing für Skoda Public API:
- battery: vehicle.charging.status.battery.stateOfChargeInPercent
- range: battery.remainingCruisingRangeInMeters /1000 oder fuelStatus.totalRangeInKm
- odometer: vehicle.odometer.mileageInKm
- lock: status.overall.doorsLocked/locked/reliableLockStatus
- chargingState: status.state + chargePowerInKw

## Installation
1. ZIP entpacken
2. Als GitHub Repo pushen oder in Android Studio öffnen
3. GitHub Actions baut automatisch APK (Artifact SkodaDash-APK)
4. Lokal: Android Studio -> Build APK

## Nutzung
- API-Key aus MySkoda App (Profil -> Einstellungen -> Drittanbieter-Zugriff)
- FIN/VIN eingeben
- Dashboard zeigt Daten, Widget kann platziert und konfiguriert werden
- Fahrtenbuch: Fahrt starten/beenden, GPS 1Hz + G-Sensor

Version 1.1 - Creme Edition - September 2026
