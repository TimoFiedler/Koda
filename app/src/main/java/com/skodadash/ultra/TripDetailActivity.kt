package com.skodadash.ultra

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.skodadash.ultra.databinding.ActivityTripDetailBinding
import org.osmdroid.config.Configuration
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

        // Force black background always for dark reference
        binding.root.setBackgroundColor(Color.parseColor("#FF000000"))

        val tripId = intent.getLongExtra("trip_id", -1)
        if (tripId == -1L) { finish(); return }
        val trip = TripStorage(this@TripDetailActivity).getTripById(tripId)
        if (trip == null) { finish(); return }
        currentTrip = trip

        // If trip has 0 distance but has points, recalculate
        val effectiveTrip = if (trip.distanceMeters < 5 && trip.points.size > 1) {
            var dist = 0.0
            for (i in 1 until trip.points.size) {
                val a = trip.points[i-1]
                val b = trip.points[i]
                val result = FloatArray(1)
                android.location.Location.distanceBetween(a.lat, a.lon, b.lat, b.lon, result)
                dist += result[0]
            }
            trip.copy(distanceMeters = dist)
        } else trip

        val suggestedName = if (effectiveTrip.customName.isNotEmpty()) effectiveTrip.customName else StatsHelper.suggestTripName(effectiveTrip)
        binding.tvDetailTitle.text = suggestedName
        binding.tvDetailSubtitle.text = "${String.format("%.1f km", effectiveTrip.distanceMeters/1000)} - ${effectiveTrip.durationSec/60} Min ${effectiveTrip.durationSec%60} Sek"
        binding.tvDetailLevel.text = effectiveTrip.getLevel().uppercase()

        binding.tvDetailStats.text = """
${String.format("%.2f km", effectiveTrip.distanceMeters/1000)}
${effectiveTrip.durationSec/60} Min ${effectiveTrip.durationSec%60} Sek
Max ${effectiveTrip.maxSpeedKmh.toInt()} km/h
Ø ${effectiveTrip.avgSpeedKmh.toInt()} km/h
${effectiveTrip.pointCount} Punkte
        """.trimIndent()

        binding.tvDetailScores.text = """
${effectiveTrip.score} Punkte
${effectiveTrip.getDrivingStyle()}
${String.format("%.2f G", effectiveTrip.maxG)} max
${String.format("%.1f", effectiveTrip.maxAccel)} m/s² Acc
${String.format("%.1f", effectiveTrip.maxBrake)} m/s² Brake
        """.trimIndent()

        val engineType = try { kotlinx.coroutines.runBlocking { SettingsRepository(this@TripDetailActivity).getEngineType() } } catch (_: Exception) { "electric" }
        val cost = CostCalculator.calculate(effectiveTrip, engineType)
        binding.tvCostDetail.text = "${String.format("%.2f €", cost.costEuro)} - ${cost.efficiency}\n${if (cost.fuelLiters>0) "${String.format("%.2f L", cost.fuelLiters)}" else "${String.format("%.1f kWh", cost.kwh)}"} - ${String.format("%.1f kg CO2", cost.co2Kg)}"
        binding.tvBehavior.text = StatsHelper.getDrivingBehavior(effectiveTrip).entries.joinToString("\n") { "${it.key}: ${it.value}" }

        binding.etCustomName.setText(effectiveTrip.customName)
        binding.etNotes.setText(effectiveTrip.notes)

        binding.tvDetailEvents.text = if (effectiveTrip.events.isEmpty()) "Keine Events - sanfte Fahrt" else {
            effectiveTrip.events.groupBy { it.type }.entries.joinToString("\n") { (t, l) ->
                val n = when(t) {
                    "BRAKE" -> "Bremsen"; "HARD_BRAKE" -> "Stark Bremsen"
                    "ACCEL" -> "Gas"; "HARD_ACCEL" -> "Stark Gas"
                    "CORNER" -> "Kurve"; "SHARP_CORNER" -> "Scharfe Kurve"
                    "SPEED" -> "Schnell"; else -> t
                }
                "$n: ${l.size}x - max ${String.format("%.1f", l.maxOfOrNull { it.value } ?: 0.0)}"
            }
        }

        setupMap(effectiveTrip)

        binding.btnToggleMap.setOnClickListener {
            isSatellite = !isSatellite
            setupMap(effectiveTrip)
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
                startReplay(effectiveTrip)
            }
        }

        binding.btnSaveNote.setOnClickListener {
            val updated = effectiveTrip.copy(customName = binding.etCustomName.text.toString().trim(), notes = binding.etNotes.text.toString().trim())
            TripStorage(this@TripDetailActivity).updateTrip(updated)
            currentTrip = updated
            Toast.makeText(this@TripDetailActivity, "Gespeichert", Toast.LENGTH_SHORT).show()
            binding.tvDetailTitle.text = updated.customName.ifEmpty { StatsHelper.suggestTripName(updated) }
        }

        binding.btnOpenMaps.setOnClickListener {
            if (effectiveTrip.points.isNotEmpty()) {
                val start = effectiveTrip.points.first()
                val end = effectiveTrip.points.last()
                val uri = Uri.parse("https://www.google.com/maps/dir/${start.lat},${start.lon}/${end.lat},${end.lon}/")
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
        }

        binding.btnShareTrip.setOnClickListener {
            val shareText = "${binding.tvDetailTitle.text} - ${String.format("%.1f km", effectiveTrip.distanceMeters/1000)} - ${effectiveTrip.score} Pkt - ${effectiveTrip.durationSec/60} Min"
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).also { it.type = "text/plain"; it.putExtra(Intent.EXTRA_TEXT, shareText) }, "Teilen"))
        }

        binding.btnExportGpx.setOnClickListener {
            val file = GpxExporter.exportTrip(this@TripDetailActivity, effectiveTrip)
            if (file != null) GpxExporter.shareGpx(this@TripDetailActivity, file) else Toast.makeText(this@TripDetailActivity, "Fehler", Toast.LENGTH_SHORT).show()
        }

        binding.btnDeleteTrip.setOnClickListener {
            TripStorage(this@TripDetailActivity).deleteTrip(effectiveTrip.id)
            Toast.makeText(this@TripDetailActivity, "Gelöscht", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnClose.setOnClickListener { finish() }

        val accent = Color.parseColor("#FF6ECFC3")
        binding.btnClose.backgroundTintList = android.content.res.ColorStateList.valueOf(accent)
        binding.btnSaveNote.backgroundTintList = android.content.res.ColorStateList.valueOf(accent)
        binding.btnReplay.backgroundTintList = android.content.res.ColorStateList.valueOf(accent)
    }

    private fun setupMap(trip: TripData) {
        val map = binding.mapSatellite
        map.setMultiTouchControls(true)
        map.setUseDataConnection(true)

        val tileSource = if (isSatellite) EsriTileSource() else OsmTileSource()
        map.setTileSource(tileSource)
        map.overlays.clear()

        if (trip.points.isEmpty()) {
            binding.tvSelectedEvent.text = "Keine Punkte"
            return
        }

        val geoPoints = trip.points.map { GeoPoint(it.lat, it.lon) }

        val polyline = Polyline().also {
            it.setPoints(geoPoints)
            it.outlinePaint.color = Color.parseColor("#FF6ECFC3")
            it.outlinePaint.strokeWidth = 10f
        }
        map.overlays.add(polyline)

        Marker(map).also {
            it.position = geoPoints.first()
            it.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            it.icon = getDrawable(android.R.drawable.presence_online)?.also { d -> d.setTint(Color.parseColor("#FF30D158")) }
            it.title = "Start"
            map.overlays.add(it)
        }

        if (geoPoints.size > 1) {
            Marker(map).also {
                it.position = geoPoints.last()
                it.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                it.icon = getDrawable(android.R.drawable.presence_busy)?.also { d -> d.setTint(Color.parseColor("#FFFF3B30")) }
                it.title = "Ziel"
                map.overlays.add(it)
            }
        }

        trip.events.forEach { ev ->
            Marker(map).also {
                it.position = GeoPoint(ev.lat, ev.lon)
                it.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                val color = when(ev.type) {
                    "BRAKE" -> "#FFFF9F0A"; "HARD_BRAKE" -> "#FFFF3B30"
                    "ACCEL" -> "#FF30D158"; "HARD_ACCEL" -> "#FF1B7A3A"
                    "CORNER" -> "#FF5856D6"; "SHARP_CORNER" -> "#FFAF52DE"
                    "SPEED" -> "#FF6ECFC3"; else -> "#FFFFFFFF"
                }
                it.icon = getDrawable(android.R.drawable.presence_invisible)?.also { d -> d.setTint(Color.parseColor(color)) }
                it.title = "${ev.type} ${String.format("%.1f", ev.value)}"
                it.snippet = "${ev.speedKmh.toInt()} km/h"
                it.setOnMarkerClickListener { _, _ ->
                    binding.tvSelectedEvent.text = "${ev.type} - ${String.format("%.1f", ev.value)} m/s² - ${ev.speedKmh.toInt()} km/h"
                    true
                }
                map.overlays.add(it)
            }
        }

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
            maxDiff < 0.001 -> 18.0
            maxDiff < 0.005 -> 16.0
            maxDiff < 0.02 -> 14.0
            maxDiff < 0.05 -> 13.0
            maxDiff < 0.1 -> 12.0
            maxDiff < 0.5 -> 10.0
            else -> 8.0
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
                    binding.tvReplayInfo.text = "${i+1}/${trip.points.size} - ${pt.speedKmh.toInt()} km/h - ${String.format("%.1f", pt.lat)} ${String.format("%.1f", pt.lon)}"
                    map.controller.animateTo(GeoPoint(pt.lat, pt.lon))
                    map.overlays.removeIf { it is Marker && it.title == "REPLAY" }
                    Marker(map).also {
                        it.position = GeoPoint(pt.lat, pt.lon)
                        it.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        it.icon = getDrawable(android.R.drawable.presence_online)?.also { d -> d.setTint(Color.parseColor("#FFFFCC02")) }
                        it.title = "REPLAY"
                        map.overlays.add(it)
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
