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
                list.add(
                    TripData(
                        id = o.getLong("id"),
                        startTime = o.getLong("startTime"),
                        endTime = if (o.has("endTime")) o.getLong("endTime") else null,
                        distanceMeters = o.getDouble("distanceMeters"),
                        durationSec = o.getLong("durationSec"),
                        maxSpeedKmh = o.getDouble("maxSpeedKmh"),
                        avgSpeedKmh = o.getDouble("avgSpeedKmh"),
                        maxG = o.getDouble("maxG"),
                        maxAccel = o.getDouble("maxAccel"),
                        maxBrake = o.getDouble("maxBrake"),
                        pointCount = o.getInt("pointCount")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearAll() {
        prefs.edit().clear().apply()
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
            arr.put(o)
        }
        prefs.edit().putString("trips_json", arr.toString()).apply()
    }
}
