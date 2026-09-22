package com.skodadash.ultra

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import com.skodadash.ultra.databinding.ActivityMainBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: SettingsRepository
    private lateinit var api: SkodaApi
    private var trackingJob: Job? = null
    private var currentTab = 0

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            lifecycleScope.launch {
                settings.saveProfileImageUri(it.toString())
                Toast.makeText(this@MainActivity, "Profilbild gespeichert", Toast.LENGTH_SHORT).show()
                if (currentTab == 0) showDashboard()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = SettingsRepository(this@MainActivity)
        api = SkodaApi(settings, this@MainActivity)

        lifecycleScope.launch {
            applyCustomColors()
        }

        val perms = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        ).filter { ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED }
        if (perms.isNotEmpty()) permissionLauncher.launch(perms.toTypedArray())

        lifecycleScope.launch {
            val apiKey = settings.getApiKey()
            val vin = settings.getVin()
            if (apiKey.isEmpty() || vin.isEmpty()) {
                showLogin()
            } else {
                showDashboard()
                if (settings.getAutoTripEnabled()) {
                    try {
                        val intent = Intent(this@MainActivity, AutoTripService::class.java).also { it.action = AutoTripService.ACTION_START }
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
                    } catch (_: Exception) {}
                }
            }
        }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                trackingJob?.cancel()
                currentTab = tab?.position ?: 0
                when (currentTab) {
                    0 -> showDashboard()
                    1 -> showTrips()
                    2 -> showSettings()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { applyCustomColors() }
    }

    override fun onDestroy() {
        trackingJob?.cancel()
        super.onDestroy()
    }

    private suspend fun applyCustomColors() {
        try {
            val customBg = settings.getCustomBgHex()
            val customAccent = settings.getCustomAccentHex()
            val appTheme = settings.getAppTheme()
            val widgetAccent = settings.getWidgetAccent()

            val bgHex = customBg.ifEmpty { appTheme }
            val accentHex = customAccent.ifEmpty { widgetAccent }

            val bgColor = ColorHelper.parseColor(bgHex)
            val accentColor = ColorHelper.parseColor(accentHex)

            getSharedPreferences("theme_cache", MODE_PRIVATE).edit().apply {
                bgColor?.let { putInt("bg_color", it) }
                accentColor?.let { putInt("accent_color", it) }
                putString("bg_hex", bgHex)
                putString("accent_hex", accentHex)
                apply()
            }

            // Force black for dark reference, ignore custom bg if empty check? Keep custom but dashboard forced black
            binding.root.setBackgroundColor(Color.parseColor("#FF000000"))
        } catch (_: Exception) {}
    }

    private fun showLogin() {
        binding.content.removeAllViews()
        val view = layoutInflater.inflate(R.layout.layout_login, binding.content, false)
        val etApiKey = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etApiKey)
        val etVin = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etVin)
        val btnLogin = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnLogin)
        val tvHelp = view.findViewById<TextView>(R.id.tvHelp)
        val rgEngine = view.findViewById<android.widget.RadioGroup>(R.id.rgEngineType)

        tvHelp.text = "1. MySkoda App oeffnen\n2. Profil -> Drittanbieter-Zugriff\n3. API-Key erstellen\n4. FIN und Key eingeben\n\nMinimal und genau - nur GPS, keine Netzwerk Ortung"

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
                Toast.makeText(this@MainActivity, "Bitte gueltige Werte", Toast.LENGTH_SHORT).show()
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
        val tvRangeTile = view.findViewById<TextView>(R.id.tvRangeTile)
        val tvOdo = view.findViewById<TextView>(R.id.tvOdo)
        val tvLock = view.findViewById<TextView>(R.id.tvLock)
        val tvCharging = view.findViewById<TextView>(R.id.tvCharging)
        val tvEngineBadge = view.findViewById<TextView>(R.id.tvEngineBadge)
        val tvEngineBadge2 = view.findViewById<TextView>(R.id.tvEngineBadge2)
        val tvAutoStatus = view.findViewById<TextView>(R.id.tvAutoStatus)
        val btnRefresh = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRefresh)
        val btnStartTrip = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnStartTrip)
        val btnStopTrip = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnStopTrip)
        val swAutoTrip = view.findViewById<SwitchCompat>(R.id.swAutoTrip)
        val tvRateLimit = view.findViewById<TextView>(R.id.tvRateLimit)
        val ivProfile = view.findViewById<ImageView>(R.id.ivProfile)
        val tvCarModelLabel = view.findViewById<TextView>(R.id.tvCarModelLabel)

        var initializing = true
        lifecycleScope.launch {
            val carModel = settings.getCarModel()
            tvEngineBadge.text = carModel.uppercase()
            tvEngineBadge2.text = carModel.uppercase()

            val engineType = settings.getEngineType()
            tvBatteryLabel.text = when (engineType) {
                "combustion" -> "Tank"
                "hybrid" -> "Akku"
                else -> "Ladezustand"
            }

            val profileUri = settings.getProfileImageUri()
            if (profileUri.isNotEmpty()) {
                try {
                    ivProfile.setImageURI(Uri.parse(profileUri))
                } catch (_: Exception) {}
            }

            swAutoTrip.isChecked = settings.getAutoTripEnabled()
            tvAutoStatus.text = if (settings.getAutoTripEnabled()) "Auto an" else "Auto aus"
            tvRateLimit.text = RateLimiter(this@MainActivity).getStatusText()
            initializing = false
        }

        ivProfile.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        val carModelClickListener = View.OnClickListener {
            val editText = EditText(this@MainActivity)
            editText.hint = "z.B. SCALA, OCTAVIA"
            lifecycleScope.launch {
                editText.setText(settings.getCarModel())
            }
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Auto Modell")
                .setMessage("Welches Auto hast du? z.B. Scala")
                .setView(editText)
                .setPositiveButton("Speichern") { _, _ ->
                    val model = editText.text.toString().trim().ifEmpty { "SCALA" }
                    lifecycleScope.launch {
                        settings.saveCarModel(model)
                        tvEngineBadge.text = model.uppercase()
                        tvEngineBadge2.text = model.uppercase()
                        Toast.makeText(this@MainActivity, "Modell: $model", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        }
        tvEngineBadge.setOnClickListener(carModelClickListener)
        tvEngineBadge2.setOnClickListener(carModelClickListener)
        tvCarModelLabel.setOnClickListener(carModelClickListener)

        fun updateTrackingButtons() {
            try {
                val running = TripService.isRunning
                val autoRunning = AutoTripService.isRunning
                btnStartTrip.isEnabled = !running
                btnStopTrip.isEnabled = running
                btnStartTrip.text = if (running) "Aufzeichnung laeuft" else "Fahrt starten"
                tvAutoStatus.text = when {
                    running && autoRunning -> "Auto - Fahrt laeuft"
                    running -> "Manuell - laeuft"
                    autoRunning -> "Auto wartet"
                    else -> "Bereit"
                }
            } catch (_: Exception) {}
        }

        swAutoTrip.setOnCheckedChangeListener { _, isChecked ->
            if (initializing) return@setOnCheckedChangeListener
            lifecycleScope.launch {
                settings.saveAutoTripEnabled(isChecked)
                getSharedPreferences("settings_cache", MODE_PRIVATE).edit().putInt("auto_threshold", settings.getAutoTripThreshold()).apply()
                if (isChecked) {
                    val intent = Intent(this@MainActivity, AutoTripService::class.java).also { it.action = AutoTripService.ACTION_START }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
                    Toast.makeText(this@MainActivity, "Auto Erkennung an", Toast.LENGTH_SHORT).show()
                } else {
                    val intent = Intent(this@MainActivity, AutoTripService::class.java).also { it.action = AutoTripService.ACTION_STOP }
                    startService(intent)
                    Toast.makeText(this@MainActivity, "Auto aus", Toast.LENGTH_SHORT).show()
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
                btnRefresh.isEnabled = false
                try {
                    val data = api.fetchVehicle()
                    if (data != null) {
                        tvStatus.text = "${data.name} - ${settings.getCarModel()}"
                        tvRateLimit.text = limiter.getStatusText()
                        tvBattery.text = data.batteryPercent?.let { "${it.toInt()}%" } ?: "--"
                        tvRange.text = data.rangeKm?.let { "${it.toInt()} km" } ?: "--"
                        tvRangeTile.text = data.rangeKm?.let { "${it.toInt()} km" } ?: "--"
                        tvOdo.text = data.odometerKm?.let { "${it.toInt()} km" } ?: "--"
                        tvLock.text = when (data.doorsLocked) { true -> "Verriegelt" else -> "Offen" }
                        tvCharging.text = data.chargingState ?: "Bereit"
                        getSharedPreferences("widget_data", MODE_PRIVATE).edit().apply {
                            putInt("battery", data.batteryPercent?.toInt() ?: -1)
                            putInt("range", data.rangeKm?.toInt() ?: -1)
                            putInt("odometer", data.odometerKm?.toInt() ?: -1)
                            putBoolean("locked", data.doorsLocked ?: false)
                            putString("charging", data.chargingState ?: "")
                            putString("name", data.name)
                            putString("engine_type", settings.getEngineType())
                            putString("car_model", settings.getCarModel())
                            apply()
                        }
                    } else {
                        tvStatus.text = "Keine Daten"
                    }
                } catch (e: Exception) {
                    tvStatus.text = e.message ?: "Fehler"
                } finally {
                    btnRefresh.isEnabled = true
                }
            }
        }

        btnStartTrip.setOnClickListener {
            val intent = Intent(this@MainActivity, TripService::class.java).also { it.action = TripService.ACTION_START }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            Toast.makeText(this@MainActivity, "Gestartet - nur GPS", Toast.LENGTH_SHORT).show()
            updateTrackingButtons()
        }

        btnStopTrip.setOnClickListener {
            val intent = Intent(this@MainActivity, TripService::class.java).also { it.action = TripService.ACTION_STOP }
            startService(intent)
            Toast.makeText(this@MainActivity, "Beendet", Toast.LENGTH_SHORT).show()
            updateTrackingButtons()
        }

        btnRefresh.performClick()

        trackingJob?.cancel()
        trackingJob = lifecycleScope.launch {
            while (isActive && currentTab == 0) {
                delay(1200)
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
        val tvTotalDistance = view.findViewById<TextView>(R.id.tvTotalDistance)
        val tvTotalDuration = view.findViewById<TextView>(R.id.tvTotalDuration)
        val tvStandzeit = view.findViewById<TextView>(R.id.tvStandzeit)
        val tvTotalTrips = view.findViewById<TextView>(R.id.tvTotalTrips)
        val tvMaxSpeed = view.findViewById<TextView>(R.id.tvMaxSpeed)
        val tvLeftCurves = view.findViewById<TextView>(R.id.tvLeftCurves)
        val tvRightCurves = view.findViewById<TextView>(R.id.tvRightCurves)
        val tvBrakes = view.findViewById<TextView>(R.id.tvBrakes)
        val tvLaneChanges = view.findViewById<TextView>(R.id.tvLaneChanges)
        val tvCurveLeft = view.findViewById<TextView>(R.id.tvCurveLeft)
        val tvCurveRight = view.findViewById<TextView>(R.id.tvCurveRight)
        val tvMaxBrake = view.findViewById<TextView>(R.id.tvMaxBrake)
        val tvMaxAccel = view.findViewById<TextView>(R.id.tvMaxAccel)
        val tvMaxG = view.findViewById<TextView>(R.id.tvMaxG)
        val tvTotalTrips2 = view.findViewById<TextView>(R.id.tvTotalTrips2)
        val tvTotalStops = view.findViewById<TextView>(R.id.tvTotalStops)
        val tvAvgDistance = view.findViewById<TextView>(R.id.tvAvgDistance)
        val tvTotalDuration2 = view.findViewById<TextView>(R.id.tvTotalDuration2)
        val tvWeeklySummary = view.findViewById<TextView>(R.id.tvWeeklySummary)
        val chartView = view.findViewById<StatsChartView>(R.id.chartView)
        val tvTotalScore = view.findViewById<TextView>(R.id.tvTotalScore)
        val tvLevel = view.findViewById<TextView>(R.id.tvLevel)
        val tvStatsDetail = view.findViewById<TextView>(R.id.tvStatsDetail)
        val tvAchievementsCount = view.findViewById<TextView>(R.id.tvAchievementsCount)
        val tvAchievements = view.findViewById<TextView>(R.id.tvAchievements)
        val tvNextAchievement = view.findViewById<TextView>(R.id.tvNextAchievement)
        val tvCost = view.findViewById<TextView>(R.id.tvCost)
        val btnClear = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnClearTrips)

        fun loadTrips() {
            container.removeAllViews()
            val storage = TripStorage(this@MainActivity)
            val trips = storage.getTrips()
            val tripsReversed = trips.reversed()

            val totalKm = storage.getTotalDistance() / 1000.0
            val totalDurMin = storage.getTotalDuration() / 60
            val avgDist = if (trips.isNotEmpty()) totalKm / trips.size else 0.0
            val maxSpeed = trips.maxOfOrNull { it.maxSpeedKmh } ?: 0.0
            val maxBrake = trips.maxOfOrNull { it.maxBrake } ?: 0.0
            val maxAccel = trips.maxOfOrNull { it.maxAccel } ?: 0.0
            val maxG = trips.maxOfOrNull { it.maxG } ?: 0.0

            val allEvents = trips.flatMap { it.events }
            val leftCurves = allEvents.count { it.type.contains("CORNER") && it.value < 0 }
            val rightCurves = allEvents.count { it.type.contains("CORNER") && it.value >= 0 }
            val brakes = allEvents.count { it.type.contains("BRAKE") }
            val laneChanges = allEvents.count { it.type.contains("CORNER") }

            val totalCurve = leftCurves + rightCurves
            val leftPct = if (totalCurve > 0) leftCurves * 100.0 / totalCurve else 50.0
            val rightPct = 100.0 - leftPct

            tvTotalDistance.text = String.format("%.1f km", totalKm)
            tvTotalDuration.text = if (totalDurMin < 60) "${totalDurMin}m" else "${totalDurMin/60}h ${totalDurMin%60}m"
            tvStandzeit.text = "${(totalDurMin * 0.2).toInt()}m"
            tvTotalTrips.text = "${trips.size}"
            tvMaxSpeed.text = "${maxSpeed.toInt()} km/h"

            tvLeftCurves.text = "$leftCurves"
            tvRightCurves.text = "$rightCurves"
            tvBrakes.text = "$brakes"
            tvLaneChanges.text = "$laneChanges"

            tvCurveLeft.text = String.format("%.1f%%", leftPct)
            tvCurveRight.text = String.format("%.1f%%", rightPct)
            try {
                tvCurveLeft.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, leftPct.toFloat().coerceAtLeast(10f))
                tvCurveRight.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, rightPct.toFloat().coerceAtLeast(10f))
            } catch (_: Exception) {}

            tvMaxBrake.text = String.format("%.1f m/s²", maxBrake)
            tvMaxAccel.text = String.format("%.1f m/s²", maxAccel)
            tvMaxG.text = String.format("%.2f G", maxG)

            tvTotalTrips2.text = "${trips.size}"
            tvTotalStops.text = "$brakes"
            tvAvgDistance.text = String.format("%.1f km", avgDist)
            tvTotalDuration2.text = if (totalDurMin < 60) "${totalDurMin}m" else "${totalDurMin/60}h"

            val daily = StatsHelper.getLast7Days(trips)
            chartView.setData(daily)
            val weekly = StatsHelper.getWeeklySummary(trips)
            tvWeeklySummary.text = "${String.format("%.1f km", weekly.totalKm)} diese Woche"

            tvTotalScore.text = "${storage.getTotalScore()} Punkte"
            tvLevel.text = storage.getLevel()
            tvStatsDetail.text = "${String.format("%.1f km", totalKm)}"

            val (unlocked, totalAch) = AchievementsHelper.getUnlockedCount(trips)
            tvAchievementsCount.text = "$unlocked/$totalAch Erfolge"
            val unlockedAch = AchievementsHelper.getAchievements(trips).filter { it.unlocked }.takeLast(3)
            tvAchievements.text = if (unlockedAch.isEmpty()) "Fahre los für Erfolge" else unlockedAch.joinToString(", ") { it.title }
            val next = AchievementsHelper.getNextAchievement(trips)
            tvNextAchievement.text = next?.let { "Nächstes: ${it.title} ${it.progress}/${it.target}" } ?: "Alle erreicht!"

            lifecycleScope.launch {
                val engineType = settings.getEngineType()
                val costTotal = CostCalculator.calculateTotal(trips, engineType)
                tvCost.text = "${String.format("%.2f €", costTotal.costEuro)} • ${String.format("%.0f kg CO2", costTotal.co2Kg)}"
            }

            if (trips.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                tvEmpty.text = "Noch keine Fahrten.\n\nTracking minimal und genau: Nur GPS, Filter <30m, 1-200m Distanz"
            } else {
                tvEmpty.visibility = View.GONE
                tripsReversed.take(50).forEach { trip ->
                    val tile = layoutInflater.inflate(R.layout.item_trip_tile, container, false)
                    val tvDate = tile.findViewById<TextView>(R.id.tvTileDate)
                    val tvType = tile.findViewById<TextView>(R.id.tvTileType)
                    val tvDist = tile.findViewById<TextView>(R.id.tvTileDistance)
                    val tvDur = tile.findViewById<TextView>(R.id.tvTileDuration)
                    val tvScore = tile.findViewById<TextView>(R.id.tvTileScore)
                    val tvEco = tile.findViewById<TextView>(R.id.tvTileEco)
                    val tvSpeed = tile.findViewById<TextView>(R.id.tvTileSpeed)
                    val tvEvents = tile.findViewById<TextView>(R.id.tvTileEvents)

                    val displayName = trip.customName.ifEmpty { StatsHelper.suggestTripName(trip) }
                    tvDate.text = displayName
                    tvType.text = "${if (trip.isAuto) "Auto" else "Manuell"} - ${java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.GERMANY).format(java.util.Date(trip.startTime))}"
                    tvDist.text = "${String.format("%.1f km", trip.distanceMeters/1000)}"
                    tvDur.text = "${trip.durationSec/60} Min"
                    tvScore.text = "${trip.score} Pkt"
                    tvEco.text = trip.getDrivingStyle()
                    tvSpeed.text = "${trip.maxSpeedKmh.toInt()} km/h max"
                    tvEvents.text = "${trip.events.size} Events - ${if (trip.notes.isNotEmpty()) "Notiz: ${trip.notes.take(20)}" else "Tippe für Karte + Replay"}"

                    val accent = getSharedPreferences("theme_cache", MODE_PRIVATE).getInt("accent_color", Color.parseColor("#8B7355"))
                    tvScore.setTextColor(accent)

                    tile.setOnClickListener {
                        val intent = Intent(this@MainActivity, TripDetailActivity::class.java).also { it.putExtra("trip_id", trip.id) }
                        startActivity(intent)
                    }
                    container.addView(tile)
                }
            }
        }

        loadTrips()
        btnClear.setOnClickListener {
            TripStorage(this@MainActivity).clearAll()
            loadTrips()
            Toast.makeText(this@MainActivity, "Gelöscht", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSettings() {
        binding.content.removeAllViews()
        val view = layoutInflater.inflate(R.layout.layout_settings, binding.content, false)
        binding.content.addView(view)

        val etFin = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etFinSettings)
        val etApiKey = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etApiKeySettings)
        val etCarModel = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etCarModelSettings)
        val btnSaveProfile = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveProfile)
        val tvRateStatus = view.findViewById<TextView>(R.id.tvRateStatus)
        val rgEngine = view.findViewById<android.widget.RadioGroup>(R.id.rgEngineSettings)
        val swAutoTrip = view.findViewById<SwitchCompat>(R.id.swAutoTripSettings)
        val rgThreshold = view.findViewById<android.widget.RadioGroup>(R.id.rgThreshold)
        val etCustomBg = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etCustomBg)
        val etCustomAccent = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etCustomAccent)
        val btnSaveColors = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveColors)
        val viewPreview = view.findViewById<View>(R.id.viewColorPreview)
        val btnLogout = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnLogout)
        val btnPickProfile = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnPickProfileImage)

        var initializing = true
        lifecycleScope.launch {
            etFin.setText(settings.getVin())
            etApiKey.setText(settings.getApiKey())
            etCarModel.setText(settings.getCarModel())
            etCustomBg.setText(settings.getCustomBgHex().ifEmpty { settings.getAppTheme() })
            etCustomAccent.setText(settings.getCustomAccentHex().ifEmpty { settings.getWidgetAccent() })
            tvRateStatus.text = RateLimiter(this@MainActivity).getStatusText()

            when (settings.getEngineType()) {
                "combustion" -> view.findViewById<android.widget.RadioButton>(R.id.rbCombustionSettings).isChecked = true
                "hybrid" -> view.findViewById<android.widget.RadioButton>(R.id.rbHybridSettings).isChecked = true
                else -> view.findViewById<android.widget.RadioButton>(R.id.rbElectricSettings).isChecked = true
            }

            swAutoTrip.isChecked = settings.getAutoTripEnabled()
            when (settings.getAutoTripThreshold()) {
                10 -> view.findViewById<android.widget.RadioButton>(R.id.rbThresh10).isChecked = true
                25 -> view.findViewById<android.widget.RadioButton>(R.id.rbThresh25).isChecked = true
                else -> view.findViewById<android.widget.RadioButton>(R.id.rbThresh15).isChecked = true
            }

            val bg = ColorHelper.parseColor(etCustomBg.text.toString()) ?: Color.parseColor("#FFF8E7")
            val accent = ColorHelper.parseColor(etCustomAccent.text.toString()) ?: Color.parseColor("#8B7355")
            val drawable = GradientDrawable().also {
                it.shape = GradientDrawable.RECTANGLE
                it.cornerRadius = 24f
                it.setColor(bg)
                it.setStroke(2, accent)
            }
            viewPreview.background = drawable

            initializing = false
        }

        fun updatePreview() {
            val bg = ColorHelper.parseColor(etCustomBg.text.toString()) ?: Color.parseColor("#FFF8E7")
            val accent = ColorHelper.parseColor(etCustomAccent.text.toString()) ?: Color.parseColor("#8B7355")
            val drawable = GradientDrawable().also {
                it.shape = GradientDrawable.RECTANGLE
                it.cornerRadius = 24f
                it.setColor(bg)
                it.setStroke(4, accent)
            }
            viewPreview.background = drawable
        }

        etCustomBg.setOnFocusChangeListener { _, _ -> updatePreview() }
        etCustomAccent.setOnFocusChangeListener { _, _ -> updatePreview() }

        btnPickProfile.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        btnSaveProfile.setOnClickListener {
            val fin = etFin.text.toString().trim().uppercase()
            val key = etApiKey.text.toString().trim()
            val carModel = etCarModel.text.toString().trim().ifEmpty { "SCALA" }
            if (fin.length < 10 || key.length < 10) {
                Toast.makeText(this@MainActivity, "Bitte gueltige Werte", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                settings.saveVin(fin)
                settings.saveApiKey(key)
                settings.saveCarModel(carModel)
                Toast.makeText(this@MainActivity, "Profil gespeichert - $carModel", Toast.LENGTH_SHORT).show()
            }
        }

        btnSaveColors.setOnClickListener {
            val bg = etCustomBg.text.toString().trim()
            val accent = etCustomAccent.text.toString().trim()
            val bgColor = ColorHelper.parseColor(bg)
            val accentColor = ColorHelper.parseColor(accent)
            if (bgColor == null && bg.isNotEmpty() && !bg.matches(Regex("(?i)creme|sand|mocca|graphite|brown|gold|sage|olive"))) {
                Toast.makeText(this@MainActivity, "Ungültige Hintergrund Farbe: $bg", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (accentColor == null && accent.isNotEmpty() && !accent.matches(Regex("(?i)creme|sand|mocca|graphite|brown|gold|sage|olive"))) {
                Toast.makeText(this@MainActivity, "Ungültige Akzent Farbe: $accent", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                settings.saveCustomBgHex(bg)
                settings.saveCustomAccentHex(accent)
                if (bg.isNotEmpty()) settings.saveAppTheme(bg)
                if (accent.isNotEmpty()) settings.saveWidgetAccent(accent)
                applyCustomColors()
                updatePreview()
                Toast.makeText(this@MainActivity, "Farben angewendet", Toast.LENGTH_SHORT).show()
                showSettings()
            }
        }

        rgEngine.setOnCheckedChangeListener { _, checkedId ->
            if (initializing) return@setOnCheckedChangeListener
            val type = when (checkedId) {
                R.id.rbCombustionSettings -> "combustion"
                R.id.rbHybridSettings -> "hybrid"
                else -> "electric"
            }
            lifecycleScope.launch {
                settings.saveEngineType(type)
                Toast.makeText(this@MainActivity, "Antrieb: $type", Toast.LENGTH_SHORT).show()
            }
        }

        swAutoTrip.setOnCheckedChangeListener { _, isChecked ->
            if (initializing) return@setOnCheckedChangeListener
            lifecycleScope.launch {
                settings.saveAutoTripEnabled(isChecked)
                getSharedPreferences("settings_cache", MODE_PRIVATE).edit().putInt("auto_threshold", settings.getAutoTripThreshold()).apply()
                if (isChecked) {
                    val intent = Intent(this@MainActivity, AutoTripService::class.java).also { it.action = AutoTripService.ACTION_START }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
                    Toast.makeText(this@MainActivity, "Auto an", Toast.LENGTH_SHORT).show()
                } else {
                    val intent = Intent(this@MainActivity, AutoTripService::class.java).also { it.action = AutoTripService.ACTION_STOP }
                    startService(intent)
                    Toast.makeText(this@MainActivity, "Auto aus", Toast.LENGTH_SHORT).show()
                }
            }
        }

        rgThreshold.setOnCheckedChangeListener { _, checkedId ->
            if (initializing) return@setOnCheckedChangeListener
            val thresh = when (checkedId) {
                R.id.rbThresh10 -> 10
                R.id.rbThresh25 -> 25
                else -> 15
            }
            lifecycleScope.launch {
                settings.saveAutoTripThreshold(thresh)
                getSharedPreferences("settings_cache", MODE_PRIVATE).edit().putInt("auto_threshold", thresh).apply()
                Toast.makeText(this@MainActivity, "Schwelle $thresh km/h", Toast.LENGTH_SHORT).show()
            }
        }

        btnLogout.setOnClickListener {
            lifecycleScope.launch {
                settings.clear()
                getSharedPreferences("widget_data", MODE_PRIVATE).edit().clear().apply()
                getSharedPreferences("rate_limit", MODE_PRIVATE).edit().clear().apply()
                getSharedPreferences("theme_cache", MODE_PRIVATE).edit().clear().apply()
                getSharedPreferences("settings_cache", MODE_PRIVATE).edit().clear().apply()
                TripStorage(this@MainActivity).clearAll()
                Toast.makeText(this@MainActivity, "Abgemeldet", Toast.LENGTH_SHORT).show()
                showLogin()
            }
        }
    }
}
