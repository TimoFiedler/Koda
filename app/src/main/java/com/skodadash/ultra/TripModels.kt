package com.skodadash.ultra

data class TripPoint(
    val lat: Double,
    val lon: Double,
    val speedKmh: Double,
    val time: Long,
    val accuracy: Float = 0f,
    val altitude: Double = 0.0
)

data class TripEvent(
    val type: String, // ACCEL, BRAKE, CORNER, SPEED, HARD_BRAKE, HARD_ACCEL, SHARP_CORNER
    val time: Long,
    val value: Double,
    val lat: Double,
    val lon: Double,
    val speedKmh: Double = 0.0
)

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
    val pointCount: Int,
    val isAuto: Boolean = false,
    val points: List<TripPoint> = emptyList(),
    val events: List<TripEvent> = emptyList(),
    val score: Int = 0,
    val ecoScore: Int = 0,
    val sportScore: Int = 0,
    val efficiencyScore: Int = 0,
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val customName: String = ""
) {
    // Vollversion: Sport Score - mehr Punkte für hohe Geschwindigkeit, hohe G-Kräfte, wenig Bremsen
    fun calculateSportScore(): Int {
        var s = 0
        val distanceKm = distanceMeters / 1000.0
        val durationMin = durationSec / 60.0

        // Basis: Distanz und Dauer
        s += (distanceKm * 12).toInt()
        s += (durationMin * 1.5).toInt()

        // Geschwindigkeit - viel Geschwindigkeit = mehr Punkte
        when {
            maxSpeedKmh >= 200 -> s += 120
            maxSpeedKmh >= 180 -> s += 90
            maxSpeedKmh >= 160 -> s += 65
            maxSpeedKmh >= 140 -> s += 45
            maxSpeedKmh >= 120 -> s += 30
            maxSpeedKmh >= 100 -> s += 18
            maxSpeedKmh >= 80 -> s += 8
        }
        when {
            avgSpeedKmh >= 120 -> s += 60
            avgSpeedKmh >= 100 -> s += 40
            avgSpeedKmh >= 80 -> s += 25
            avgSpeedKmh >= 60 -> s += 12
            avgSpeedKmh >= 40 -> s += 5
        }

        // G-Kräfte - hohe G = mehr Punkte (dynamische Fahrt)
        when {
            maxG >= 1.3 -> s += 100
            maxG >= 1.1 -> s += 70
            maxG >= 0.9 -> s += 45
            maxG >= 0.7 -> s += 25
            maxG >= 0.5 -> s += 12
            maxG >= 0.3 -> s += 5
        }

        // Beschleunigung - starke Beschleunigung = sportlich
        when {
            maxAccel >= 5.0 -> s += 50
            maxAccel >= 4.0 -> s += 35
            maxAccel >= 3.0 -> s += 20
            maxAccel >= 2.0 -> s += 10
        }

        // Bremsen - WENIG bremsen = mehr Punkte (flüssig und schnell)
        val brakeEvents = events.count { it.type == "BRAKE" || it.type == "HARD_BRAKE" }
        val hardBrakeEvents = events.count { it.type == "HARD_BRAKE" }
        when {
            brakeEvents == 0 -> s += 60
            brakeEvents <= 2 -> s += 40
            brakeEvents <= 5 -> s += 20
            brakeEvents <= 10 -> s += 5
            brakeEvents > 20 -> s -= 20
        }
        // Harte Bremsungen abziehen, da wenig bremsen gewünscht
        s -= hardBrakeEvents * 3

        // Kurven - schnelle Kurven = mehr Punkte
        val cornerEvents = events.count { it.type == "CORNER" || it.type == "SHARP_CORNER" }
        s += cornerEvents * 8
        val sharpCorners = events.count { it.type == "SHARP_CORNER" }
        s += sharpCorners * 12

        // Beschleunigungs Events
        val accelEvents = events.count { it.type == "ACCEL" || it.type == "HARD_ACCEL" }
        s += accelEvents * 6

        // Speed Events - hohe Geschwindigkeit gehalten
        val speedEvents = events.count { it.type == "SPEED" }
        s += speedEvents * 4

        // Effizienz: wenig Stopps, gleichmäßige Fahrt trotz hoher Geschwindigkeit
        if (pointCount > 10) {
            val avgGPerKm = if (distanceKm > 0) events.size / distanceKm else 0.0
            if (avgGPerKm < 2.0) s += 25 // wenig Events pro km = flüssig schnell
        }

        // Auto Bonus
        if (isAuto) s += 8

        // Mindestens 0
        return s.coerceAtLeast(0)
    }

    fun calculateEcoScore(): Int {
        var eco = 100
        if (maxG > 0.8) eco -= 25
        else if (maxG > 0.6) eco -= 15
        else if (maxG > 0.4) eco -= 5

        if (maxAccel > 4.0) eco -= 20
        else if (maxAccel > 3.0) eco -= 12
        else if (maxAccel > 2.0) eco -= 5

        if (maxBrake > 4.0) eco -= 20
        else if (maxBrake > 3.0) eco -= 12
        else if (maxBrake > 2.0) eco -= 5

        if (maxSpeedKmh > 160) eco -= 25
        else if (maxSpeedKmh > 130) eco -= 15
        else if (maxSpeedKmh > 110) eco -= 5

        if (avgSpeedKmh > 110) eco -= 15

        val brakeCount = events.count { it.type == "BRAKE" || it.type == "HARD_BRAKE" }
        if (brakeCount > 15) eco -= 15
        else if (brakeCount > 8) eco -= 8

        return eco.coerceIn(0, 100)
    }

    fun calculateScore(): Int {
        // Neuer Score = Sport Score, da User mehr Punkte für Geschwindigkeit und G will
        return calculateSportScore()
    }

    fun calculateEfficiencyScore(): Int {
        // Wenig Bremsen + hohe Durchschnittsgeschwindigkeit = effizient sportlich
        var eff = 100
        val brakeEvents = events.count { it.type == "BRAKE" || it.type == "HARD_BRAKE" }
        val distanceKm = distanceMeters / 1000.0
        if (distanceKm > 0) {
            val brakesPerKm = brakeEvents / distanceKm
            when {
                brakesPerKm > 5 -> eff -= 40
                brakesPerKm > 3 -> eff -= 25
                brakesPerKm > 1.5 -> eff -= 12
                brakesPerKm < 0.5 -> eff += 10
            }
        }
        if (avgSpeedKmh in 60.0..110.0) eff += 10
        return eff.coerceIn(0, 100)
    }

    fun getLevel(): String {
        return when {
            score >= 1000 -> "Legende"
            score >= 600 -> "Profi"
            score >= 350 -> "Fortgeschritten"
            score >= 150 -> "Geuebt"
            else -> "Einsteiger"
        }
    }

    fun getDrivingStyle(): String {
        return when {
            maxG >= 1.0 && maxSpeedKmh >= 140 -> "Sehr sportlich"
            maxG >= 0.7 && maxSpeedKmh >= 100 -> "Sportlich"
            maxG >= 0.5 && avgSpeedKmh >= 60 -> "Dynamisch"
            maxG < 0.3 && events.size < 5 -> "Sehr sanft"
            else -> "Ausgewogen"
        }
    }
}
