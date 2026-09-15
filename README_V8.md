# Dash Ultralite v8 - Vollversion

## Vollversion Features 2026-09-15

### UI Bugs gefixt
- **Memory Leak Fix**: `trackingJob` in MainActivity mit `isActive` Check, cancel in `onDestroy` und bei Tab Wechsel, kein infinite while mehr
- **RadioGroup Toast Spam**: `isInitializing` Flag in showSettings und Dashboard, Listener feuert nicht mehr bei initialem setChecked
- **Null Safety**: try/catch in updateTrackingButtons, btnRefresh disabled während Laden
- **Widget Config**: Initialisierung vor Listener, freie Hex Felder korrekt geladen
- **FileProvider**: Für GPX Export hinzugefügt, Manifest Provider + file_paths.xml
- **Layout IDs**: tvTrackerStats, tvLevel, tvStatsDetail, tvTileEvents hinzugefügt, alle IDs konsistent

### Neuer Sport Score - Vollversion
**User Wunsch: mehr Punkte für wenig bremsen, viel Geschwindigkeit, hohe G-Kräfte**

`TripData.calculateSportScore()` neu:
- Basis: 12 Pkt/km + 1.5 Pkt/Min
- Geschwindigkeit: bis 120 Pkt für 200 km/h max, bis 60 Pkt für 120 km/h Schnitt
- G-Kräfte: bis 100 Pkt für 1.3 G, 70 für 1.1 G, 45 für 0.9 G
- Beschleunigung: bis 50 Pkt für 5 m/s²
- **Wenig Bremsen Bonus**: 60 Pkt bei 0 Bremsungen, 40 bei <=2, 20 bei <=5, -20 bei >20, -3 pro harter Bremsung
- Kurven: 8 Pkt pro Kurve, 12 Pkt pro scharfe Kurve
- Gas: 6 Pkt pro Beschleunigung
- Speed Events: 4 Pkt pro High-Speed Event >120 km/h
- Flüssig schnell Bonus: 25 Pkt wenn <2 Events/km
- Auto Bonus: 8 Pkt

EcoScore bleibt separat für sparsames Fahren, EfficiencyScore neu für wenig Bremsen + optimaler Schnitt.

Level System: Einsteiger <300, Geuebt <1000, Fortgeschritten <2500, Profi <5000, Meister <10000, Legende >=10000

### Interaktive Karte mit klickbaren Events - Vollversion

**TripMapView.kt** - Custom View 340dp:
- Zeichnet GPS Track als braune Linie mit Schatten
- Bounds mit 8% Padding, Aspect Ratio bewahrt, Grid Linien
- Start Marker grün S, Ziel Marker rot Z
- Events als farbige Kreise mit weißem Rand:
  - ACCEL hellgrün, HARD_ACCEL dunkelgrün
  - BRAKE orange, HARD_BRAKE rot
  - CORNER hellblau, SHARP_CORNER dunkelblau
  - SPEED lila mit km/h Label
- Selected Event gelb umrandet, 22dp Radius
- Touch: Findet nächstes Event in 80dp, ruft onEventSelected, zeigt Details in tvSelectedEvent
- Touch auf GPS Punkt: zeigt Lat/Lon, Speed, Accuracy
- Info: Luftlinie Distanz, N Pfeil, Legende unter Karte

**TripDetailActivity.kt** Vollversion:
- Map oben, darunter Stats, Sport Score Card braun, zwei Spalten Fahrdynamik + Analyse, Events Liste, GPX Export + Löschen Buttons
- Selected Event Text zeigt Typ, Wert, Speed, Zeit, Position + sportliche Bewertung
- GPX Export via GpxExporter, share Intent
- Delete Trip via TripStorage.deleteTrip

**Neue Features:**
- GpxExporter.kt: Exportiert TripPoints als <trkseg> und Events als <wpt> mit Zeit, Speed, Höhe
- TripStorage: getTotalDuration, getAverageSpeed, getBestTrip, getLevel, getLevelProgress, deleteTrip, keep last 100 trips, 3000 Punkte max, 500 gespeichert
- TripService: 0.8s GPS, 0.5m min, 7 Event Typen, Throttle 1.5s, nur bei >8 km/h, HARD_* Schwellen 4.5
- Dashboard: tvTrackerStats zeigt Punkte + Level + km
- Tracker Liste: Level, Progress, Stats Detail, Best Trip, 100 Trips, Events/GPS/Eff in Kachel

### Design
- Creme beige, keine Emojis, Material3, 100% Design
- Karten Hintergrund #FFFEF9, braune Linie #5D4037
- Vollversion Label überall

### Build
- Version 4.0.0 Code 8
- 13 Kotlin Files
- viewBinding true, min 26 target 34
- GitHub Action ohne setup-android@v3
