package com.skodadash.ultra

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Toast
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

        // Farben anwenden
        val bgColor = getSharedPreferences("theme_cache", MODE_PRIVATE).getInt("bg_color", Color.parseColor("#FFF8E7"))
        binding.root.setBackgroundColor(bgColor)

        val tripId = intent.getLongExtra("trip_id", -1)
        if (tripId == -1L) { finish(); return }

        val trip = TripStorage(this).getTripById(tripId)
        if (trip == null) { finish(); return }

        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY)
        val sdfShort = SimpleDateFormat("HH:mm", Locale.GERMANY)

        binding.tvDetailTitle.text = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(java.util.Date(trip.startTime))
        binding.tvDetailSubtitle.text = "${String.format("%.1f km", trip.distanceMeters/1000)} - ${trip.durationSec/60} Min - ${if (trip.isAuto) "Auto" else "Manuell"}"
        binding.tvDetailLevel.text = trip.getLevel().uppercase()

        // Nur nötige Daten
        binding.tvDetailStats.text = """
Distanz: ${String.format("%.2f km", trip.distanceMeters/1000)}
Dauer: ${trip.durationSec/60} Min ${trip.durationSec%60} Sek
Max: ${trip.maxSpeedKmh.toInt()} km/h
Schnitt: ${trip.avgSpeedKmh.toInt()} km/h
GPS Punkte: ${trip.points.size}
        """.trimIndent()

        binding.tvDetailScores.text = """
Score: ${trip.score} Punkte
Stil: ${trip.getDrivingStyle()}
G max: ${String.format("%.2f", trip.maxG)} G
Bremsen: ${trip.events.count { it.type.contains("BRAKE") }}x - wenig = mehr Punkte
        """.trimIndent()

        val eventsText = if (trip.events.isEmpty()) {
            "Keine Events - gleichmässige Fahrt"
        } else {
            val sb = StringBuilder()
            sb.append("${trip.events.size} Events:\n")
            trip.events.groupBy { it.type }.forEach { (type, list) ->
                val name = when(type) {
                    "BRAKE" -> "Bremsen"
                    "HARD_BRAKE" -> "Stark Bremsen"
                    "ACCEL" -> "Gas"
                    "HARD_ACCEL" -> "Stark Gas"
                    "CORNER" -> "Kurve"
                    "SHARP_CORNER" -> "Scharfe Kurve"
                    "SPEED" -> "Schnell"
                    else -> type
                }
                sb.append("$name: ${list.size}x\n")
            }
            sb.toString()
        }
        binding.tvDetailEvents.text = eventsText

        // Karte minimal
        binding.mapView.setTripData(trip.points, trip.events)
        binding.mapView.onEventSelected = { event ->
            val name = when(event.type) {
                "BRAKE" -> "Bremsen"
                "HARD_BRAKE" -> "Stark Bremsen"
                "ACCEL" -> "Beschleunigung"
                "HARD_ACCEL" -> "Stark Beschleunigung"
                "CORNER" -> "Kurve"
                "SHARP_CORNER" -> "Scharfe Kurve"
                "SPEED" -> "Hohe Geschwindigkeit"
                else -> event.type
            }
            binding.tvSelectedEvent.text = "$name - ${String.format("%.1f", event.value)} - ${event.speedKmh.toInt()} km/h - ${sdfShort.format(java.util.Date(event.time))}\n${String.format("%.5f", event.lat)}, ${String.format("%.5f", event.lon)}"
            binding.mapView.setSelectedEvent(event)
        }

        binding.btnExportGpx.setOnClickListener {
            val file = GpxExporter.exportTrip(this, trip)
            if (file != null) {
                Toast.makeText(this, "GPX ${file.name}", Toast.LENGTH_SHORT).show()
                GpxExporter.shareGpx(this, file)
            } else Toast.makeText(this, "Export Fehler", Toast.LENGTH_SHORT).show()
        }

        binding.btnDeleteTrip.setOnClickListener {
            TripStorage(this).deleteTrip(trip.id)
            Toast.makeText(this, "Gelöscht", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnClose.setOnClickListener { finish() }

        // Akzent Farbe auf Buttons
        val accent = getSharedPreferences("theme_cache", MODE_PRIVATE).getInt("accent_color", Color.parseColor("#8B7355"))
        binding.btnClose.backgroundTintList = android.content.res.ColorStateList.valueOf(accent)
    }
}
