# Dash Ultralite v7 - Premium Fahrten Tracker

## Neu in v7 (2026-09-15)

### 1. Freie Farbwahl
- **App**: In Einstellungen Hex Farben frei eingebbar: `creme`, `sand`, `mocca`, `graphite` oder eigene Hex wie `#FFF8E7`, `#8B7355`
- **Widget**: Im Widget Editor eigene Hintergrundfarbe, Akzentfarbe und Textfarbe als Hex frei konfigurierbar + Presets
- **Speicherung**: Via DataStore und SharedPreferences pro Widget

### 2. Fahrten Tracker - Verlauf als Kacheln
- **Umbenannt**: Ueberall von Fahrtenbuch -> Fahrten Tracker (Notification, UI, Tabs)
- **Kacheln**: `item_trip_tile.xml` - jede Fahrt eigene Card mit Datum, Typ (Auto/Manuell), Distanz, Dauer, Punkte, Eco Score, Max Speed
- **Click**: Oeffnet `TripDetailActivity` mit:
  - Stats: Distanz, Dauer, Max/Schnitt, GPS Punkte
  - Scores: Punkte + Eco Score 0-100 mit Bewertung
  - G-Kraefte: Max G, Beschleunigung, Bremsen
  - Ereignisse: Gruppiert nach Typ ACCEL/BRAKE/CORNER/SPEED mit Zeit und Wert
  - Karte: Start/Ende Koordinaten + Liste der ersten 10 Punkte mit Speed, Anzahl Punkte, Hinweis auf GPX Export
- **Storage**: `TripStorage` speichert letzte 500 Punkte pro Trip als JSON Array + Events unbegrenzt, `getTotalScore()`, `getTotalDistance()`, `getTripById()`

### 3. Mehr Widget Einstellungen
- Inhalte: 6 Switches (Name, Batterie/Tank, Reichweite, Odo, Lock, Charging)
- Layout: Kompakt/Detailliert/Minimal
- Ecken: Klein/Mittel/Gross
- Schrift: Klein/Mittel/Gross
- Farben: 5 Presets + 3 freie Hex Felder
- Live Preview via SkodaWidgetProvider.updateWidget

### 4. Profil - FIN und API Key editierbar
- `layout_settings.xml`: etFinSettings + etApiKeySettings mit Save Button btnSaveProfile
- `tvRateStatus` zeigt Rate Limit Status
- `etCustomBg` + `etCustomAccent` freie Farben
- Speicherung via SettingsRepository DataStore

### 5. Rate Limit Schutz
- `RateLimiter.kt`: 20 Anfragen pro Stunde sliding window, SharedPreferences `rate_limit` mit timestamps
- `canMakeRequest()`, `getRemaining()`, `getStatusText()`, `recordRequest()`
- `SkodaApi.kt`: Prueft vor Request `RateLimiter.canMakeRequest()`, wirft Exception mit verbleibender Zeit, `recordRequest()` nach 200, behandelt 429
- Dashboard + Settings zeigen `tvRateLimit` / `tvRateStatus` mit "X/20 verbleibend" oder "Reset in Y min"

### 6. Punktesystem
- `TripData.calculateScore()`:
  - 10 Punkte pro km
  - 1 Punkt pro Minute
  - 20 Punkte wenn maxG < 0.3, 10 wenn < 0.5
  - 15 Punkte wenn max 50-130 km/h
  - 25 Punkte wenn accel/brake < 2.0 (gleichmaessig)
  - 5 Punkte Auto Bonus
- `calculateEcoScore()`: Start 100, Abzuege fuer hohes G, hohe Beschleunigung, Bremsen, Speed >130, Schnitt >100
- Anzeige: Gesamtpunkte + Gesamtdistanz in `layout_trips.xml` tvTotalScore, pro Kachel Score/Eco

### 7. Design
- Creme beige Style beibehalten, keine Emojis
- 100% Material3 Cards, abgerundete Ecken
- Brauner Primary #5D4037

## Architektur
- TripService sammelt TripPoint 1Hz GPS + TripEvent via Sensor (linear accel fuer ACCEL/BRAKE/CORNER, SPEED via GPS)
- AutoTripService startet bei Bewegung > Schwelle (10/15/25 km/h), stoppt nach 3 Min Stillstand
- Alle Toast.makeText mit this@MainActivity qualifiziert (Fix fuer compile Fehler)

## Build
- `gradle assembleDebug` via GitHub Actions ohne setup-android@v3
- viewBinding true, minSdk 26, target 34
- Version 3.0.0 Code 7
