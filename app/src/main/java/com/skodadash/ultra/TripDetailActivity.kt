package com.skodadash.ultra

import android.content.Intent
import android.graphics.Color
import android.net.Uri
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

        val bgColor = getSharedPreferences("theme_cache", MODE_PRIVATE).getInt("bg_color", Color.parseColor("#FFF8E7"))
        binding.root.setBackgroundColor(bgColor)

        val tripId = intent.getLongExtra("trip_id", -1)
        if (tripId == -1L) { finish(); return }
        val trip = TripStorage(this).getTripById(tripId)
        if (trip == null) { finish(); return }
        currentTrip = trip

        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY)
        val sdfShort = SimpleDateFormat("HH:mm", Locale.GERMANY)

        val suggestedName = if (trip.customName.isNotEmpty()) trip.customName else StatsHelper.suggestTripName(trip)
        binding.tvDetailTitle.text = suggestedName
        binding.tvDetailSubtitle.text = "${String.format("%.1f km", trip.distanceMeters/1000)} - ${trip.durationSec/60} Min - ${if (trip.isAuto) "Auto" else "Manuell"}"
        binding.tvDetailLevel.text = trip.getLevel().uppercase()

        binding.tvDetailStats.text = """
Distanz: ${String.format("%.2f km", trip.distanceMeters/1000)}
Dauer: ${trip.durationSec/60} Min ${trip.durationSec%60} Sek
Max: ${trip.maxSpeedKmh.toInt()} km/h
Schnitt: ${trip.avgSpeedKmh.toInt()} km/h
GPS: ${trip.points.size} Punkte
        """.trimIndent()

        binding.tvDetailScores.text = """
Score: ${trip.score} Punkte
Stil: ${trip.getDrivingStyle()}
G max: ${String.format("%.2f", trip.maxG)} G
Bremsen: ${trip.events.count { it.type.contains("BRAKE") }}x
        """.trimIndent()

        // Eigene Features: Kosten & Verhalten
        val engineType = SettingsRepository(this).let {
            var type = "electric"
            try { type = kotlinx.coroutines.runBlocking { it.getEngineType() } } catch (_: Exception) {}
            type
        }
        val cost = CostCalculator.calculate(trip, engineType)
        binding.tvCostDetail.text = """
Kosten: ${String.format("%.2f €", cost.costEuro)} - ${cost.efficiency}
${if (cost.fuelLiters > 0) "Kraftstoff: ${String.format("%.2f L", cost.fuelLiters)}" else "Energie: ${String.format("%.1f kWh", cost.kwh)}"}
CO2: ${String.format("%.1f kg", cost.co2Kg)}
Pro km: ${String.format("%.2f €/km", if (trip.distanceMeters>0) cost.costEuro/(trip.distanceMeters/1000) else 0.0)}
        """.trimIndent()

        val behavior = StatsHelper.getDrivingBehavior(trip)
        binding.tvBehavior.text = """
Fahrverhalten:
${behavior.entries.joinToString("\n") { "${it.key}: ${it.value}" }}
        """.trimIndent()

        binding.etCustomName.setText(trip.customName)
        binding.etNotes.setText(trip.notes)

        binding.tvDetailEvents.text = if (trip.events.isEmpty()) "Keine Events - gleichmässig" else {
            trip.events.groupBy { it.type }.entries.joinToString("\n") { (type, list) ->
                val name = when(type) {
                    "BRAKE" -> "Bremsen"; "HARD_BRAKE" -> "Stark Bremsen"
                    "ACCEL" -> "Gas"; "HARD_ACCEL" -> "Stark Gas"
                    "CORNER" -> "Kurve"; "SHARP_CORNER" -> "Scharfe Kurve"
                    "SPEED" -> "Schnell"; else -> type
                }
                "$name: ${list.size}x"
            }
        }

        // Karte
        binding.mapView.setTripData(trip.points, trip.events)
        binding.mapView.onEventSelected = { event ->
            val name = when(event.type) {
                "BRAKE" -> "Bremsen"; "HARD_BRAKE" -> "Stark Bremsen"
                "ACCEL" -> "Gas"; "HARD_ACCEL" -> "Stark Gas"
                "CORNER" -> "Kurve"; "SHARP_CORNER" -> "Scharfe Kurve"
                "SPEED" -> "Schnell"; else -> event.type
            }
            binding.tvSelectedEvent.text = "$name ${String.format("%.1f", event.value)} - ${event.speedKmh.toInt()} km/h - ${sdfShort.format(java.util.Date(event.time))}"
            binding.mapView.setSelectedEvent(event)
        }

        // Replay Feature
        binding.btnReplay.setOnClickListener {
            if (binding.mapView.isReplayActive()) {
                binding.mapView.stopReplay()
                binding.btnReplay.text = "Replay"
                binding.tvReplayInfo.visibility = android.view.View.GONE
            } else {
                binding.btnReplay.text = "Stop"
                binding.tvReplayInfo.visibility = android.view.View.VISIBLE
                binding.mapView.startReplay { idx, point ->
                    runOnUiThread {
                        binding.tvReplayInfo.text = "Replay ${idx+1}/${trip.points.size} - ${point.speedKmh.toInt()} km/h"
                    }
                }
            }
        }

        binding.btnSaveNote.setOnClickListener {
            val updated = trip.copy(
                customName = binding.etCustomName.text.toString().trim(),
                notes = binding.etNotes.text.toString().trim()
            )
            TripStorage(this).updateTrip(updated)
            currentTrip = updated
            Toast.makeText(this, "Gespeichert", Toast.LENGTH_SHORT).show()
            binding.tvDetailTitle.text = updated.customName.ifEmpty { StatsHelper.suggestTripName(updated) }
        }

        binding.btnOpenMaps.setOnClickListener {
            if (trip.points.isNotEmpty()) {
                val start = trip.points.first()
                val end = trip.points.last()
                // Open in Google Maps with directions
                val uri = Uri.parse("https://www.google.com/maps/dir/${start.lat},${start.lon}/${end.lat},${end.lon}/")
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
        }

        binding.btnShareTrip.setOnClickListener {
            val shareText = """
${binding.tvDetailTitle.text} - ${String.format("%.1f km", trip.distanceMeters/1000)}
${trip.durationSec/60} Min, Max ${trip.maxSpeedKmh.toInt()} km/h, Schnitt ${trip.avgSpeedKmh.toInt()} km/h
Score ${trip.score} Punkte - ${trip.getDrivingStyle()}
Kosten ${String.format("%.2f €", cost.costEuro)}
${trip.notes}
            """.trimIndent()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(intent, "Fahrt teilen"))
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

        val accent = getSharedPreferences("theme_cache", MODE_PRIVATE).getInt("accent_color", Color.parseColor("#8B7355"))
        binding.btnClose.backgroundTintList = android.content.res.ColorStateList.valueOf(accent)
        binding.btnSaveNote.backgroundTintList = android.content.res.ColorStateList.valueOf(accent)
        binding.btnReplay.backgroundTintList = android.content.res.ColorStateList.valueOf(accent)
    }
}
