package com.skodadash.ultra

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlin.math.sqrt

class TripService : Service(), SensorEventListener, LocationListener {

    companion object {
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
        var isRunning = false
    }

    private lateinit var locationManager: LocationManager
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lastLocation: Location? = null
    private var tripId: Long = 0
    private var distanceMeters = 0.0
    private var maxSpeedKmh = 0.0
    private var avgSpeedSum = 0.0
    private var speedCount = 0
    private var maxG = 0.0
    private var maxAccel = 0.0
    private var maxBrake = 0.0
    private var pointCount = 0
    private var startTime = 0L

    private var lastAccelX = 0f
    private var lastAccelY = 0f
    private var lastAccelZ = 0f

    private val GRAVITY_EARTH = 9.81f

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTrip()
            ACTION_STOP -> stopTrip()
        }
        return START_STICKY
    }

    private fun startTrip() {
        if (isRunning) return
        isRunning = true
        startTime = System.currentTimeMillis()
        tripId = startTime
        distanceMeters = 0.0
        maxSpeedKmh = 0.0
        avgSpeedSum = 0.0
        speedCount = 0
        maxG = 0.0
        maxAccel = 0.0
        maxBrake = 0.0
        pointCount = 0
        lastLocation = null

        val notification = buildNotification("Fahrt läuft - GPS aktiv")
        startForeground(1, notification)

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                1f,
                this
            )
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                1000L,
                1f,
                this
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun stopTrip() {
        if (!isRunning) {
            stopSelf()
            return
        }

        val endTime = System.currentTimeMillis()
        val durationSec = (endTime - startTime) / 1000

        val trip = TripData(
            id = tripId,
            startTime = startTime,
            endTime = endTime,
            distanceMeters = distanceMeters,
            durationSec = durationSec,
            maxSpeedKmh = maxSpeedKmh,
            avgSpeedKmh = if (speedCount > 0) avgSpeedSum / speedCount else 0.0,
            maxG = maxG,
            maxAccel = maxAccel,
            maxBrake = maxBrake,
            pointCount = pointCount
        )

        TripStorage(this).saveTrip(trip)

        isRunning = false
        try {
            locationManager.removeUpdates(this)
        } catch (e: Exception) {}
        sensorManager.unregisterListener(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onLocationChanged(location: Location) {
        val speedKmh = location.speed * 3.6
        if (speedKmh > maxSpeedKmh) maxSpeedKmh = speedKmh
        avgSpeedSum += speedKmh
        speedCount++
        pointCount++

        lastLocation?.let { last ->
            val dist = last.distanceTo(location).toDouble()
            if (dist < 1000) distanceMeters += dist
        }
        lastLocation = location

        // Update notification
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(1, buildNotification("${String.format("%.1f km/h", speedKmh)} - ${String.format("%.1f km", distanceMeters/1000)}"))
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            lastAccelX = x
            lastAccelY = y
            lastAccelZ = z

            val total = sqrt((x*x + y*y + z*z).toDouble())
            val gForce = (total / GRAVITY_EARTH).toDouble()

            if (gForce > maxG) maxG = gForce

            // Longitudinal = Y axis (forward/backward) approx
            val longitudinal = y.toDouble()
            if (longitudinal > maxAccel) maxAccel = longitudinal
            if (-longitudinal > maxBrake) maxBrake = -longitudinal
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun buildNotification(content: String): Notification {
        return NotificationCompat.Builder(this, "trip_channel")
            .setContentTitle("SkodaDash Fahrt")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "trip_channel",
                "Fahrten",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

data class TripData(
    val id: Long,
    val startTime: Long,
    val endTime: Long?,
    val distanceMeters: Double,
    val durationSec: Long,
    val maxSpeedKmh: Double,
    val avgSpeedKmh: Double,
    val maxG: Double,
    val maxAccel: Double,
    val maxBrake: Double,
    val pointCount: Int
)
