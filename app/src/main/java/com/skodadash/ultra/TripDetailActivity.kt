package com.skodadash.ultra

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.skodadash.ultra.databinding.ActivityTripDetailBinding
import java.text.SimpleDateFormat
import java.util.Locale

class TripDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTripDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTripDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val tripId = intent.getLongExtra("trip_id", -1)
        if (tripId == -1L) {
            finish()
            return
        }

        val trip = TripStorage(this).getTripById(tripId)
        if (trip == null) {
            finish()
            return
        }

        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY)

        binding.tvDetailTitle.text = "Fahrt ${sdf.format(java.util.Date(trip.startTime))}"
        binding.tvDetailSubtitle.text = if (trip.isAuto) "Automatisch erkannt" else "Manuell gestartet"

        binding.tvDetailStats.text = """
Distanz: ${String.format("%.2f km", trip.distanceMeters/1000)}
Dauer: ${trip.durationSec/3600}h ${trip.durationSec%3600/60}min ${trip.durationSec%60}sek
Max: ${trip.maxSpeedKmh.toInt()} km/h  Schnitt: ${trip.avgSpeedKmh.toInt()} km/h
Punkte: ${trip.pointCount} GPS Punkte
        """.trimIndent()

        binding.tvDetailScores.text = """
Punkte: ${trip.score}
Eco Score: ${trip.ecoScore}/100
${when {
            trip.ecoScore >= 80 -> "Sehr sparsam und sanft"
            trip.ecoScore >= 60 -> "Gute Fahrweise"
            trip.ecoScore >= 40 -> "Durchschnittlich"
            else -> "Sportlich - mehr Verbrauch"
        }}
        """.trimIndent()

        binding.tvDetailG.text = """
Max G-Kraft: ${String.format("%.2f", trip.maxG)} G
Max Beschleunigung: ${String.format("%.2f", trip.maxAccel)} m/s2
Max Bremsen: ${String.format("%.2f", trip.maxBrake)} m/s2
        """.trimIndent()

        val eventsText = if (trip.events.isEmpty()) {
            "Keine besonderen Ereignisse - sehr sanfte Fahrt"
        } else {
            val sb = StringBuilder()
            sb.append("${trip.events.size} Ereignisse:\n\n")
            trip.events.groupBy { it.type }.forEach { (type, evts) ->
                sb.append("$type: ${evts.size}x\n")
                evts.take(5).forEach { e ->
                    sb.append("  ${String.format("%.1f", e.value)} bei ${sdf.format(java.util.Date(e.time))}\n")
                }
                sb.append("\n")
            }
            sb.toString()
        }
        binding.tvDetailEvents.text = eventsText

        // Simple map view - show start/end and track info
        val mapText = if (trip.points.isEmpty()) {
            "Keine GPS Daten gespeichert"
        } else {
            val start = trip.points.first()
            val end = trip.points.last()
            """
Start: ${String.format("%.5f", start.lat)}, ${String.format("%.5f", start.lon)}
Ende: ${String.format("%.5f", end.lat)}, ${String.format("%.5f", end.lon)}
Punkte: ${trip.points.size}

Kartenansicht:
${trip.points.take(10).joinToString("\n") { "${String.format("%.5f", it.lat)},${String.format("%.5f", it.lon)} ${it.speedKmh.toInt()} km/h" }}
${if (trip.points.size > 10) "\n... und ${trip.points.size - 10} weitere" else ""}

Tipp: Exportiere die Punkte als GPX fuer externe Karten Apps.
            """.trimIndent()
        }
        binding.tvDetailMap.text = mapText

        binding.btnClose.setOnClickListener { finish() }
    }
}
