package com.skodadash.ultra

import android.content.Context
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
    val lastUpdated: String,
    val rawJson: String
)

class SkodaApi(private val settings: SettingsRepository, private val context: Context? = null) {

    suspend fun fetchVehicle(): VehicleData? = withContext(Dispatchers.IO) {
        try {
            val apiKey = settings.getApiKey()
            val vin = settings.getVin()
            if (apiKey.isEmpty() || vin.isEmpty()) return@withContext null

            // Rate limit check
            context?.let { ctx ->
                val limiter = RateLimiter(ctx)
                if (!limiter.canMakeRequest()) {
                    throw Exception("Rate Limit erreicht: ${limiter.getRemainingRequests()} übrig. ${limiter.getStatusText()}. Bitte warten.")
                }
            }

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
                if (code == 429) {
                    throw Exception("Rate Limit 429: Zu viele Anfragen. Max 20 pro Stunde. Bitte warten.")
                }
                throw Exception("API Fehler $code: $err - Key abgelaufen? In MySkoda App neuen Key erstellen!")
            }

            context?.let { RateLimiter(it).recordRequest() }

            val text = conn.inputStream.bufferedReader().readText()
            val root = JSONObject(text)
            val vehicle = root.optJSONObject("vehicle") ?: root

            val name = vehicle.optString("name", vin).ifEmpty { vin }

            var doorsLocked: Boolean? = null
            vehicle.optJSONObject("status")?.optJSONObject("overall")?.let { overall ->
                val dl = overall.optString("doorsLocked", "")
                val locked = overall.optString("locked", "")
                val reliable = overall.optString("reliableLockStatus", "")
                doorsLocked = when {
                    dl.equals("YES", true) || dl.equals("LOCKED", true) -> true
                    dl.equals("NO", true) || dl.equals("UNLOCKED", true) || dl.contains("OPEN", true) -> false
                    locked.equals("YES", true) || reliable.equals("LOCKED", true) -> true
                    locked.equals("NO", true) || reliable.equals("UNLOCKED", true) -> false
                    else -> null
                }
            }

            var odometerKm: Double? = null
            vehicle.optJSONObject("odometer")?.let { odo ->
                if (odo.has("mileageInKm")) odometerKm = odo.optDouble("mileageInKm")
            }

            var batteryPercent: Double? = null
            var rangeKm: Double? = null
            var chargingState: String? = null
            var chargingPowerKw: Double? = null

            vehicle.optJSONObject("charging")?.let { charging ->
                val status = charging.optJSONObject("status")
                status?.optJSONObject("battery")?.let { bat ->
                    if (bat.has("stateOfChargeInPercent")) batteryPercent = bat.optDouble("stateOfChargeInPercent")
                    if (bat.has("remainingCruisingRangeInMeters")) {
                        rangeKm = bat.optDouble("remainingCruisingRangeInMeters") / 1000.0
                    }
                }
                status?.let { s ->
                    if (s.has("state")) chargingState = s.optString("state")
                    if (s.has("chargePowerInKw")) chargingPowerKw = s.optDouble("chargePowerInKw")
                    if (rangeKm == null && s.has("remainingCruisingRangeInMeters")) {
                        rangeKm = s.optDouble("remainingCruisingRangeInMeters") / 1000.0
                    }
                }
            }

            if (rangeKm == null || batteryPercent == null) {
                vehicle.optJSONObject("fuelStatus")?.let { fuel ->
                    if (fuel.has("totalRangeInKm")) {
                        if (rangeKm == null) rangeKm = fuel.optDouble("totalRangeInKm")
                    }
                    fuel.optJSONObject("primaryEngineRange")?.let { primary ->
                        if (rangeKm == null && primary.has("remainingRangeInKm")) {
                            rangeKm = primary.optDouble("remainingRangeInKm")
                        }
                        if (batteryPercent == null && primary.has("currentSoCInPercent")) {
                            batteryPercent = primary.optDouble("currentSoCInPercent")
                        }
                        if (batteryPercent == null && primary.has("currentFuelLevelInPercent")) {
                            batteryPercent = primary.optDouble("currentFuelLevelInPercent")
                        }
                    }
                    fuel.optJSONObject("secondaryEngineRange")?.let { secondary ->
                        if (batteryPercent == null && secondary.has("currentSoCInPercent")) {
                            batteryPercent = secondary.optDouble("currentSoCInPercent")
                        }
                    }
                }
            }

            if (rangeKm == null) {
                vehicle.optJSONObject("fuelStatus")?.optDouble("totalRangeInKm")?.let { rangeKm = it }
            }

            VehicleData(
                name = name,
                batteryPercent = batteryPercent,
                rangeKm = rangeKm,
                odometerKm = odometerKm,
                doorsLocked = doorsLocked,
                chargingState = chargingState,
                chargingPowerKw = chargingPowerKw,
                lastUpdated = java.text.SimpleDateFormat("dd.MM HH:mm:ss", java.util.Locale.GERMANY).format(java.util.Date()),
                rawJson = text.take(2000)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}
