package com.skodadash.ultra

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.skodadash.ultra.databinding.ActivityTripDetailBinding
import java.text.SimpleDateFormat
import java.util.Locale

class TripDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTripDetailBinding
    private var currentTrip: TripData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTripDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val tripId = intent.getLongExtra("trip_id", -1)
        if (tripId == -1L) { finish(); return }

        val trip = TripStorage(this).getTripById(tripId)
        if (trip == null) { finish(); return }
        currentTrip = trip

        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY)
        val sdfShort = SimpleDateFormat("HH:mm:ss", Locale.GERMANY)

        binding.tvDetailTitle.text = "Fahrt ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(java.util.Date(trip.startTime))}"
        binding.tvDetailSubtitle.text = "${trip.getDrivingStyle()} - ${if (trip.isAuto) "Automatisch" else "Manuell"} - ${trip.events.size} Events"
        binding.tvDetailLevel.text = trip.getLevel().uppercase()

        binding.tvDetailStats.text = """
Distanz: ${String.format("%.2f km", trip.distanceMeters/1000)}
Dauer: ${trip.durationSec/3600}h ${trip.durationSec%3600/60}min ${trip.durationSec%60}sek
Max: ${trip.maxSpeedKmh.toInt()} km/h  Schnitt: ${trip.avgSpeedKmh.toInt()} km/h
Punkte: ${trip.pointCount} GPS Punkte
Start: ${sdf.format(java.util.Date(trip.startTime))}
Ende: ${trip.endTime?.let { sdf.format(java.util.Date(it)) } ?: "laufend"}
        """.trimIndent()

        binding.tvDetailScores.text = """
Sport Score: ${trip.sportScore} Punkte (neu - hohe Geschw. + G)
Gesamt Score: ${trip.score}
Eco Score: ${trip.ecoScore}/100
Effizienz: ${trip.efficiencyScore}/100

Bewertung: ${when {
            trip.sportScore >= 800 -> "Extrem sportlich - Rennstrecke Niveau"
            trip.sportScore >= 500 -> "Sehr sportlich - dynamische Fahrt"
            trip.sportScore >= 300 -> "Sportlich - zuegig unterwegs"
            trip.sportScore >= 150 -> "Dynamisch - flott"
            else -> "Entspannt - gemuetlich"
        }}

Punkte System Vollversion:
- 12 Pkt/km + 1.5 Pkt/Min
- Hohe Geschw: bis 120 Pkt fuer 200 km/h
- Hohe G: bis 100 Pkt fuer 1.3 G
- Wenig Bremsen: 60 Pkt bei 0 Bremsungen
- Kurven: 8 Pkt pro Kurve, 12 fuer scharfe
- Gas: 6 Pkt pro Beschleunigung
        """.trimIndent()

        binding.tvDetailG.text = """
Max G: ${String.format("%.2f", trip.maxG)} G
Max Beschleunigung: ${String.format("%.2f", trip.maxAccel)} m/s²
Max Bremsen: ${String.format("%.2f", trip.maxBrake)} m/s²
        """.trimIndent()

        val brakeCount = trip.events.count { it.type == "BRAKE" || it.type == "HARD_BRAKE" }
        val accelCount = trip.events.count { it.type == "ACCEL" || it.type == "HARD_ACCEL" }
        val cornerCount = trip.events.count { it.type == "CORNER" || it.type == "SHARP_CORNER" }
        val speedCount = trip.events.count { it.type == "SPEED" }

        binding.tvDetailAnalysis.text = """
Bremsen: $brakeCount x (${if (brakeCount <=2) "sehr wenig - Top" else if (brakeCount <=5) "wenig - gut" else "haeufig"})
Gas: $accelCount x Beschleunigungen
Kurven: $cornerCount x davon ${trip.events.count { it.type=="SHARP_CORNER" }} scharf
High Speed: $speedCount x ueber 120 km/h

Effizienz: ${trip.efficiencyScore}/100
${when {
            trip.efficiencyScore >= 80 -> "Sehr effizient sportlich"
            trip.efficiencyScore >= 60 -> "Effizient"
            else -> "Viele Stopps"
        }}

Stil: ${trip.getDrivingStyle()}
Level: ${trip.getLevel()}
        """.trimIndent()

        val eventsText = if (trip.events.isEmpty()) {
            "Keine besonderen Ereignisse - sehr gleichmaessige Fahrt\n\nTippe in der Karte auf Punkte fuer Details"
        } else {
            val sb = StringBuilder()
            sb.append("${trip.events.size} Ereignisse gesamt:\n\n")
            trip.events.groupBy { it.type }.forEach { (type, evts) ->
                val label = when(type) {
                    "ACCEL" -> "Beschleunigung"
                    "HARD_ACCEL" -> "Starke Beschleunigung"
                    "BRAKE" -> "Bremsen"
                    "HARD_BRAKE" -> "Starkes Bremsen"
                    "CORNER" -> "Kurve"
                    "SHARP_CORNER" -> "Scharfe Kurve"
                    "SPEED" -> "Hohe Geschwindigkeit"
                    else -> type
                }
                sb.append("$label: ${evts.size}x\n")
                evts.take(3).forEach { e ->
                    sb.append("  ${String.format("%.1f", e.value)} ${if (e.type.contains("SPEED")) "km/h" else "G/m/s²"} bei ${sdfShort.format(java.util.Date(e.time))} - ${e.speedKmh.toInt()} km/h\n")
                }
                if (evts.size > 3) sb.append("  ... und ${evts.size-3} weitere\n")
                sb.append("\n")
            }
            sb.append("Tippe in der Karte auf farbige Punkte fuer Details")
            sb.toString()
        }
        binding.tvDetailEvents.text = eventsText

        // Map
        binding.mapView.setTripData(trip.points, trip.events)
        binding.mapView.onEventSelected = { event ->
            val label = when(event.type) {
                "ACCEL" -> "Beschleunigung"
                "HARD_ACCEL" -> "Starke Beschleunigung"
                "BRAKE" -> "Bremsen"
                "HARD_BRAKE" -> "Starkes Bremsen - viel G"
                "CORNER" -> "Kurve"
                "SHARP_CORNER" -> "Scharfe Kurve - hohe G"
                "SPEED" -> "Hohe Geschwindigkeit"
                else -> event.type
            }
            binding.tvSelectedEvent.text = """
$label ausgewählt
Wert: ${String.format("%.2f", event.value)} ${if (event.type=="SPEED") "km/h" else "G / m/s²"}
Geschwindigkeit dabei: ${event.speedKmh.toInt()} km/h
Zeit: ${sdf.format(java.util.Date(event.time))}
Position: ${String.format("%.5f", event.lat)}, ${String.format("%.5f", event.lon)}

${when(event.type) {
                "HARD_ACCEL" -> "Sehr sportliche Beschleunigung - gibt viele Punkte"
                "HARD_BRAKE" -> "Starke Bremsung - wenig Bremsen gibt mehr Punkte, aber starke Bremsung zeigt G"
                "SHARP_CORNER" -> "Schnelle Kurve mit hoher G-Kraft - sportlich"
                "SPEED" -> "Hohe Geschwindigkeit gehalten - gibt viele Punkte"
                else -> "Tippe auf andere Punkte fuer Vergleich"
            }}
            """.trimIndent()
            binding.mapView.setSelectedEvent(event)
        }
        binding.mapView.onPointSelected = { point ->
            binding.tvSelectedEvent.text = """
GPS Punkt
${String.format("%.5f", point.lat)}, ${String.format("%.5f", point.lon)}
Geschwindigkeit: ${point.speedKmh.toInt()} km/h
Zeit: ${sdf.format(java.util.Date(point.time))}
Genauigkeit: ${point.accuracy.toInt()}m
            """.trimIndent()
        }

        binding.btnExportGpx.setOnClickListener {
            val file = GpxExporter.exportTrip(this, trip)
            if (file != null) {
                Toast.makeText(this, "GPX exportiert: ${file.name}", Toast.LENGTH_SHORT).show()
                GpxExporter.shareGpx(this, file)
            } else {
                Toast.makeText(this, "Export fehlgeschlagen", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnDeleteTrip.setOnClickListener {
            TripStorage(this).deleteTrip(trip.id)
            Toast.makeText(this, "Fahrt gelöscht", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnClose.setOnClickListener { finish() }
    }
}
