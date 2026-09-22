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
import kotlin.math.abs
import kotlin.math.sqrt

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
    private var speedSum = 0.0
    private var speedCount = 0
    private var movingSpeedSum = 0.0
    private var movingCount = 0
    private var maxG = 0.0
    private var maxAccel = 0.0
    private var maxBrake = 0.0
    private var pointCount = 0
    private var startTime = 0L
    private var warmupPoints = 0

    private val tripPoints = mutableListOf<TripPoint>()
    private val tripEvents = mutableListOf<TripEvent>()

    private var lastEventTime = 0L
    private var filteredAccelX = 0f
    private var filteredAccelY = 0f
    private var filteredAccelZ = 0f
    private val alpha = 0.8f // low-pass filter

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
        speedSum = 0.0
        speedCount = 0
        movingSpeedSum = 0.0
        movingCount = 0
        maxG = 0.0
        maxAccel = 0.0
        maxBrake = 0.0
        pointCount = 0
        warmupPoints = 0
        lastLocation = null
        tripPoints.clear()
        tripEvents.clear()
        lastEventTime = 0L
        filteredAccelX = 0f
        filteredAccelY = 0f
        filteredAccelZ = 0f

        val notification = buildNotification(if (auto) "Auto Fahrt - Tracking" else "Fahrt - Tracking")
        startForeground(1, notification)

        try {
            // Nur GPS für genaue Daten
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 2f, this)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun stopTrip() {
        if (!isRunning) { stopSelf(); return }

        val endTime = System.currentTimeMillis()
        val durationSec = (endTime - startTime) / 1000

        // Korrekte Durchschnittsgeschwindigkeit: Distanz / Zeit wenn bewegt, sonst Mittel der Speeds
        val avgSpeed = if (durationSec > 0 && distanceMeters > 100) {
            (distanceMeters / 1000.0) / (durationSec / 3600.0) // km/h aus Distanz/Zeit
        } else if (movingCount > 0) {
            movingSpeedSum / movingCount
        } else if (speedCount > 0) {
            speedSum / speedCount
        } else 0.0

        val tempTrip = TripData(
            id = tripId,
            startTime = startTime,
            endTime = endTime,
            distanceMeters = distanceMeters,
            durationSec = durationSec,
            maxSpeedKmh = maxSpeedKmh,
            avgSpeedKmh = avgSpeed,
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
        try { locationManager.removeUpdates(this) } catch (_: Exception) {}
        sensorManager.unregisterListener(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onLocationChanged(location: Location) {
        // Filter: Genauigkeit
        if (location.accuracy > 30f) return
        if (location.latitude == 0.0 && location.longitude == 0.0) return

        // Warmup: erste 3 Punkte ignorieren für Distanz (GPS springt)
        warmupPoints++
        if (warmupPoints <= 3) {
            lastLocation = location
            return
        }

        val speedKmh = if (location.hasSpeed()) location.speed * 3.6 else 0.0
        val validSpeed = speedKmh >= 0 && speedKmh < 300 // plausibel

        if (validSpeed) {
            if (speedKmh > maxSpeedKmh) maxSpeedKmh = speedKmh
            speedSum += speedKmh
            speedCount++
            if (speedKmh > 2.0) {
                movingSpeedSum += speedKmh
                movingCount++
            }
        }

        pointCount++

        val point = TripPoint(
            lat = location.latitude,
            lon = location.longitude,
            speedKmh = speedKmh,
            time = System.currentTimeMillis(),
            accuracy = location.accuracy,
            altitude = if (location.hasAltitude()) location.altitude else 0.0
        )
        tripPoints.add(point)
        if (tripPoints.size > 2000) tripPoints.removeAt(0)

        // Distanz nur wenn genau und Bewegung plausibel
        lastLocation?.let { last ->
            val dist = last.distanceTo(location).toDouble()
            // Ignoriere Sprünge >200m (GPS Fehler) und <1m (Rauschen)
            if (dist in 1.0..200.0 && location.accuracy < 20f && last.accuracy < 20f) {
                // Nur wenn beide Punkte nicht zu alt
                distanceMeters += dist
            }
        }
        lastLocation = location

        // Speed Event nur bei hoher Geschwindigkeit, gedrosselt
        val now = System.currentTimeMillis()
        if (now - lastEventTime > 4000 && speedKmh > 120) {
            tripEvents.add(TripEvent("SPEED", now, speedKmh, location.latitude, location.longitude, speedKmh))
            lastEventTime = now
        }

        // Notification minimal
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(1, buildNotification("${String.format("%.0f km/h", speedKmh)} - ${String.format("%.1f km", distanceMeters/1000)}"))
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_LINEAR_ACCELERATION) return
        val loc = lastLocation ?: return
        if (loc.speed * 3.6 < 10) return // nur ab 10 km/h

        // Low-pass Filter gegen Rauschen
        filteredAccelX = alpha * filteredAccelX + (1 - alpha) * event.values[0]
        filteredAccelY = alpha * filteredAccelY + (1 - alpha) * event.values[1]
        filteredAccelZ = alpha * filteredAccelZ + (1 - alpha) * event.values[2]

        val x = filteredAccelX
        val y = filteredAccelY
        val z = filteredAccelZ

        val total = sqrt((x*x + y*y + z*z).toDouble())
        val gForce = total / 9.81
        if (gForce > maxG) maxG = gForce

        val now = System.currentTimeMillis()
        if (now - lastEventTime < 2000) return // 2s Drossel

        val longitudinal = y.toDouble() // vor/zurück
        val lateral = x.toDouble() // seitlich

        when {
            longitudinal > 1.8 -> {
                if (longitudinal > maxAccel) maxAccel = longitudinal
                val type = if (longitudinal > 3.5) "HARD_ACCEL" else "ACCEL"
                tripEvents.add(TripEvent(type, now, longitudinal, loc.latitude, loc.longitude, loc.speed*3.6))
                lastEventTime = now
            }
            longitudinal < -1.8 -> {
                val brakeVal = -longitudinal
                if (brakeVal > maxBrake) maxBrake = brakeVal
                val type = if (brakeVal > 3.5) "HARD_BRAKE" else "BRAKE"
                tripEvents.add(TripEvent(type, now, brakeVal, loc.latitude, loc.longitude, loc.speed*3.6))
                lastEventTime = now
            }
            abs(lateral) > 1.5 -> {
                val type = if (abs(lateral) > 3.2) "SHARP_CORNER" else "CORNER"
                // Store lateral sign in value for left/right detection: negative = left, positive = right
                tripEvents.add(TripEvent(type, now, lateral, loc.latitude, loc.longitude, loc.speed*3.6))
                lastEventTime = now
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun buildNotification(content: String): Notification {
        return NotificationCompat.Builder(this, "trip_channel")
            .setContentTitle("Fahrten Tracker")
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
