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

        val perms = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        ).filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (perms.isNotEmpty()) permissionLauncher.launch(perms.toTypedArray())

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

        tvHelp.text = "1. MySkoda App oeffnen (Version 8.16 oder neuer)\n2. Profil -> Einstellungen -> Drittanbieter-Zugriff\n3. Neuen API-Key erstellen und Fahrzeug auswaehlen\n4. API-Key und FIN hier eingeben\n\nDer Key ist 6 Monate gueltig und erlaubt 20 Anfragen pro Stunde."

        lifecycleScope.launch {
            etApiKey.setText(settings.getApiKey())
            etVin.setText(settings.getVin())
        }

        btnLogin.setOnClickListener {
            val key = etApiKey.text.toString().trim()
            val vin = etVin.text.toString().trim().uppercase()
            if (key.length < 10 || vin.length < 10) {
                Toast.makeText(this, "Bitte gueltigen API-Key und FIN eingeben", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                settings.saveApiKey(key)
                settings.saveVin(vin)
                Toast.makeText(this@MainActivity, "Gespeichert", Toast.LENGTH_SHORT).show()
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
            btnStartTrip.text = if (running) "Aufzeichnung laeuft" else "Fahrt starten"
        }

        updateTrackingButtons()

        btnRefresh.setOnClickListener {
            lifecycleScope.launch {
                tvStatus.text = "Aktualisiere"
                try {
                    val data = api.fetchVehicle()
                    if (data != null) {
                        tvStatus.text = "${data.name} - ${data.lastUpdated}"

                        tvBattery.text = data.batteryPercent?.let { "${it.toInt()}%" } ?: "--"
                        tvRange.text = data.rangeKm?.let { "${it.toInt()} km" } ?: "--"
                        tvOdo.text = data.odometerKm?.let { "Kilometerstand ${it.toInt()} km" } ?: "Kilometerstand nicht verfuegbar"
                        tvLock.text = when (data.doorsLocked) {
                            true -> "Verriegelt"
                            false -> "Offen"
                            null -> "Status nicht verfuegbar"
                        }
                        tvCharging.text = when (data.chargingState) {
                            "CHARGING" -> "Laden mit ${data.chargingPowerKw?.let { "${it.toInt()} kW" } ?: ""}".trim()
                            "CHARGED" -> "Vollstaendig geladen"
                            "READY_FOR_CHARGING" -> "Bereit zum Laden"
                            "CONSERVING" -> "Ladeerhaltung aktiv"
                            "CHARGING_INTERRUPTED" -> "Laden unterbrochen"
                            "CONNECT_CABLE" -> "Kabel verbinden"
                            null, "" -> ""
                            else -> data.chargingState
                        }

                        getSharedPreferences("widget_data", MODE_PRIVATE).edit().apply {
                            putInt("battery", data.batteryPercent?.toInt() ?: -1)
                            putInt("range", data.rangeKm?.toInt() ?: -1)
                            putInt("odometer", data.odometerKm?.toInt() ?: -1)
                            putBoolean("locked", data.doorsLocked ?: false)
                            if (data.doorsLocked != null) putBoolean("hasLock", true) else remove("hasLock")
                            putString("charging", data.chargingState ?: "")
                            putString("name", data.name)
                            apply()
                        }
                        // Update all widgets
                        sendBroadcast(Intent(this@MainActivity, SkodaWidgetProvider::class.java).apply {
                            action = "android.appwidget.action.APPWIDGET_UPDATE"
                        })
                    } else {
                        tvStatus.text = "Keine Daten erhalten"
                    }
                } catch (e: Exception) {
                    tvStatus.text = e.message ?: "Fehler beim Laden"
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
            Toast.makeText(this, "Aufzeichnung gestartet", Toast.LENGTH_SHORT).show()
            updateTrackingButtons()
        }

        btnStopTrip.setOnClickListener {
            val intent = Intent(this, TripService::class.java).apply { action = TripService.ACTION_STOP }
            startService(intent)
            Toast.makeText(this, "Aufzeichnung beendet", Toast.LENGTH_SHORT).show()
            updateTrackingButtons()
        }

        btnRefresh.performClick()

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
                tvTrips.text = "Noch keine Fahrten vorhanden.\n\nStarte die Aufzeichnung im Dashboard vor deiner Fahrt.\n\nErfasst werden:\n- GPS Verlauf mit 1Hz\n- Geschwindigkeit\n- G-Kraefte fuer Beschleunigung, Bremsen und Kurven\n- Distanz, Maximal- und Durchschnittsgeschwindigkeit"
            } else {
                val sb = StringBuilder()
                trips.take(30).forEach { trip ->
                    sb.append("${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.GERMANY).format(java.util.Date(trip.startTime))}\n")
                    sb.append("Distanz ${String.format("%.1f km", trip.distanceMeters/1000)}  Dauer ${trip.durationSec/60} Min  Max ${trip.maxSpeedKmh.toInt()} km/h  Schnitt ${trip.avgSpeedKmh.toInt()} km/h\n")
                    sb.append("Max G ${String.format("%.2f", trip.maxG)}  Beschl ${String.format("%.1f", trip.maxAccel)} m/s2  Bremse ${String.format("%.1f", trip.maxBrake)} m/s2  Punkte ${trip.pointCount}\n\n")
                }
                tvTrips.text = sb.toString()
            }
        }

        loadTrips()
        btnClear.setOnClickListener {
            TripStorage(this).clearAll()
            loadTrips()
            Toast.makeText(this, "Verlauf geloescht", Toast.LENGTH_SHORT).show()
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
            tvInfo.text = "FIN\n$vin\n\nAPI-Key\n${key.take(12)}...${key.takeLast(6)}\n\nDiese App nutzt die offizielle Skoda Public API. Der Key wird lokal verschluesselt gespeichert und nur fuer Anfragen an public.api.connect.skoda-auto.cz verwendet."
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
