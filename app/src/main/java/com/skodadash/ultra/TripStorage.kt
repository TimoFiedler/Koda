package com.skodadash.ultra

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class TripStorage(private val context: Context) {

    private val prefs = context.getSharedPreferences("trips", Context.MODE_PRIVATE)

    fun saveTrip(trip: TripData) {
        val trips = getTrips().toMutableList()
        trips.add(trip)
        // Keep last 100 trips to avoid huge storage
        if (trips.size > 100) {
            trips.removeAt(0)
        }
        saveAll(trips)
    }

    fun getTrips(): List<TripData> {
        val json = prefs.getString("trips_json", "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<TripData>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val points = mutableListOf<TripPoint>()
                if (o.has("points")) {
                    val pa = o.getJSONArray("points")
                    for (j in 0 until pa.length()) {
                        val po = pa.getJSONObject(j)
                        points.add(
                            TripPoint(
                                lat = po.getDouble("lat"),
                                lon = po.getDouble("lon"),
                                speedKmh = po.getDouble("speedKmh"),
                                time = po.getLong("time"),
                                accuracy = po.optDouble("accuracy", 0.0).toFloat(),
                                altitude = po.optDouble("altitude", 0.0)
                            )
                        )
                    }
                }
                val events = mutableListOf<TripEvent>()
                if (o.has("events")) {
                    val ea = o.getJSONArray("events")
                    for (j in 0 until ea.length()) {
                        val eo = ea.getJSONObject(j)
                        events.add(
                            TripEvent(
                                type = eo.getString("type"),
                                time = eo.getLong("time"),
                                value = eo.getDouble("value"),
                                lat = eo.getDouble("lat"),
                                lon = eo.getDouble("lon"),
                                speedKmh = eo.optDouble("speedKmh", 0.0)
                            )
                        )
                    }
                }
                val base = TripData(
                    id = o.getLong("id"),
                    startTime = o.getLong("startTime"),
                    endTime = if (o.has("endTime") && !o.isNull("endTime")) o.getLong("endTime") else null,
                    distanceMeters = o.getDouble("distanceMeters"),
                    durationSec = o.getLong("durationSec"),
                    maxSpeedKmh = o.getDouble("maxSpeedKmh"),
                    avgSpeedKmh = o.getDouble("avgSpeedKmh"),
                    maxG = o.getDouble("maxG"),
                    maxAccel = o.getDouble("maxAccel"),
                    maxBrake = o.getDouble("maxBrake"),
                    pointCount = o.getInt("pointCount"),
                    isAuto = o.optBoolean("isAuto", false),
                    points = points,
                    events = events,
                    score = o.optInt("score", 0),
                    ecoScore = o.optInt("ecoScore", 0),
                    sportScore = o.optInt("sportScore", o.optInt("score", 0)),
                    efficiencyScore = o.optInt("efficiencyScore", 0)
                )
                // Recalculate if old data has 0 score
                val recalculated = if (base.score == 0 && base.distanceMeters > 0) {
                    base.copy(
                        sportScore = base.calculateSportScore(),
                        score = base.calculateSportScore(),
                        ecoScore = base.calculateEcoScore(),
                        efficiencyScore = base.calculateEfficiencyScore()
                    )
                } else base
                list.add(recalculated)
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getTripById(id: Long): TripData? = getTrips().find { it.id == id }

    fun deleteTrip(id: Long) {
        val filtered = getTrips().filter { it.id != id }
        saveAll(filtered)
    }

    fun clearAll() { prefs.edit().clear().apply() }

    fun getTotalScore(): Int = getTrips().sumOf { it.score }
    fun getTotalDistance(): Double = getTrips().sumOf { it.distanceMeters }
    fun getTotalDuration(): Long = getTrips().sumOf { it.durationSec }
    fun getAverageSpeed(): Double {
        val trips = getTrips()
        return if (trips.isEmpty()) 0.0 else trips.map { it.avgSpeedKmh }.average()
    }
    fun getBestTrip(): TripData? = getTrips().maxByOrNull { it.score }
    fun getLevel(): String {
        val total = getTotalScore()
        return when {
            total >= 10000 -> "Legende"
            total >= 5000 -> "Meister"
            total >= 2500 -> "Profi"
            total >= 1000 -> "Fortgeschritten"
            total >= 300 -> "Geuebt"
            else -> "Einsteiger"
        }
    }
    fun getLevelProgress(): Int {
        val total = getTotalScore()
        return when {
            total >= 10000 -> 100
            total >= 5000 -> ((total - 5000) / 50).coerceIn(0,100)
            total >= 2500 -> ((total - 2500) / 25).coerceIn(0,100)
            total >= 1000 -> ((total - 1000) / 15).coerceIn(0,100)
            total >= 300 -> ((total - 300) / 7).coerceIn(0,100)
            else -> (total / 3).coerceIn(0,100)
        }
    }

    private fun saveAll(trips: List<TripData>) {
        val arr = JSONArray()
        trips.forEach { trip ->
            val o = JSONObject()
            o.put("id", trip.id)
            o.put("startTime", trip.startTime)
            o.put("endTime", trip.endTime)
            o.put("distanceMeters", trip.distanceMeters)
            o.put("durationSec", trip.durationSec)
            o.put("maxSpeedKmh", trip.maxSpeedKmh)
            o.put("avgSpeedKmh", trip.avgSpeedKmh)
            o.put("maxG", trip.maxG)
            o.put("maxAccel", trip.maxAccel)
            o.put("maxBrake", trip.maxBrake)
            o.put("pointCount", trip.pointCount)
            o.put("isAuto", trip.isAuto)
            o.put("score", trip.score)
            o.put("ecoScore", trip.ecoScore)
            o.put("sportScore", trip.sportScore)
            o.put("efficiencyScore", trip.efficiencyScore)

            val pa = JSONArray()
            trip.points.takeLast(500).forEach { p ->
                val po = JSONObject()
                po.put("lat", p.lat)
                po.put("lon", p.lon)
                po.put("speedKmh", p.speedKmh)
                po.put("time", p.time)
                po.put("accuracy", p.accuracy)
                po.put("altitude", p.altitude)
                pa.put(po)
            }
            o.put("points", pa)

            val ea = JSONArray()
            trip.events.forEach { e ->
                val eo = JSONObject()
                eo.put("type", e.type)
                eo.put("time", e.time)
                eo.put("value", e.value)
                eo.put("lat", e.lat)
                eo.put("lon", e.lon)
                eo.put("speedKmh", e.speedKmh)
                ea.put(eo)
            }
            o.put("events", ea)
            arr.put(o)
        }
        prefs.edit().putString("trips_json", arr.toString()).apply()
    }
}
