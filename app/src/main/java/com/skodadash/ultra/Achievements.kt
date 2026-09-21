package com.skodadash.ultra

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val unlocked: Boolean,
    val progress: Int = 0,
    val target: Int = 1
)

object AchievementsHelper {

    fun getAchievements(trips: List<TripData>): List<Achievement> {
        val totalDist = trips.sumOf { it.distanceMeters } / 1000.0
        val totalScore = trips.sumOf { it.score }
        val totalTrips = trips.size
        val maxSpeed = trips.maxOfOrNull { it.maxSpeedKmh } ?: 0.0
        val maxG = trips.maxOfOrNull { it.maxG } ?: 0.0
        val bestScore = trips.maxOfOrNull { it.score } ?: 0
        val noBrakeTrips = trips.count { it.events.none { e -> e.type.contains("BRAKE") } }
        val autoTrips = trips.count { it.isAuto }

        return listOf(
            Achievement("first_trip", "Erste Fahrt", "Deine erste Fahrt aufgezeichnet", "🚗", totalTrips >= 1, totalTrips, 1),
            Achievement("explorer_10", "Entdecker", "10 Fahrten", "🗺️", totalTrips >= 10, totalTrips, 10),
            Achievement("explorer_50", "Viel Fahrer", "50 Fahrten", "🏁", totalTrips >= 50, totalTrips, 50),
            Achievement("distance_100", "100 km", "100 km gesamt", "📍", totalDist >= 100, totalDist.toInt(), 100),
            Achievement("distance_500", "500 km", "500 km gesamt", "🌍", totalDist >= 500, totalDist.toInt(), 500),
            Achievement("distance_1000", "1000 km", "1000 km - Langstrecke", "🚀", totalDist >= 1000, totalDist.toInt(), 1000),
            Achievement("speed_120", "Schnell", "120 km/h erreicht", "💨", maxSpeed >= 120, maxSpeed.toInt(), 120),
            Achievement("speed_180", "Speed Demon", "180 km/h erreicht", "⚡", maxSpeed >= 180, maxSpeed.toInt(), 180),
            Achievement("gforce_08", "G-Kraft", "0.8 G erreicht", "🔄", maxG >= 0.8, (maxG*100).toInt(), 80),
            Achievement("gforce_12", "Grenzbereich", "1.2 G erreicht", "💥", maxG >= 1.2, (maxG*100).toInt(), 120),
            Achievement("score_500", "Punkte Sammler", "500 Punkte in einer Fahrt", "⭐", bestScore >= 500, bestScore, 500),
            Achievement("score_1000", "Legende", "1000 Punkte in einer Fahrt", "👑", bestScore >= 1000, bestScore, 1000),
            Achievement("smooth", "Sanft Fahrer", "Fahrt ohne Bremsen", "🍃", noBrakeTrips >= 1, noBrakeTrips, 1),
            Achievement("smooth_5", "Gleiter", "5 Fahrten ohne Bremsen", "🦢", noBrakeTrips >= 5, noBrakeTrips, 5),
            Achievement("auto_10", "Automatik", "10 Auto Fahrten", "🤖", autoTrips >= 10, autoTrips, 10),
            Achievement("total_5k", "Meister", "5000 Gesamtpunkte", "🏆", totalScore >= 5000, totalScore, 5000),
            Achievement("total_10k", "Champion", "10000 Gesamtpunkte", "🎖️", totalScore >= 10000, totalScore, 10000)
        )
    }

    fun getUnlockedCount(trips: List<TripData>): Pair<Int, Int> {
        val all = getAchievements(trips)
        return Pair(all.count { it.unlocked }, all.size)
    }

    fun getNextAchievement(trips: List<TripData>): Achievement? {
        return getAchievements(trips).filter { !it.unlocked }.minByOrNull { it.target - it.progress }
    }
}
