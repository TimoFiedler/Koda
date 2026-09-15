package com.skodadash.ultra

data class TripPoint(
    val lat: Double,
    val lon: Double,
    val speedKmh: Double,
    val time: Long,
    val accuracy: Float = 0f
)

data class TripEvent(
    val type: String, // ACCEL, BRAKE, CORNER, SPEED
    val time: Long,
    val value: Double,
    val lat: Double,
    val lon: Double
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
    val ecoScore: Int = 0
) {
    fun calculateScore(): Int {
        var s = 0
        // Distance points
        s += (distanceMeters / 1000 * 10).toInt()
        // Duration points
        s += (durationSec / 60).toInt()
        // Eco bonus - low max G
        if (maxG < 0.3) s += 20
        else if (maxG < 0.5) s += 10
        // Speed bonus - not too fast
        if (maxSpeedKmh in 50.0..130.0) s += 15
        // Smooth driving
        if (maxAccel < 2.0 && maxBrake < 2.0) s += 25
        // Auto bonus
        if (isAuto) s += 5
        return s.coerceAtLeast(0)
    }

    fun calculateEcoScore(): Int {
        var eco = 100
        if (maxG > 0.6) eco -= 20
        if (maxG > 0.8) eco -= 20
        if (maxAccel > 3.0) eco -= 15
        if (maxBrake > 3.0) eco -= 15
        if (maxSpeedKmh > 130) eco -= 20
        if (avgSpeedKmh > 100) eco -= 10
        return eco.coerceIn(0, 100)
    }
}
