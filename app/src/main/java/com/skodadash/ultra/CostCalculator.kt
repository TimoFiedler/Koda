package com.skodadash.ultra

object CostCalculator {

    // Durchschnittliche Verbräuche und Preise (konfigurierbar)
    private const val FUEL_PRICE_PER_L = 1.85 // Euro
    private const val ELECTRIC_PRICE_PER_KWH = 0.35 // Euro
    private const val AVG_FUEL_CONSUMPTION = 7.5 // L/100km
    private const val AVG_ELECTRIC_CONSUMPTION = 18.0 // kWh/100km
    private const val AVG_HYBRID_CONSUMPTION_FUEL = 5.5 // L/100km
    private const val AVG_HYBRID_CONSUMPTION_ELECTRIC = 10.0 // kWh/100km

    data class CostResult(
        val fuelLiters: Double,
        val kwh: Double,
        val costEuro: Double,
        val co2Kg: Double,
        val efficiency: String
    )

    fun calculate(trip: TripData, engineType: String): CostResult {
        val km = trip.distanceMeters / 1000.0
        if (km <= 0) return CostResult(0.0, 0.0, 0.0, 0.0, "Keine Distanz")

        return when (engineType) {
            "combustion" -> {
                // Verbrauch abhängig von Fahrstil
                val factor = when {
                    trip.avgSpeedKmh > 120 -> 1.3
                    trip.avgSpeedKmh > 100 -> 1.15
                    trip.avgSpeedKmh > 80 -> 1.0
                    trip.avgSpeedKmh > 50 -> 0.9
                    else -> 1.1
                } * when {
                    trip.maxG > 0.8 -> 1.2
                    trip.maxG > 0.5 -> 1.1
                    else -> 1.0
                }
                val liters = km * AVG_FUEL_CONSUMPTION / 100.0 * factor
                val cost = liters * FUEL_PRICE_PER_L
                val co2 = liters * 2.31 // kg CO2 pro Liter Benzin
                val eff = when {
                    factor < 0.95 -> "Sehr effizient"
                    factor < 1.05 -> "Effizient"
                    factor < 1.2 -> "Normal"
                    else -> "Hoher Verbrauch"
                }
                CostResult(liters, 0.0, cost, co2, eff)
            }
            "electric" -> {
                val factor = when {
                    trip.avgSpeedKmh > 120 -> 1.4
                    trip.avgSpeedKmh > 100 -> 1.2
                    trip.avgSpeedKmh > 80 -> 1.0
                    trip.avgSpeedKmh > 50 -> 0.85
                    else -> 0.95
                } * when {
                    trip.maxG > 0.8 -> 1.25
                    trip.maxG > 0.5 -> 1.1
                    else -> 1.0
                }
                val kwh = km * AVG_ELECTRIC_CONSUMPTION / 100.0 * factor
                val cost = kwh * ELECTRIC_PRICE_PER_KWH
                val co2 = kwh * 0.4 // kg CO2 pro kWh (Strommix)
                val eff = when {
                    kwh / km * 100 < 15 -> "Sehr effizient"
                    kwh / km * 100 < 19 -> "Effizient"
                    kwh / km * 100 < 24 -> "Normal"
                    else -> "Hoher Verbrauch"
                }
                CostResult(0.0, kwh, cost, co2, eff)
            }
            else -> { // hybrid
                val fuelLiters = km * AVG_HYBRID_CONSUMPTION_FUEL / 100.0 * 0.9
                val kwh = km * AVG_HYBRID_CONSUMPTION_ELECTRIC / 100.0 * 0.9
                val cost = fuelLiters * FUEL_PRICE_PER_L + kwh * ELECTRIC_PRICE_PER_KWH
                val co2 = fuelLiters * 2.31 + kwh * 0.4
                CostResult(fuelLiters, kwh, cost, co2, "Hybrid Mix")
            }
        }
    }

    fun calculateTotal(trips: List<TripData>, engineType: String): CostResult {
        var totalFuel = 0.0
        var totalKwh = 0.0
        var totalCost = 0.0
        var totalCo2 = 0.0
        trips.forEach {
            val r = calculate(it, engineType)
            totalFuel += r.fuelLiters
            totalKwh += r.kwh
            totalCost += r.costEuro
            totalCo2 += r.co2Kg
        }
        return CostResult(totalFuel, totalKwh, totalCost, totalCo2, "Gesamt")
    }
}
