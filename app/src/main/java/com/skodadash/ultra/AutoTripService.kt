package com.skodadash.ultra

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.os.Handler
import android.os.Looper

class AutoTripService : Service(), LocationListener {

    companion object {
        const val ACTION_START = "AUTO_START"
        const val ACTION_STOP = "AUTO_STOP"
        var isRunning = false
    }

    private lateinit var locationManager: LocationManager
    private var speedCounter = 0
    private var lastLocation: Location? = null
    private var lastMovingTime = 0L
    private var thresholdKmh = 15
    private var isWaiting = true
    private val handler = Handler(Looper.getMainLooper())
    private val checkRunnable = object : Runnable {
        override fun run() {
            if (isRunning && !isWaiting && TripService.isRunning) {
                val now = System.currentTimeMillis()
                if (now - lastMovingTime > 120_000) { // 2 Min ohne Bewegung
                    val intent = Intent(this@AutoTripService, TripService::class.java).apply { action = TripService.ACTION_STOP }
                    startService(intent)
                    isWaiting = true
                    speedCounter = 0
                    updateNotification("Steht 2 Min - Fahrt beendet - wartet wieder")
                }
            }
            if (isRunning) handler.postDelayed(this, 5000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startAuto()
            ACTION_STOP -> stopAuto()
        }
        return START_STICKY
    }

    private fun startAuto() {
        isRunning = true
        speedCounter = 0
        lastLocation = null
        lastMovingTime = System.currentTimeMillis()
        isWaiting = true

        thresholdKmh = getSharedPreferences("settings_cache", MODE_PRIVATE).getInt("auto_threshold", 15)
        Thread {
            try {
                val settings = SettingsRepository(this)
                val thresh = kotlinx.coroutines.runBlocking { settings.getAutoTripThreshold() }
                thresholdKmh = thresh
                getSharedPreferences("settings_cache", MODE_PRIVATE).edit().putInt("auto_threshold", thresh).apply()
            } catch (_: Exception) {}
        }.start()

        val notification = buildNotification("Auto aktiv - wartet ab ${thresholdKmh} km/h")
        startForeground(2, notification)
        handler.postDelayed(checkRunnable, 5000)

        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, this)
            // Fallback network for faster fix, but filter speed 0 later
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 5f, this)
        } catch (e: SecurityException) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun stopAuto() {
        isRunning = false
        handler.removeCallbacks(checkRunnable)
        try { locationManager.removeUpdates(this) } catch (_: Exception) {}
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onLocationChanged(location: Location) {
        if (location.accuracy > 50f) return

        var speedKmh = if (location.hasSpeed()) location.speed * 3.6 else 0.0

        // Falls kein Speed, berechne aus Distanz
        if (!location.hasSpeed() || speedKmh < 0.5) {
            lastLocation?.let { last ->
                val dist = last.distanceTo(location)
                val timeDiff = (location.time - last.time) / 1000.0
                if (timeDiff > 0 && timeDiff < 10) {
                    speedKmh = (dist / timeDiff) * 3.6
                }
            }
        }

        if (speedKmh >= thresholdKmh) {
            speedCounter++
            lastMovingTime = System.currentTimeMillis()
            if (isWaiting) {
                updateNotification("Bewegung ${speedKmh.toInt()} km/h - Start in ${3 - speedCounter}")
            }
            if (speedCounter >= 3 && !TripService.isRunning) {
                val intent = Intent(this, TripService::class.java).apply { action = TripService.ACTION_START_AUTO }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
                updateNotification("Fahrt läuft - ${speedKmh.toInt()} km/h")
                isWaiting = false
                speedCounter = 0
            }
        } else if (speedKmh < 5) {
            speedCounter = 0
            if (!isWaiting && TripService.isRunning) {
                // lastMovingTime wird nicht aktualisiert, checkRunnable stoppt nach 2 Min
                updateNotification("Langsam ${speedKmh.toInt()} km/h - stoppt nach 2 Min Stillstand")
            }
        } else {
            speedCounter = 0
        }

        lastLocation = location
    }

    private fun buildNotification(content: String): Notification {
        return NotificationCompat.Builder(this, "auto_trip_channel")
            .setContentTitle("Auto Erkennung")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(content: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(2, buildNotification(content))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("auto_trip_channel", "Auto Erkennung", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Automatische Fahrt Erkennung"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
