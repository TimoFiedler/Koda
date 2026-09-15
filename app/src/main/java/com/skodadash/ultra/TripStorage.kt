package com.skodadash.ultra

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class TripStorage(private val context: Context) {

    private val prefs = context.getSharedPreferences("trips", Context.MODE_PRIVATE)

    fun saveTrip(trip: TripData) {
        val trips = getTrips().toMutableList()
        trips.add(trip)
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
                                accuracy = po.optDouble("accuracy", 0.0).toFloat()
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
                                lon = eo.getDouble("lon")
                            )
                        )
                    }
                }
                list.add(
                    TripData(
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
                        ecoScore = o.optInt("ecoScore", 0)
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getTripById(id: Long): TripData? {
        return getTrips().find { it.id == id }
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    fun getTotalScore(): Int {
        return getTrips().sumOf { it.score }
    }

    fun getTotalDistance(): Double {
        return getTrips().sumOf { it.distanceMeters }
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
            
            val pa = JSONArray()
            trip.points.takeLast(500).forEach { p ->
                val po = JSONObject()
                po.put("lat", p.lat)
                po.put("lon", p.lon)
                po.put("speedKmh", p.speedKmh)
                po.put("time", p.time)
                po.put("accuracy", p.accuracy)
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
                ea.put(eo)
            }
            o.put("events", ea)
            arr.put(o)
        }
        prefs.edit().putString("trips_json", arr.toString()).apply()
    }
}
