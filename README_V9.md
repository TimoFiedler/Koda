# v9 Minimal Fix - Vollversion bereinigt

## Probleme aus v8 behoben

### 1. Tracking Daten falsch
**Vorher**: GPS + Network gemischt, Distanz doppelt gezählt, accuracy ignoriert, Sprünge >200m gezählt, avg Speed aus allen Speeds inkl 0
**Jetzt**: Nur GPS_PROVIDER, Filter:
- accuracy >30m ignoriert
- erste 3 Punkte Warmup ignoriert (GPS springt beim Start)
- Distanz nur 1..200m zwischen Punkten und accuracy <20m
- maxSpeed nur aus validen Speeds <300 km/h
- avgSpeed = Distanz/Zeit wenn >100m, sonst Mittel nur >2 km/h
- movingSpeed für bessere Schnitt Berechnung
- Low-pass Filter alpha 0.8 für Beschleunigung gegen Rauschen
- Events nur ab 10 km/h, 2s Drossel, Schwellen 3.0 m/s²

Resultat: Distanz, Max, Schnitt stimmen, keine GPS Sprünge mehr.

### 2. Karte kein Hintergrund, passt nicht, zu viel Daten
**Vorher**: Canvas.drawColor, Grid, Speed Labels, Compass, Distanz Text, Legende mit vielen Farben, 340dp, viel Text
**Jetzt**: TripMapView minimal:
- Hintergrund #FFFEF9 mit roundRect 24dp Radius
- Subtiles Grid nur 3 Linien #F0E6D2
- Path mit Hintergrund Schatten 14dp #E8DCC6 + Hauptlinie 8dp #5D4037
- Events nur 10dp farbige Punkte mit weißem Rand, kein Text, keine km/h Labels
- Start grün 10dp, Ziel rot 10dp, weißer Halo 14dp
- Bounds mit 15% Padding, mindestens 0.0008 Grad (~80m), Aspect Ratio bewahrt, zentriert
- Touch 70dp Radius nur Events, kein Point Select
- Keine Compass, keine Distanz, keine Legende unten - nur "Tippe auf Punkte"
- Höhe 300dp, passt in Card mit 20dp Radius

Detail Activity minimal:
- Nur 3 Cards: Daten (Distanz, Dauer, Max, Schnitt, GPS Punkte), Score (Punkte, Stil, G max, Bremsen Anzahl), Events (Anzahl pro Typ)
- Kein Eco, kein Effizienz, keine G Details extra, keine Map Text Liste
- Selected Event zeigt nur Typ, Wert, Speed, Zeit kurz, Lat/Lon

### 3. Autoerkennung startet nicht
**Vorher**: GPS + Network gemischt, Network liefert 0 km/h und resettet speedCounter, threshold async geladen, 5 Messungen nötig, 90 *2s =3 Min Stop
**Jetzt**:
- Nur GPS_PROVIDER, accuracy <40m, hasSpeed Pflicht
- threshold aus Cache synchron geladen, async update
- 3 Messungen über Schwelle = 4.5s -> startet sicher
- Stop nach 80 *1.5s = 2 Min Stand <4 km/h
- isWaiting Flag für korrekte Notification Texte
- BootReceiver neu: ACTION_BOOT_COMPLETED + QUICKBOOT, startet AutoTripService wenn in Settings aktiviert
- MainActivity onCreate startet Auto Service automatisch wenn enabled
- Settings speichert threshold auch in settings_cache für sofortigen Start
- Notification Texte minimal und klar

### 4. Slider kaputt und falsch angezeigt
**Vorher**: MaterialSwitch im braunen Card, thumb unsichtbar, MaterialSwitch default Farben passen nicht zu braunem Hintergrund, Initialisierung feuert Listener
**Jetzt**:
- Dashboard und Settings nutzen androidx.appcompat.widget.SwitchCompat
- Dashboard: thumbTint @color/cream_surface, trackTint #80FFFEF9 - sichtbar auf braun
- Settings: thumbTint @color/brown_primary
- initializing Flag verhindert Toast Spam beim Laden
- SwitchCompat ist robuster als MaterialSwitch, kein Rendering Bug

### 5. Widget Seite weg
**Vorher**: 4 Tabs Dashboard, Tracker, Widget, Profil - Widget Info Seite unnötig
**Jetzt**: activity_main.xml nur 3 Tabs Dashboard, Tracker, Profil
- MainActivity tab handling nur 0,1,2
- showWidgetInfo Methode entfernt
- SkodaWidgetProvider bleibt vorhanden für bestehende Widgets, aber keine UI mehr in App
- Weniger Code, minimal

### 6. Farben werden nicht angewendet
**Vorher**: Custom Farben in DataStore gespeichert, aber nie auf UI angewendet, nur in Widget, Preview fehlte
**Jetzt**:
- ColorHelper.kt: Parst Hex mit/ohne #, Namen (creme, sand, brown etc), toHex, parseOrDefault
- ThemeHelper.kt: Lädt Farben, speichert in theme_cache SharedPreferences für schnellen Zugriff
- MainActivity applyCustomColors(): Liest customBgHex, customAccentHex, appTheme, widgetAccent, parst, setzt root background, speichert in theme_cache, wird in onCreate und onResume aufgerufen
- Settings: etCustomBg und etCustomAccent speichern jetzt in saveCustomBgHex/saveCustomAccentHex + kompatibel auch in appTheme/widgetAccent
- Preview View viewColorPreview: GradientDrawable mit bg Farbe und accent Border, updatePreview() bei Focus Change
- TripDetailActivity: Liest bg_color aus theme_cache und setzt root background, accent auf Close Button
- Item Tiles: Score Farbe = accent aus theme_cache
- Validierung: Ungültige Hex zeigt Toast "Ungültige Farbe", erlaubt auch Namen

### Minimal Prinzip
- Nur nötige Daten: Distanz, Dauer, Max, Schnitt, Punkte, Stil, G max, Bremsen Anzahl, Events Anzahl
- Keine Eco, Effizienz, Level Progress etc in Detail - nur in Liste kurz
- Karte minimal ohne viel Text
- Kein Widget Tab
- Farben sofort sichtbar

Build 4.1.0 Code 9
