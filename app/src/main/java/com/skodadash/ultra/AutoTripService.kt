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

class AutoTripService : Service(), LocationListener {

    companion object {
        const val ACTION_START = "AUTO_START"
        const val ACTION_STOP = "AUTO_STOP"
        var isRunning = false
    }

    private lateinit var locationManager: LocationManager
    private var speedCounter = 0
    private var stationaryCounter = 0
    private var thresholdKmh = 15
    private var isWaiting = true

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
        if (isRunning) return
        isRunning = true
        speedCounter = 0
        stationaryCounter = 0
        isWaiting = true

        // Lade Schwelle synchron aus Cache für sofortigen Start
        thresholdKmh = getSharedPreferences("settings_cache", MODE_PRIVATE).getInt("auto_threshold", 15)
        // Async update aus DataStore
        Thread {
            try {
                val settings = SettingsRepository(this)
                val thresh = kotlinx.coroutines.runBlocking { settings.getAutoTripThreshold() }
                thresholdKmh = thresh
                getSharedPreferences("settings_cache", MODE_PRIVATE).edit().putInt("auto_threshold", thresh).apply()
            } catch (_: Exception) {}
        }.start()

        val notification = buildNotification("Auto-Erkennung aktiv - wartet auf Fahrt ab ${thresholdKmh} km/h")
        startForeground(2, notification)

        try {
            // Nur GPS für Auto-Erkennung - Netzwerk liefert oft 0 km/h und stört
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1500L, 2f, this)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun stopAuto() {
        isRunning = false
        try { locationManager.removeUpdates(this) } catch (_: Exception) {}
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onLocationChanged(location: Location) {
        // Filter ungenaue Punkte
        if (location.accuracy > 40f) return
        if (!location.hasSpeed()) return

        val speedKmh = location.speed * 3.6

        if (speedKmh >= thresholdKmh) {
            speedCounter++
            stationaryCounter = 0
            if (isWaiting) {
                updateNotification("Bewegung erkannt ${speedKmh.toInt()} km/h - starte in ${3 - speedCounter}...")
            }
            // 3 Messungen über Schwelle = ca 4.5 Sekunden -> sicher dass wirklich fährt
            if (speedCounter >= 3 && !TripService.isRunning) {
                val intent = Intent(this, TripService::class.java).apply { action = TripService.ACTION_START_AUTO }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
                updateNotification("Fahrt erkannt - Aufzeichnung läuft")
                isWaiting = false
                speedCounter = 0
            }
        } else if (speedKmh < 4) {
            stationaryCounter++
            speedCounter = 0
            if (!isWaiting && TripService.isRunning) {
                // 80 * 1.5s = 120s = 2 Min Stillstand -> Stop
                if (stationaryCounter >= 80) {
                    val intent = Intent(this, TripService::class.java).apply { action = TripService.ACTION_STOP }
                    startService(intent)
                    updateNotification("Steht seit 2 Min - Aufzeichnung beendet - wartet wieder")
                    isWaiting = true
                    stationaryCounter = 0
                } else if (stationaryCounter % 20 == 0) {
                    updateNotification("Steht ${stationaryCounter * 1.5 / 60} Min - stoppt gleich")
                }
            } else if (isWaiting) {
                // Reset waiting message
                if (stationaryCounter % 30 == 0) {
                    updateNotification("Auto-Erkennung aktiv - wartet auf Fahrt ab ${thresholdKmh} km/h")
                }
            }
        } else {
            // Zwischen 4 und Schwelle - weder fahren noch stehen
            speedCounter = 0
            // stationary nicht erhöhen
        }
    }

    private fun buildNotification(content: String): Notification {
        return NotificationCompat.Builder(this, "auto_trip_channel")
            .setContentTitle("Auto Erkennung")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(2, buildNotification(content))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("auto_trip_channel", "Auto Fahrt Erkennung", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
