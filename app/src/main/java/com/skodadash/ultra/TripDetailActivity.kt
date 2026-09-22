package com.skodadash.ultra

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.skodadash.ultra.databinding.ActivityTripDetailBinding
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Locale

class TripDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTripDetailBinding
    private var currentTrip: TripData? = null
    private var isSatellite = true
    private var replayJob: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        Configuration.getInstance().osmdroidBasePath = getExternalFilesDir(null)
        Configuration.getInstance().osmdroidTileCache = cacheDir

        binding = ActivityTripDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bgColor = getSharedPreferences("theme_cache", MODE_PRIVATE).getInt("bg_color", Color.parseColor("#000000"))
        binding.root.setBackgroundColor(bgColor)

        val tripId = intent.getLongExtra("trip_id", -1)
        if (tripId == -1L) { finish(); return }
        val trip = TripStorage(this).getTripById(tripId)
        if (trip == null) { finish(); return }
        currentTrip = trip

        val sdfShort = SimpleDateFormat("HH:mm", Locale.GERMANY)
        val suggestedName = if (trip.customName.isNotEmpty()) trip.customName else StatsHelper.suggestTripName(trip)
        binding.tvDetailTitle.text = suggestedName
        binding.tvDetailSubtitle.text = "${String.format("%.1f km", trip.distanceMeters/1000)} - ${trip.durationSec/60} Min"
        binding.tvDetailLevel.text = trip.getLevel().uppercase()

        binding.tvDetailStats.text = """
${String.format("%.2f km", trip.distanceMeters/1000)}
${trip.durationSec/60} Min ${trip.durationSec%60} Sek
Max ${trip.maxSpeedKmh.toInt()} km/h
Ø ${trip.avgSpeedKmh.toInt()} km/h
        """.trimIndent()

        binding.tvDetailScores.text = """
${trip.score} Punkte
${trip.getDrivingStyle()}
${String.format("%.2f G", trip.maxG)} max
        """.trimIndent()

        val engineType = try { kotlinx.coroutines.runBlocking { SettingsRepository(this).getEngineType() } } catch (_: Exception) { "electric" }
        val cost = CostCalculator.calculate(trip, engineType)
        binding.tvCostDetail.text = "${String.format("%.2f €", cost.costEuro)} - ${cost.efficiency}\n${if (cost.fuelLiters>0) "${String.format("%.2f L", cost.fuelLiters)}" else "${String.format("%.1f kWh", cost.kwh)}"} - ${String.format("%.1f kg CO2", cost.co2Kg)}"
        binding.tvBehavior.text = StatsHelper.getDrivingBehavior(trip).entries.joinToString("\n") { "${it.key}: ${it.value}" }

        binding.etCustomName.setText(trip.customName)
        binding.etNotes.setText(trip.notes)

        binding.tvDetailEvents.text = if (trip.events.isEmpty()) "Keine Events" else {
            trip.events.groupBy { it.type }.entries.joinToString("\n") { (t, l) ->
                val n = when(t) {
                    "BRAKE" -> "Bremsen"; "HARD_BRAKE" -> "Stark Bremsen"
                    "ACCEL" -> "Gas"; "HARD_ACCEL" -> "Stark Gas"
                    "CORNER" -> "Kurve"; "SHARP_CORNER" -> "Scharfe Kurve"
                    "SPEED" -> "Schnell"; else -> t
                }
                "$n: ${l.size}x"
            }
        }

        // Satellite Map with osmdroid
        setupMap(trip)

        binding.btnToggleMap.setOnClickListener {
            isSatellite = !isSatellite
            setupMap(trip)
            binding.btnToggleMap.text = if (isSatellite) "Satellit" else "Karte"
        }

        binding.btnReplay.setOnClickListener {
            if (replayJob?.isAlive == true) {
                replayJob?.interrupt()
                binding.btnReplay.text = "Replay"
                binding.tvReplayInfo.visibility = android.view.View.GONE
            } else {
                binding.btnReplay.text = "Stop"
                binding.tvReplayInfo.visibility = android.view.View.VISIBLE
                startReplay(trip)
            }
        }

        binding.btnSaveNote.setOnClickListener {
            val updated = trip.copy(customName = binding.etCustomName.text.toString().trim(), notes = binding.etNotes.text.toString().trim())
            TripStorage(this).updateTrip(updated)
            currentTrip = updated
            Toast.makeText(this, "Gespeichert", Toast.LENGTH_SHORT).show()
            binding.tvDetailTitle.text = updated.customName.ifEmpty { StatsHelper.suggestTripName(updated) }
        }

        binding.btnOpenMaps.setOnClickListener {
            if (trip.points.isNotEmpty()) {
                val start = trip.points.first()
                val end = trip.points.last()
                val uri = Uri.parse("https://www.google.com/maps/dir/${start.lat},${start.lon}/${end.lat},${end.lon}/")
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
        }

        binding.btnShareTrip.setOnClickListener {
            val shareText = "${binding.tvDetailTitle.text} - ${String.format("%.1f km", trip.distanceMeters/1000)} - ${trip.score} Pkt"
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareText) }, "Teilen"))
        }

        binding.btnExportGpx.setOnClickListener {
            val file = GpxExporter.exportTrip(this, trip)
            if (file != null) GpxExporter.shareGpx(this, file) else Toast.makeText(this, "Fehler", Toast.LENGTH_SHORT).show()
        }

        binding.btnDeleteTrip.setOnClickListener {
            TripStorage(this).deleteTrip(trip.id)
            Toast.makeText(this, "Gelöscht", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnClose.setOnClickListener { finish() }

        val accent = getSharedPreferences("theme_cache", MODE_PRIVATE).getInt("accent_color", Color.parseColor("#6ECFC3"))
        binding.btnClose.backgroundTintList = android.content.res.ColorStateList.valueOf(accent)
        binding.btnSaveNote.backgroundTintList = android.content.res.ColorStateList.valueOf(accent)
        binding.btnReplay.backgroundTintList = android.content.res.ColorStateList.valueOf(accent)
    }

    private fun setupMap(trip: TripData) {
        val map = binding.mapSatellite
        map.setMultiTouchControls(true)
        map.setUseDataConnection(true)

        val tileSource = if (isSatellite) {
            XYTileSource("EsriWorldImagery", 0, 19, 256, ".jpg", arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"))
        } else {
            XYTileSource("Mapnik", 0, 19, 256, ".png", arrayOf("https://tile.openstreetmap.org/"))
        }
        map.setTileSource(tileSource)
        map.overlays.clear()

        if (trip.points.isEmpty()) return

        val geoPoints = trip.points.map { GeoPoint(it.lat, it.lon) }

        val polyline = Polyline().apply {
            setPoints(geoPoints)
            outlinePaint.color = Color.parseColor("#6ECFC3")
            outlinePaint.strokeWidth = 10f
        }
        map.overlays.add(polyline)

        // Start marker
        Marker(map).apply {
            position = geoPoints.first()
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = getDrawable(android.R.drawable.presence_online)?.apply { setTint(Color.parseColor("#30D158")) }
            title = "Start"
            map.overlays.add(this)
        }

        // End marker
        if (geoPoints.size > 1) {
            Marker(map).apply {
                position = geoPoints.last()
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = getDrawable(android.R.drawable.presence_busy)?.apply { setTint(Color.parseColor("#FF3B30")) }
                title = "Ziel"
                map.overlays.add(this)
            }
        }

        // Events
        trip.events.forEach { ev ->
            Marker(map).apply {
                position = GeoPoint(ev.lat, ev.lon)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                val color = when(ev.type) {
                    "BRAKE" -> "#FF9F0A"; "HARD_BRAKE" -> "#FF3B30"
                    "ACCEL" -> "#30D158"; "HARD_ACCEL" -> "#1B7A3A"
                    "CORNER" -> "#5856D6"; "SHARP_CORNER" -> "#AF52DE"
                    "SPEED" -> "#6ECFC3"; else -> "#FFFFFF"
                }
                icon = getDrawable(android.R.drawable.presence_invisible)?.apply { setTint(Color.parseColor(color)) }
                title = "${ev.type} ${String.format("%.1f", ev.value)}"
                snippet = "${ev.speedKmh.toInt()} km/h"
                setOnMarkerClickListener { marker, _ ->
                    binding.tvSelectedEvent.text = "${ev.type} - ${String.format("%.1f", ev.value)} - ${ev.speedKmh.toInt()} km/h"
                    true
                }
                map.overlays.add(this)
            }
        }

        // Center map
        val minLat = trip.points.minOf { it.lat }
        val maxLat = trip.points.maxOf { it.lat }
        val minLon = trip.points.minOf { it.lon }
        val maxLon = trip.points.maxOf { it.lon }
        val center = GeoPoint((minLat+maxLat)/2, (minLon+maxLon)/2)
        map.controller.setCenter(center)
        val latDiff = maxLat - minLat
        val lonDiff = maxLon - minLon
        val maxDiff = maxOf(latDiff, lonDiff)
        val zoom = when {
            maxDiff < 0.005 -> 16.0
            maxDiff < 0.02 -> 14.0
            maxDiff < 0.05 -> 13.0
            maxDiff < 0.1 -> 12.0
            else -> 11.0
        }
        map.controller.setZoom(zoom)
        map.invalidate()
    }

    private fun startReplay(trip: TripData) {
        if (trip.points.size < 2) return
        val map = binding.mapSatellite
        replayJob = Thread {
            for (i in trip.points.indices) {
                if (Thread.interrupted()) break
                val pt = trip.points[i]
                runOnUiThread {
                    binding.tvReplayInfo.text = "${i+1}/${trip.points.size} - ${pt.speedKmh.toInt()} km/h"
                    map.controller.animateTo(GeoPoint(pt.lat, pt.lon))
                    // Add moving marker
                    map.overlays.removeIf { it is Marker && it.title == "REPLAY" }
                    Marker(map).apply {
                        position = GeoPoint(pt.lat, pt.lon)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = getDrawable(android.R.drawable.presence_online)?.apply { setTint(Color.parseColor("#FFCC02")) }
                        title = "REPLAY"
                        map.overlays.add(this)
                    }
                    map.invalidate()
                }
                try { Thread.sleep(100) } catch (_: Exception) { break }
            }
            runOnUiThread {
                binding.btnReplay.text = "Replay"
                binding.tvReplayInfo.visibility = android.view.View.GONE
                map.overlays.removeIf { it is Marker && it.title == "REPLAY" }
                map.invalidate()
            }
        }.also { it.start() }
    }

    override fun onResume() {
        super.onResume()
        binding.mapSatellite.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapSatellite.onPause()
        replayJob?.interrupt()
    }
}
