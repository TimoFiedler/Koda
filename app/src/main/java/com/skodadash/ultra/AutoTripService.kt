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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    private var scope = CoroutineScope(Dispatchers.Main + Job())

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

        scope.launch {
            val settings = SettingsRepository(this@AutoTripService)
            thresholdKmh = settings.getAutoTripThreshold()
        }

        val notification = buildNotification("Auto-Erkennung aktiv - wartet auf Fahrt")
        startForeground(2, notification)

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                2000L,
                5f,
                this
            )
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                3000L,
                10f,
                this
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun stopAuto() {
        isRunning = false
        try {
            locationManager.removeUpdates(this)
        } catch (e: Exception) {}
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onLocationChanged(location: Location) {
        val speedKmh = location.speed * 3.6

        if (speedKmh >= thresholdKmh) {
            speedCounter++
            stationaryCounter = 0
            if (speedCounter >= 5 && !TripService.isRunning) {
                // Auto start trip
                val intent = Intent(this, TripService::class.java).apply { action = TripService.ACTION_START_AUTO }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                updateNotification("Fahrt automatisch erkannt - Aufzeichnung läuft")
                speedCounter = 0
            }
        } else if (speedKmh < 5) {
            stationaryCounter++
            speedCounter = 0
            if (stationaryCounter >= 90 && TripService.isRunning) {
                // 90 * 2s = 3min stationary -> auto stop
                val intent = Intent(this, TripService::class.java).apply { action = TripService.ACTION_STOP }
                startService(intent)
                updateNotification("Fahrzeug steht - Aufzeichnung beendet")
                stationaryCounter = 0
            }
        } else {
            speedCounter = 0
            stationaryCounter = 0
        }
    }

    private fun buildNotification(content: String): Notification {
        return NotificationCompat.Builder(this, "auto_trip_channel")
            .setContentTitle("SkodaDash Auto")
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
            val channel = NotificationChannel(
                "auto_trip_channel",
                "Auto Fahrt Erkennung",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
