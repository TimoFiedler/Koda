package com.skodadash.ultra

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class VehicleData(
    val name: String,
    val batteryPercent: Double?,
    val rangeKm: Double?,
    val odometerKm: Double?,
    val doorsLocked: Boolean?,
    val chargingState: String?,
    val chargingPowerKw: Double?,
    val lastUpdated: String
)

class SkodaApi(private val settings: SettingsRepository) {

    suspend fun fetchVehicle(): VehicleData? = withContext(Dispatchers.IO) {
        try {
            val apiKey = settings.getApiKey()
            val vin = settings.getVin()
            if (apiKey.isEmpty() || vin.isEmpty()) return@withContext null

            val url = URL("https://public.api.connect.skoda-auto.cz/api/v1/vehicles/$vin")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("X-API-Key", apiKey)
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            val code = conn.responseCode
            if (code != 200) {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
                throw Exception("API Fehler $code: $err")
            }

            val text = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(text)

            // Parse - API structure may vary, try multiple paths
            val name = json.optString("name", json.optString("model", vin))
            val battery = json.optJSONObject("battery") ?: json.optJSONObject("electric")
            val batteryPercent = battery?.optDouble("stateOfChargeInPercent") ?: battery?.optDouble("batteryLevelInPercent")
            ?: json.optDouble("batteryPercent", Double.NaN).takeIf { !it.isNaN() }

            val range = json.optJSONObject("range") ?: json
            val rangeKm = range.optDouble("electricRangeInKm", Double.NaN).takeIf { !it.isNaN() }
                ?: range.optDouble("rangeInKm", Double.NaN).takeIf { !it.isNaN() }
                ?: json.optDouble("rangeKm", Double.NaN).takeIf { !it.isNaN() }

            val odo = json.optJSONObject("odometer") ?: json
            val odometerKm = odo.optDouble("odometerInKm", Double.NaN).takeIf { !it.isNaN() }
                ?: odo.optDouble("mileageInKm", Double.NaN).takeIf { !it.isNaN() }

            val doors = json.optJSONObject("doors") ?: json.optJSONObject("lock")
            val locked = doors?.optBoolean("locked", false)

            val charging = json.optJSONObject("charging") ?: battery
            val chargingState = charging?.optString("state") ?: charging?.optString("chargingState")
            val chargingPower = charging?.optDouble("chargingPowerInKw")

            VehicleData(
                name = name,
                batteryPercent = batteryPercent,
                rangeKm = rangeKm,
                odometerKm = odometerKm,
                doorsLocked = locked,
                chargingState = chargingState,
                chargingPowerKw = chargingPower,
                lastUpdated = java.text.SimpleDateFormat("dd.MM HH:mm:ss", java.util.Locale.GERMANY).format(java.util.Date())
            )
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}
