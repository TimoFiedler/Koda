package com.skodadash.ultra

import java.text.SimpleDateFormat
import java.util.*

object StatsHelper {

    data class DailyStat(val date: String, val distanceKm: Double, val score: Int, val trips: Int)
    data class WeeklySummary(val totalKm: Double, val totalScore: Int, val avgSpeed: Double, val trips: Int, val bestTrip: TripData?)

    fun getLast7Days(trips: List<TripData>): List<DailyStat> {
        val sdf = SimpleDateFormat("dd.MM", Locale.GERMANY)
        val calendar = Calendar.getInstance()
        val result = mutableListOf<DailyStat>()

        for (i in 6 downTo 0) {
            calendar.time = Date()
            calendar.add(Calendar.DAY_OF_YEAR, -i)
            val startOfDay = calendar.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
            val endOfDay = calendar.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }.timeInMillis

            val dayTrips = trips.filter { it.startTime in startOfDay..endOfDay }
            val dist = dayTrips.sumOf { it.distanceMeters } / 1000.0
            val score = dayTrips.sumOf { it.score }

            result.add(DailyStat(sdf.format(Date(startOfDay)), dist, score, dayTrips.size))
        }
        return result
    }

    fun getWeeklySummary(trips: List<TripData>): WeeklySummary {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val weekAgo = calendar.timeInMillis
        val weekTrips = trips.filter { it.startTime >= weekAgo }

        return WeeklySummary(
            totalKm = weekTrips.sumOf { it.distanceMeters } / 1000.0,
            totalScore = weekTrips.sumOf { it.score },
            avgSpeed = if (weekTrips.isNotEmpty()) weekTrips.map { it.avgSpeedKmh }.average() else 0.0,
            trips = weekTrips.size,
            bestTrip = weekTrips.maxByOrNull { it.score }
        )
    }

    fun getDrivingBehavior(trip: TripData): Map<String, String> {
        val brakeCount = trip.events.count { it.type.contains("BRAKE") }
        val accelCount = trip.events.count { it.type.contains("ACCEL") }
        val cornerCount = trip.events.count { it.type.contains("CORNER") }
        val distanceKm = trip.distanceMeters / 1000.0

        val brakesPerKm = if (distanceKm > 0) brakeCount / distanceKm else 0.0
        val accelsPerKm = if (distanceKm > 0) accelCount / distanceKm else 0.0

        return mapOf(
            "Bremsen" to when {
                brakesPerKm == 0.0 -> "Perfekt - 0/km"
                brakesPerKm < 0.5 -> "Sehr gut - ${String.format("%.1f", brakesPerKm)}/km"
                brakesPerKm < 1.5 -> "Gut - ${String.format("%.1f", brakesPerKm)}/km"
                brakesPerKm < 3.0 -> "Normal - ${String.format("%.1f", brakesPerKm)}/km"
                else -> "Häufig - ${String.format("%.1f", brakesPerKm)}/km"
            },
            "Beschleunigung" to when {
                accelsPerKm < 0.5 -> "Sanft - ${String.format("%.1f", accelsPerKm)}/km"
                accelsPerKm < 1.5 -> "Dynamisch - ${String.format("%.1f", accelsPerKm)}/km"
                accelsPerKm < 3.0 -> "Sportlich - ${String.format("%.1f", accelsPerKm)}/km"
                else -> "Sehr sportlich - ${String.format("%.1f", accelsPerKm)}/km"
            },
            "Kurven" to when {
                cornerCount == 0 -> "Gerade Strecke"
                cornerCount < 5 -> "Wenig Kurven - $cornerCount"
                cornerCount < 15 -> "Kurvig - $cornerCount"
                else -> "Sehr kurvig - $cornerCount"
            },
            "G-Kraft" to when {
                trip.maxG < 0.4 -> "Sehr sanft - ${String.format("%.2f", trip.maxG)} G"
                trip.maxG < 0.7 -> "Normal - ${String.format("%.2f", trip.maxG)} G"
                trip.maxG < 1.0 -> "Dynamisch - ${String.format("%.2f", trip.maxG)} G"
                else -> "Extrem - ${String.format("%.2f", trip.maxG)} G"
            }
        )
    }

    fun suggestTripName(trip: TripData): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = trip.startTime }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        val timeName = when (hour) {
            in 5..9 -> "Morgen Fahrt"
            in 10..11 -> "Vormittag Tour"
            in 12..13 -> "Mittag Fahrt"
            in 14..17 -> "Nachmittag Tour"
            in 18..20 -> "Abend Fahrt"
            in 21..23 -> "Nacht Fahrt"
            else -> "Früh Fahrt"
        }

        val distance = trip.distanceMeters / 1000.0
        val distName = when {
            distance < 5 -> "Kurzstrecke"
            distance < 20 -> "Stadt Fahrt"
            distance < 50 -> "Überland"
            distance < 150 -> "Langstrecke"
            else -> "Roadtrip"
        }

        val style = trip.getDrivingStyle()

        return if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
            "Wochenende $distName"
        } else {
            "$timeName - $distName"
        }
    }
}
