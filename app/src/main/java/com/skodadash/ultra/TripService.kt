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
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlin.math.sqrt
import kotlin.math.abs

class TripService : Service(), SensorEventListener, LocationListener {

    companion object {
        const val ACTION_START = "START"
        const val ACTION_START_AUTO = "START_AUTO"
        const val ACTION_STOP = "STOP"
        var isRunning = false
        var isAutoStarted = false
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
    private val tripPoints = mutableListOf<TripPoint>()
    private val tripEvents = mutableListOf<TripEvent>()
    private var lastEventTime = 0L

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
            ACTION_START -> startTrip(false)
            ACTION_START_AUTO -> startTrip(true)
            ACTION_STOP -> stopTrip()
        }
        return START_STICKY
    }

    private fun startTrip(auto: Boolean) {
        if (isRunning) return
        isRunning = true
        isAutoStarted = auto
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
        tripPoints.clear()
        tripEvents.clear()
        lastEventTime = 0L

        val notification = buildNotification(if (auto) "Auto Fahrt erkannt - Tracking aktiv" else "Manuelle Fahrt - Tracking aktiv")
        startForeground(1, notification)

        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 800L, 0.5f, this)
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1500L, 3f, this)
        } catch (e: SecurityException) { e.printStackTrace() }

        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    private fun stopTrip() {
        if (!isRunning) { stopSelf(); return }

        val endTime = System.currentTimeMillis()
        val durationSec = (endTime - startTime) / 1000

        val tempTrip = TripData(
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
            pointCount = pointCount,
            isAuto = isAutoStarted,
            points = tripPoints.toList(),
            events = tripEvents.toList()
        )

        val trip = tempTrip.copy(
            sportScore = tempTrip.calculateSportScore(),
            score = tempTrip.calculateSportScore(),
            ecoScore = tempTrip.calculateEcoScore(),
            efficiencyScore = tempTrip.calculateEfficiencyScore()
        )

        TripStorage(this).saveTrip(trip)

        isRunning = false
        isAutoStarted = false
        try { locationManager.removeUpdates(this) } catch (e: Exception) {}
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

        val point = TripPoint(
            lat = location.latitude,
            lon = location.longitude,
            speedKmh = speedKmh,
            time = System.currentTimeMillis(),
            accuracy = location.accuracy,
            altitude = location.altitude
        )
        tripPoints.add(point)
        if (tripPoints.size > 3000) tripPoints.removeAt(0)

        lastLocation?.let { last ->
            val dist = last.distanceTo(location).toDouble()
            if (dist < 500 && dist > 0.5) distanceMeters += dist
        }
        lastLocation = location

        // Speed event - high speed
        val now = System.currentTimeMillis()
        if (now - lastEventTime > 3000) {
            if (speedKmh > 120) {
                tripEvents.add(TripEvent("SPEED", now, speedKmh, location.latitude, location.longitude, speedKmh))
                lastEventTime = now
            }
        }

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(1, buildNotification("${String.format("%.0f km/h", speedKmh)} - ${String.format("%.1f km", distanceMeters/1000)} - ${tripEvents.size} Events"))
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val total = sqrt((x*x + y*y + z*z).toDouble())
            val gForce = total / GRAVITY_EARTH
            if (gForce > maxG) maxG = gForce

            val longitudinal = y.toDouble()
            val lateral = x.toDouble()
            val now = System.currentTimeMillis()

            // Throttle events to 1.5s
            if (now - lastEventTime < 1500) return
            val loc = lastLocation ?: return
            if (loc.speed * 3.6 < 8) return // ignore when standing

            when {
                longitudinal > 3.5 -> {
                    if (longitudinal > maxAccel) maxAccel = longitudinal
                    val type = if (longitudinal > 4.5) "HARD_ACCEL" else "ACCEL"
                    tripEvents.add(TripEvent(type, now, longitudinal, loc.latitude, loc.longitude, loc.speed*3.6))
                    lastEventTime = now
                }
                longitudinal < -3.5 -> {
                    val brakeVal = -longitudinal
                    if (brakeVal > maxBrake) maxBrake = brakeVal
                    val type = if (brakeVal > 4.5) "HARD_BRAKE" else "BRAKE"
                    tripEvents.add(TripEvent(type, now, brakeVal, loc.latitude, loc.longitude, loc.speed*3.6))
                    lastEventTime = now
                }
                abs(lateral) > 3.0 -> {
                    val type = if (abs(lateral) > 4.5) "SHARP_CORNER" else "CORNER"
                    tripEvents.add(TripEvent(type, now, abs(lateral), loc.latitude, loc.longitude, loc.speed*3.6))
                    lastEventTime = now
                }
                gForce > 0.8 -> {
                    // High G without specific direction - count as corner
                    if (abs(lateral) > 2.0) {
                        tripEvents.add(TripEvent("CORNER", now, gForce, loc.latitude, loc.longitude, loc.speed*3.6))
                        lastEventTime = now
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun buildNotification(content: String): Notification {
        return NotificationCompat.Builder(this, "trip_channel")
            .setContentTitle("Fahrten Tracker - Vollversion")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("trip_channel", "Fahrten Tracker", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
