package com.skodadash.ultra

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
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
        api = SkodaApi(settings, this)

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
        val rgEngine = view.findViewById<android.widget.RadioGroup>(R.id.rgEngineType)

        tvHelp.text = "1. MySkoda App oeffnen (Version 8.16 oder neuer)\n2. Profil -> Einstellungen -> Drittanbieter-Zugriff\n3. Neuen API-Key erstellen und Fahrzeug auswaehlen\n4. API-Key und FIN hier eingeben\n5. Antriebsart waehlen fuer korrekte Anzeige\n\nDer Key ist 6 Monate gueltig und erlaubt 20 Anfragen pro Stunde. Rate Limit wird automatisch verwaltet."

        lifecycleScope.launch {
            etApiKey.setText(settings.getApiKey())
            etVin.setText(settings.getVin())
            when (settings.getEngineType()) {
                "combustion" -> view.findViewById<android.widget.RadioButton>(R.id.rbCombustion).isChecked = true
                "hybrid" -> view.findViewById<android.widget.RadioButton>(R.id.rbHybrid).isChecked = true
                else -> view.findViewById<android.widget.RadioButton>(R.id.rbElectric).isChecked = true
            }
        }

        btnLogin.setOnClickListener {
            val key = etApiKey.text.toString().trim()
            val vin = etVin.text.toString().trim().uppercase()
            if (key.length < 10 || vin.length < 10) {
                Toast.makeText(this@MainActivity, "Bitte gueltigen API-Key und FIN eingeben", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val engineType = when (rgEngine.checkedRadioButtonId) {
                R.id.rbCombustion -> "combustion"
                R.id.rbHybrid -> "hybrid"
                else -> "electric"
            }
            lifecycleScope.launch {
                settings.saveApiKey(key)
                settings.saveVin(vin)
                settings.saveEngineType(engineType)
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

        val tvStatus = view.findViewById<TextView>(R.id.tvStatus)
        val tvBattery = view.findViewById<TextView>(R.id.tvBattery)
        val tvBatteryLabel = view.findViewById<TextView>(R.id.tvBatteryLabel)
        val tvRange = view.findViewById<TextView>(R.id.tvRange)
        val tvOdo = view.findViewById<TextView>(R.id.tvOdo)
        val tvLock = view.findViewById<TextView>(R.id.tvLock)
        val tvCharging = view.findViewById<TextView>(R.id.tvCharging)
        val tvEngineBadge = view.findViewById<TextView>(R.id.tvEngineBadge)
        val tvAutoStatus = view.findViewById<TextView>(R.id.tvAutoStatus)
        val btnRefresh = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRefresh)
        val btnStartTrip = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnStartTrip)
        val btnStopTrip = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnStopTrip)
        val swAutoTrip = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.swAutoTrip)
        val tvRateLimit = view.findViewById<TextView>(R.id.tvRateLimit)

        lifecycleScope.launch {
            val engineType = settings.getEngineType()
            tvEngineBadge.text = when (engineType) {
                "combustion" -> "Verbrenner"
                "hybrid" -> "Hybrid"
                else -> "Elektro"
            }
            tvBatteryLabel.text = when (engineType) {
                "combustion" -> "Tank"
                "hybrid" -> "Akku"
                else -> "Ladezustand"
            }
            swAutoTrip.isChecked = settings.getAutoTripEnabled()
            tvAutoStatus.text = if (settings.getAutoTripEnabled()) "Auto an" else "Auto aus"
            tvRateLimit.text = RateLimiter(this@MainActivity).getStatusText()
        }

        fun updateTrackingButtons() {
            val running = TripService.isRunning
            val autoRunning = AutoTripService.isRunning
            btnStartTrip.isEnabled = !running
            btnStopTrip.isEnabled = running
            btnStartTrip.text = if (running) "Aufzeichnung laeuft" else "Fahrt manuell starten"
            tvAutoStatus.text = when {
                running && autoRunning -> "Auto erkennt - Fahrt laeuft"
                running -> "Manuell - Fahrt laeuft"
                autoRunning -> "Auto an - wartet"
                else -> "Bereit"
            }
        }

        swAutoTrip.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                settings.saveAutoTripEnabled(isChecked)
                if (isChecked) {
                    val intent = Intent(this@MainActivity, AutoTripService::class.java).apply { action = AutoTripService.ACTION_START }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                    Toast.makeText(this@MainActivity, "Automatische Erkennung aktiviert", Toast.LENGTH_SHORT).show()
                } else {
                    val intent = Intent(this@MainActivity, AutoTripService::class.java).apply { action = AutoTripService.ACTION_STOP }
                    startService(intent)
                    Toast.makeText(this@MainActivity, "Automatische Erkennung deaktiviert", Toast.LENGTH_SHORT).show()
                }
                updateTrackingButtons()
            }
        }

        updateTrackingButtons()

        btnRefresh.setOnClickListener {
            lifecycleScope.launch {
                val limiter = RateLimiter(this@MainActivity)
                if (!limiter.canMakeRequest()) {
                    tvStatus.text = limiter.getStatusText()
                    Toast.makeText(this@MainActivity, limiter.getStatusText(), Toast.LENGTH_LONG).show()
                    return@launch
                }
                tvStatus.text = "Aktualisiere"
                try {
                    val data = api.fetchVehicle()
                    if (data != null) {
                        val engineType = settings.getEngineType()
                        tvStatus.text = "${data.name} - ${data.lastUpdated}"
                        tvRateLimit.text = limiter.getStatusText()

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
                            null, "" -> if (engineType == "electric") "Nicht am Laden" else ""
                            else -> data.chargingState
                        }

                        getSharedPreferences("widget_data", MODE_PRIVATE).edit().apply {
                            putInt("battery", data.batteryPercent?.toInt() ?: -1)
                            putInt("range", data.rangeKm?.toInt() ?: -1)
                            putInt("odometer", data.odometerKm?.toInt() ?: -1)
                            putBoolean("locked", data.doorsLocked ?: false)
                            putString("charging", data.chargingState ?: "")
                            putString("name", data.name)
                            putString("engine_type", engineType)
                            apply()
                        }
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
            Toast.makeText(this@MainActivity, "Aufzeichnung gestartet", Toast.LENGTH_SHORT).show()
            updateTrackingButtons()
        }

        btnStopTrip.setOnClickListener {
            val intent = Intent(this, TripService::class.java).apply { action = TripService.ACTION_STOP }
            startService(intent)
            Toast.makeText(this@MainActivity, "Aufzeichnung beendet", Toast.LENGTH_SHORT).show()
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
        
        val container = view.findViewById<LinearLayout>(R.id.containerTrips)
        val tvEmpty = view.findViewById<TextView>(R.id.tvTripsEmpty)
        val tvTotalScore = view.findViewById<TextView>(R.id.tvTotalScore)
        val btnClear = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnClearTrips)

        fun loadTrips() {
            container.removeAllViews()
            val storage = TripStorage(this)
            val trips = storage.getTrips().reversed()
            tvTotalScore.text = "${storage.getTotalScore()} Punkte - ${String.format("%.1f km", storage.getTotalDistance()/1000)} gesamt"

            if (trips.isEmpty()) {
                tvEmpty.visibility = android.view.View.VISIBLE
                tvEmpty.text = "Noch keine Fahrten vorhanden.\n\nManuell:\nIm Dashboard Fahrt manuell starten.\n\nAutomatisch:\nIn den Einstellungen automatische Erkennung aktivieren. Startet ab eingestellter Geschwindigkeit und stoppt nach 3 Minuten Stillstand.\n\nPunktesystem:\n- 10 Punkte pro km\n- 1 Punkt pro Minute\n- 20 Punkte fuer sanfte Fahrt (G < 0.3)\n- 25 Punkte fuer gleichmaessiges Fahren\n- 15 Punkte fuer optimale Geschwindigkeit\n\nEco Score: 0-100 fuer sparsames Fahren"
            } else {
                tvEmpty.visibility = android.view.View.GONE
                trips.take(50).forEach { trip ->
                    val tile = layoutInflater.inflate(R.layout.item_trip_tile, container, false)
                    val tvDate = tile.findViewById<TextView>(R.id.tvTileDate)
                    val tvType = tile.findViewById<TextView>(R.id.tvTileType)
                    val tvDist = tile.findViewById<TextView>(R.id.tvTileDistance)
                    val tvDur = tile.findViewById<TextView>(R.id.tvTileDuration)
                    val tvScore = tile.findViewById<TextView>(R.id.tvTileScore)
                    val tvEco = tile.findViewById<TextView>(R.id.tvTileEco)
                    val tvSpeed = tile.findViewById<TextView>(R.id.tvTileSpeed)

                    tvDate.text = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.GERMANY).format(java.util.Date(trip.startTime))
                    tvType.text = if (trip.isAuto) "Auto" else "Manuell"
                    tvDist.text = "${String.format("%.1f km", trip.distanceMeters/1000)}"
                    tvDur.text = "${trip.durationSec/60} Min"
                    tvScore.text = "${trip.score} Punkte"
                    tvEco.text = "Eco ${trip.ecoScore}"
                    tvSpeed.text = "Max ${trip.maxSpeedKmh.toInt()} km/h"

                    tile.setOnClickListener {
                        val intent = Intent(this, TripDetailActivity::class.java).apply {
                            putExtra("trip_id", trip.id)
                        }
                        startActivity(intent)
                    }

                    container.addView(tile)
                }
            }
        }

        loadTrips()
        btnClear.setOnClickListener {
            TripStorage(this).clearAll()
            loadTrips()
            Toast.makeText(this@MainActivity, "Verlauf geloescht", Toast.LENGTH_SHORT).show()
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

        val etFin = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etFinSettings)
        val etApiKey = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etApiKeySettings)
        val btnSaveProfile = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveProfile)
        val tvRateStatus = view.findViewById<TextView>(R.id.tvRateStatus)
        val rgEngine = view.findViewById<android.widget.RadioGroup>(R.id.rgEngineSettings)
        val rgTheme = view.findViewById<android.widget.RadioGroup>(R.id.rgAppTheme)
        val swAutoTrip = view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.swAutoTripSettings)
        val rgThreshold = view.findViewById<android.widget.RadioGroup>(R.id.rgThreshold)
        val etCustomBg = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etCustomBg)
        val etCustomAccent = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etCustomAccent)
        val btnSaveColors = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveColors)
        val btnLogout = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnLogout)

        lifecycleScope.launch {
            etFin.setText(settings.getVin())
            etApiKey.setText(settings.getApiKey())
            etCustomBg.setText(settings.getAppTheme())
            etCustomAccent.setText(settings.getWidgetAccent())
            tvRateStatus.text = RateLimiter(this@MainActivity).getStatusText()

            when (settings.getEngineType()) {
                "combustion" -> view.findViewById<android.widget.RadioButton>(R.id.rbCombustionSettings).isChecked = true
                "hybrid" -> view.findViewById<android.widget.RadioButton>(R.id.rbHybridSettings).isChecked = true
                else -> view.findViewById<android.widget.RadioButton>(R.id.rbElectricSettings).isChecked = true
            }

            when (settings.getAppTheme()) {
                "sand" -> view.findViewById<android.widget.RadioButton>(R.id.rbThemeSand).isChecked = true
                "mocca" -> view.findViewById<android.widget.RadioButton>(R.id.rbThemeMocca).isChecked = true
                "graphite" -> view.findViewById<android.widget.RadioButton>(R.id.rbThemeGraphite).isChecked = true
                else -> view.findViewById<android.widget.RadioButton>(R.id.rbThemeCreme).isChecked = true
            }

            swAutoTrip.isChecked = settings.getAutoTripEnabled()

            when (settings.getAutoTripThreshold()) {
                10 -> view.findViewById<android.widget.RadioButton>(R.id.rbThresh10).isChecked = true
                25 -> view.findViewById<android.widget.RadioButton>(R.id.rbThresh25).isChecked = true
                else -> view.findViewById<android.widget.RadioButton>(R.id.rbThresh15).isChecked = true
            }
        }

        btnSaveProfile.setOnClickListener {
            val fin = etFin.text.toString().trim().uppercase()
            val key = etApiKey.text.toString().trim()
            if (fin.length < 10 || key.length < 10) {
                Toast.makeText(this@MainActivity, "Bitte gueltige Werte eingeben", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                settings.saveVin(fin)
                settings.saveApiKey(key)
                Toast.makeText(this@MainActivity, "Profil gespeichert", Toast.LENGTH_SHORT).show()
            }
        }

        btnSaveColors.setOnClickListener {
            val bg = etCustomBg.text.toString().trim()
            val accent = etCustomAccent.text.toString().trim()
            lifecycleScope.launch {
                if (bg.isNotEmpty()) settings.saveAppTheme(bg)
                if (accent.isNotEmpty()) settings.saveWidgetAccent(accent)
                Toast.makeText(this@MainActivity, "Farben gespeichert: $bg / $accent", Toast.LENGTH_SHORT).show()
            }
        }

        rgEngine.setOnCheckedChangeListener { _, checkedId ->
            val type = when (checkedId) {
                R.id.rbCombustionSettings -> "combustion"
                R.id.rbHybridSettings -> "hybrid"
                else -> "electric"
            }
            lifecycleScope.launch {
                settings.saveEngineType(type)
                getSharedPreferences("widget_data", MODE_PRIVATE).edit().putString("engine_type", type).apply()
                Toast.makeText(this@MainActivity, "Antriebsart auf $type gestellt", Toast.LENGTH_SHORT).show()
            }
        }

        rgTheme.setOnCheckedChangeListener { _, checkedId ->
            val theme = when (checkedId) {
                R.id.rbThemeSand -> "sand"
                R.id.rbThemeMocca -> "mocca"
                R.id.rbThemeGraphite -> "graphite"
                else -> "creme"
            }
            lifecycleScope.launch {
                settings.saveAppTheme(theme)
                etCustomBg.setText(theme)
                Toast.makeText(this@MainActivity, "Design auf $theme gestellt", Toast.LENGTH_SHORT).show()
            }
        }

        swAutoTrip.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                settings.saveAutoTripEnabled(isChecked)
                if (isChecked) {
                    val intent = Intent(this@MainActivity, AutoTripService::class.java).apply { action = AutoTripService.ACTION_START }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                } else {
                    val intent = Intent(this@MainActivity, AutoTripService::class.java).apply { action = AutoTripService.ACTION_STOP }
                    startService(intent)
                }
            }
        }

        rgThreshold.setOnCheckedChangeListener { _, checkedId ->
            val thresh = when (checkedId) {
                R.id.rbThresh10 -> 10
                R.id.rbThresh25 -> 25
                else -> 15
            }
            lifecycleScope.launch {
                settings.saveAutoTripThreshold(thresh)
            }
        }

        btnLogout.setOnClickListener {
            lifecycleScope.launch {
                settings.clear()
                getSharedPreferences("widget_data", MODE_PRIVATE).edit().clear().apply()
                getSharedPreferences("rate_limit", MODE_PRIVATE).edit().clear().apply()
                TripStorage(this@MainActivity).clearAll()
                Toast.makeText(this@MainActivity, "Abgemeldet", Toast.LENGTH_SHORT).show()
                showLogin()
            }
        }
    }
}
