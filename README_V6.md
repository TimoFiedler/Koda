# SkodaDash Ultra v6 - Premium Creme Edition - 100% Design

## NEU in v6 - Alle Wünsche umgesetzt

### 1. Fahrt manuell UND automatisch
**Vorher:** Nur manuell starten
**Jetzt:**
- **Manuell:** Im Dashboard Button "Fahrt manuell starten"
- **Automatisch:** Neuer Service `AutoTripService`
  - Läuft im Hintergrund mit Notification "Auto-Erkennung aktiv"
  - Erkennt Fahrt ab einstellbarer Schwelle (10 / 15 / 25 km/h)
  - Startet automatisch nach 5 Messungen über Schwelle (ca. 10 Sekunden)
  - Stoppt automatisch nach 3 Minuten Stillstand (<5 km/h)
  - Unterscheidung im Fahrtenbuch: Auto vs Manuell
  - Aktivierbar im Dashboard und in Einstellungen (Switch)
  - Benötigt GPS Berechtigung

**Code:** `AutoTripService.kt` + erweitert `TripService.kt` mit `ACTION_START_AUTO` und `isAutoStarted` Flag

### 2. Widget Editor - Voll konfigurierbar mit Farben
**Vorher:** 6 Toggles + 3 Hintergründe
**Jetzt: Premium Editor mit 5 Kategorien**

**Inhalte (6 Schalter):**
- Fahrzeugname, Ladezustand/Tank, Reichweite, Kilometerstand, Verriegelung, Ladestatus

**Layout (3+3+3 Optionen):**
- Darstellung: Kompakt (nur Wichtiges), Detailliert (alle Infos mit Labels), Minimal (nur Prozent)
- Ecken: Klein, Mittel, Gross
- Schriftgrösse: Klein, Mittel, Gross

**Farben:**
- Hintergrund: 5 Optionen - Creme (warm hell), Sand (helles Beige), Mocca (kühles Beige), Dunkel Braun, Transparent
- Akzent: 5 Optionen - Braun klassisch, Gold warm elegant, Sage grün natürlich, Olive erdig, Graphit modern minimal
- Textfarben automatisch angepasst an Hintergrund (hell/dunkel)

**Speicherung:** Pro Widget ID in `widget_config_<id>` - jedes Widget kann anders aussehen

### 3. Einstellungen - Farbe einstellbar
**Neue Einstellungsseite mit 4 Sektionen:**

**Fahrzeug:**
- FIN + API-Key Anzeige

**Antriebsart (fixiert Auswahl):**
- Elektro: Zeigt Akku % und Ladezustand
- Verbrenner: Zeigt Tank % und Reichweite
- Hybrid/PHEV: Zeigt beides
- Auswahl wird gespeichert und beeinflusst Dashboard Labels + Widget Anzeige
- Im Login bereits wählbar, in Einstellungen änderbar

**Design:**
- App Farbschema: Creme, Sand, Mocca, Graphit
- 4 Themes mit unterschiedlichen Hintergrundtönen
- Wird in DataStore gespeichert

**Fahrtenbuch:**
- Automatische Erkennung an/aus (Switch)
- Schwelle: 10 / 15 / 25 km/h (RadioGroup)
- Erklärung wie Auto-Erkennung funktioniert

### 4. Fixe die Auswahl
- Alle RadioGroups und Switches speichern jetzt korrekt
- Widget Config: Alle 5 Gruppen (Inhalte, Layout, Ecken, Text, Farben) werden korrekt gelesen und gespeichert
- Engine Type: In Login und Settings konsistent, wird in widget_data gespeichert für Widget Anzeige
- Auto Trip: Switch in Dashboard und Settings synchronisiert

### 5. Elektrisch vs Verbrenner
- Nutzer wählt im Login: Elektro / Verbrenner / Hybrid
- SettingsRepository: `engine_type` (electric/combustion/hybrid)
- Dashboard: Label "Ladezustand" vs "Tank", Badge zeigt Antriebsart
- Widget: Zeigt "$battery% Tank" vs "$battery%" vs "$battery% Akku" je nach Typ
- API Parsing bleibt gleich, aber Anzeige angepasst

### 6. 100% Design - Premium Creme
**Farben erweitert:**
- Neue Palette mit 20+ Farben: cream_background #FFF8E7, beige_light #F5E6C8, brown_primary #8B7355, text_primary #3E2723, accent_gold #C5A880, accent_sage #9CAF88 etc.
- Theme Variationen: theme_creme_bg, theme_sand_bg, theme_mocca_bg, theme_graphite_bg

**Layouts:**
- activity_main: Premium Header mit Titel + Gold Akzent Linie, TabLayout mit braunem Indicator
- layout_dashboard: Status Card mit Online Dot + Engine Badge, Haupt Card mit Batterie/Range nebeneinander + Divider, Charging Text, Refresh Button outlined, Fahrtenbuch Card in braun mit Auto Status
- layout_login: Willkommen Card mit Gold Linie, Engine Type RadioGroup horizontal, Hilfe Card in beige_light
- layout_settings: 4 Sektionen mit Cards, RadioGroups mit Padding, Info Card, Logout outlined
- activity_widget_config: 3 Hauptkategorien (Inhalte, Layout, Farben) mit Untergruppen, ScrollView, Premium Save Button
- widget_skoda: 20dp rounded, dynamische Farben

**Keine Emojis, kein Debug - nur elegante Typografie, uppercase Labels, LetterSpacing, feine Borders**

## Installation

### GitHub Actions (empfohlen)
1. Neues GitHub Repo erstellen
2. Inhalt von skoda-ultralite/ hochladen (nicht ZIP selbst, sondern entpackt)
3. Actions Tab -> Build APK startet automatisch (fixed workflow ohne setup-android)
4. Artifact "SkodaDash-APK" herunterladen
5. APK auf Handy installieren

### Lokal Android Studio
1. Android Studio -> Open -> skoda-ultralite Ordner
2. Warten auf Gradle Sync
3. Build -> Build APK
4. APK aus app/build/outputs/apk/debug/

## Nutzung
1. API-Key aus MySkoda App: Profil -> Einstellungen -> Drittanbieter-Zugriff -> Key erstellen
2. In App: Key + FIN eingeben + Antriebsart wählen
3. Dashboard: Daten laden, Fahrt manuell starten oder Auto-Erkennung aktivieren
4. Widget: Homescreen -> Widgets -> SkodaDash -> Editor öffnet sich -> Farben/Layout anpassen
5. Einstellungen: Antriebsart, Design, Auto-Erkennung konfigurieren

## Technische Details
- Min SDK 26, Target 34, Compile 34
- Permissions: INTERNET, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, FOREGROUND_SERVICE, POST_NOTIFICATIONS
- Services: TripService (foreground location), AutoTripService (foreground location)
- Storage: DataStore für Settings, SharedPreferences für Widget Data + Trips JSON
- API: public.api.connect.skoda-auto.cz mit X-API-Key

Version 2.0.0 - Premium Creme - September 2026
