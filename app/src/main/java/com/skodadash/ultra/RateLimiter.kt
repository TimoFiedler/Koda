package com.skodadash.ultra

import android.content.Context

class RateLimiter(private val context: Context) {
    private val prefs = context.getSharedPreferences("rate_limit", Context.MODE_PRIVATE)
    
    companion object {
        const val MAX_REQUESTS_PER_HOUR = 20
        const val WINDOW_MS = 60 * 60 * 1000L
    }

    fun canMakeRequest(): Boolean {
        val now = System.currentTimeMillis()
        val requests = getRequestTimes().filter { now - it < WINDOW_MS }
        return requests.size < MAX_REQUESTS_PER_HOUR
    }

    fun getRemainingRequests(): Int {
        val now = System.currentTimeMillis()
        val requests = getRequestTimes().filter { now - it < WINDOW_MS }
        return (MAX_REQUESTS_PER_HOUR - requests.size).coerceAtLeast(0)
    }

    fun getNextResetTime(): Long {
        val requests = getRequestTimes().sorted()
        if (requests.isEmpty()) return 0
        val oldestInWindow = requests.filter { System.currentTimeMillis() - it < WINDOW_MS }.minOrNull() ?: return 0
        return oldestInWindow + WINDOW_MS
    }

    fun recordRequest() {
        val now = System.currentTimeMillis()
        val requests = getRequestTimes().filter { now - it < WINDOW_MS }.toMutableList()
        requests.add(now)
        prefs.edit().putString("requests", requests.joinToString(",")).apply()
    }

    private fun getRequestTimes(): List<Long> {
        val str = prefs.getString("requests", "") ?: ""
        if (str.isEmpty()) return emptyList()
        return str.split(",").mapNotNull { it.toLongOrNull() }
    }

    fun getStatusText(): String {
        val remaining = getRemainingRequests()
        val nextReset = getNextResetTime()
        return if (remaining > 0) {
            "$remaining von $MAX_REQUESTS_PER_HOUR Anfragen übrig"
        } else {
            val mins = ((nextReset - System.currentTimeMillis()) / 60000).coerceAtLeast(0)
            "Limit erreicht - Reset in $mins Min"
        }
    }
}
