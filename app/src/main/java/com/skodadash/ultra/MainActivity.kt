package com.skodadash.ultra

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import com.skodadash.ultra.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: SettingsRepository
    private lateinit var api: SkodaApi

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = SettingsRepository(this)
        api = SkodaApi(settings)

        // Permissions
        val perms = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        ).filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (perms.isNotEmpty()) permissionLauncher.launch(perms.toTypedArray())

        // Check login
        lifecycleScope.launch {
            val apiKey = settings.getApiKey()
            val vin = settings.getVin()
            if (apiKey.isEmpty() || vin.isEmpty()) {
                showLogin()
            } else {
                showDashboard()
            }
        }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showDashboard()
                    1 -> showTrips()
                    2 -> showWidgetInfo()
                    3 -> showSettings()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun showLogin() {
        binding.content.removeAllViews()
        val view = layoutInflater.inflate(R.layout.layout_login, binding.content, false)
        val etApiKey = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etApiKey)
        val etVin = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etVin)
        val btnLogin = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnLogin)
        val tvHelp = view.findViewById<android.widget.TextView>(R.id.tvHelp)

        tvHelp.text = "Offizielle Skoda API:\n1. MySkoda App öffnen (v8.16+)\n2. Profil > Einstellungen > Drittanbieter-Zugriff\n3. API-Key generieren\n4. Hier einfügen + FIN eingeben\n\nAPI-Key läuft nach 6 Monaten ab."

        lifecycleScope.launch {
            etApiKey.setText(settings.getApiKey())
            etVin.setText(settings.getVin())
        }

        btnLogin.setOnClickListener {
            val key = etApiKey.text.toString().trim()
            val vin = etVin.text.toString().trim().uppercase()
            if (key.length < 10 || vin.length < 10) {
                Toast.makeText(this, "Bitte gültigen API-Key und FIN eingeben", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                settings.saveApiKey(key)
                settings.saveVin(vin)
                Toast.makeText(this@MainActivity, "Gespeichert! Lade Daten...", Toast.LENGTH_SHORT).show()
                showDashboard()
            }
        }
        binding.content.addView(view)
    }

    private fun showDashboard() {
        binding.content.removeAllViews()
        val view = layoutInflater.inflate(R.layout.layout_dashboard, binding.content, false)
        binding.content.addView(view)

        val tvStatus = view.findViewById<android.widget.TextView>(R.id.tvStatus)
        val tvBattery = view.findViewById<android.widget.TextView>(R.id.tvBattery)
        val tvRange = view.findViewById<android.widget.TextView>(R.id.tvRange)
        val tvOdo = view.findViewById<android.widget.TextView>(R.id.tvOdo)
        val tvLock = view.findViewById<android.widget.TextView>(R.id.tvLock)
        val tvCharging = view.findViewById<android.widget.TextView>(R.id.tvCharging)
        val btnRefresh = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRefresh)
        val btnStartTrip = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnStartTrip)
        val btnStopTrip = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnStopTrip)

        fun updateTrackingButtons() {
            val running = TripService.isRunning
            btnStartTrip.isEnabled = !running
            btnStopTrip.isEnabled = running
            btnStartTrip.text = if (running) "Fahrt läuft..." else "Fahrt starten"
        }

        updateTrackingButtons()

        btnRefresh.setOnClickListener {
            lifecycleScope.launch {
                tvStatus.text = "Lade..."
                try {
                    val data = api.fetchVehicle()
                    if (data != null) {
                        tvStatus.text = "${data.name} - ${data.lastUpdated}\nDebug: Batt=${data.batteryPercent} Range=${data.rangeKm} Odo=${data.odometerKm} Locked=${data.doorsLocked}"
                        tvBattery.text = "Akku: ${data.batteryPercent?.let { "${it.toInt()}%" } ?: "n/a (Verbrenner?)"}"
                        tvRange.text = "Reichweite: ${data.rangeKm?.let { "${it.toInt()} km" } ?: "n/a"}"
                        tvOdo.text = "KM: ${data.odometerKm?.let { "${it.toInt()} km" } ?: "n/a"}"
                        tvLock.text = when (data.doorsLocked) {
                            true -> "🔒 Verriegelt"
                            false -> "🔓 Offen"
                            null -> "Verriegelung: n/a"
                        }
                        tvCharging.text = when (data.chargingState) {
                            "CHARGING" -> "⚡ Lädt ${data.chargingPowerKw?.toInt() ?: 0} kW"
                            "CHARGED" -> "✅ Voll"
                            "READY_FOR_CHARGING" -> "🔌 Bereit"
                            "CONSERVING" -> "🔋 Erhaltung"
                            null, "" -> "Laden: n/a (kein E-Auto?) - ${data.rawJson.take(200)}"
                            else -> "Status: ${data.chargingState}"
                        }
                        // Save for widget
                        getSharedPreferences("widget_data", MODE_PRIVATE).edit().apply {
                            putInt("battery", data.batteryPercent?.toInt() ?: 0)
                            putInt("range", data.rangeKm?.toInt() ?: 0)
                            putBoolean("locked", data.doorsLocked ?: false)
                            putString("charging", data.chargingState ?: "")
                            putString("name", data.name)
                            apply()
                        }
                        // Update widgets
                        val intent = Intent(this@MainActivity, SkodaWidgetProvider::class.java).apply {
                            action = "android.appwidget.action.APPWIDGET_UPDATE"
                        }
                        sendBroadcast(intent)
                    } else {
                        tvStatus.text = "Fehler: Keine Daten. API-Key prüfen."
                    }
                } catch (e: Exception) {
                    tvStatus.text = "Fehler: ${e.message}"
                }
            }
        }

        btnStartTrip.setOnClickListener {
            val intent = Intent(this, TripService::class.java).apply { action = TripService.ACTION_START }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Toast.makeText(this, "Fahrt gestartet - GPS + G-Sensor aktiv", Toast.LENGTH_SHORT).show()
            updateTrackingButtons()
        }

        btnStopTrip.setOnClickListener {
            val intent = Intent(this, TripService::class.java).apply { action = TripService.ACTION_STOP }
            startService(intent)
            Toast.makeText(this, "Fahrt beendet - sieh Fahrten Tab", Toast.LENGTH_SHORT).show()
            updateTrackingButtons()
        }

        // Auto refresh once
        btnRefresh.performClick()

        // Poll tracking state
        lifecycleScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                updateTrackingButtons()
            }
        }
    }

    private fun showTrips() {
        binding.content.removeAllViews()
        val view = layoutInflater.inflate(R.layout.layout_trips, binding.content, false)
        binding.content.addView(view)
        val tvTrips = view.findViewById<android.widget.TextView>(R.id.tvTrips)
        val btnClear = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnClearTrips)

        fun loadTrips() {
            val trips = TripStorage(this).getTrips().reversed()
            if (trips.isEmpty()) {
                tvTrips.text = "Noch keine Fahrten.\n\nStarte Tracking im Dashboard vor deiner Fahrt.\n\nEs wird geloggt:\n• GPS Verlauf (1Hz)\n• Geschwindigkeit\n• G-Kräfte: Beschleunigen/Bremsen/Kurven\n• Strecke, Max Speed, Ø Speed\n• Harsh Events"
            } else {
                val sb = StringBuilder()
                sb.append("${trips.size} Fahrten:\n\n")
                trips.take(20).forEach { trip ->
                    sb.append("📅 ${java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.GERMANY).format(java.util.Date(trip.startTime))}\n")
                    sb.append("   ${String.format("%.1f km", trip.distanceMeters/1000)} | ${trip.durationSec/60} Min | Max ${trip.maxSpeedKmh.toInt()} km/h\n")
                    sb.append("   Max G: ${String.format("%.2f", trip.maxG)} | Accel ${String.format("%.1f", trip.maxAccel)} m/s² | Brake ${String.format("%.1f", trip.maxBrake)} m/s²\n")
                    sb.append("   Punkte: ${trip.pointCount} | ${if (trip.endTime == null) "LÄUFT" else "Beendet"}\n\n")
                }
                tvTrips.text = sb.toString()
            }
        }

        loadTrips()
        btnClear.setOnClickListener {
            TripStorage(this).clearAll()
            loadTrips()
            Toast.makeText(this, "Alle Fahrten gelöscht", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showWidgetInfo() {
        binding.content.removeAllViews()
        val view = layoutInflater.inflate(R.layout.layout_widget_info, binding.content, false)
        binding.content.addView(view)
    }

    private fun showSettings() {
        binding.content.removeAllViews()
        val view = layoutInflater.inflate(R.layout.layout_settings, binding.content, false)
        binding.content.addView(view)

        val tvInfo = view.findViewById<android.widget.TextView>(R.id.tvSettingsInfo)
        val btnLogout = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnLogout)

        lifecycleScope.launch {
            val vin = settings.getVin()
            val key = settings.getApiKey()
            tvInfo.text = "FIN: $vin\nAPI-Key: ${key.take(8)}...${key.takeLast(4)}\n\nApp: SkodaDash Ultra v1.0.1\nAPI: public.api.connect.skoda-auto.cz\nWidget: alle 15 Min\nFahrtenbuch: GPS 1Hz + G-Sensor\n\nFür Widget: Lange auf Homescreen drücken > Widgets > SkodaDash"
        }

        btnLogout.setOnClickListener {
            lifecycleScope.launch {
                settings.clear()
                getSharedPreferences("widget_data", MODE_PRIVATE).edit().clear().apply()
                TripStorage(this@MainActivity).clearAll()
                Toast.makeText(this@MainActivity, "Abgemeldet", Toast.LENGTH_SHORT).show()
                showLogin()
            }
        }
    }
}
