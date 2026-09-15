package com.skodadash.ultra

import android.graphics.Color

object ColorHelper {

    private val namedColors = mapOf(
        "creme" to "#FFF8E7",
        "cream" to "#FFF8E7",
        "sand" to "#FDF6E3",
        "mocca" to "#F5F0EB",
        "mocha" to "#F5F0EB",
        "graphite" to "#FAFAFA",
        "brown" to "#8B7355",
        "gold" to "#C5A880",
        "sage" to "#9CAF88",
        "olive" to "#8B8B6E",
        "dark" to "#3E2723",
        "white" to "#FFFFFF",
        "black" to "#000000"
    )

    fun parseColor(input: String?): Int? {
        if (input.isNullOrBlank()) return null
        val trimmed = input.trim().lowercase()
        // Check named
        namedColors[trimmed]?.let {
            return try { Color.parseColor(it) } catch (_: Exception) { null }
        }
        // Check hex
        var hex = input.trim()
        if (!hex.startsWith("#")) {
            // Maybe user typed creme etc with capital
            namedColors[hex.lowercase()]?.let {
                return try { Color.parseColor(it) } catch (_: Exception) { null }
            }
            // If it's hex without #
            if (hex.matches(Regex("[0-9A-Fa-f]{6}")) || hex.matches(Regex("[0-9A-Fa-f]{8}"))) {
                hex = "#$hex"
            } else {
                return null
            }
        }
        return try {
            Color.parseColor(hex)
        } catch (_: Exception) {
            null
        }
    }

    fun parseOrDefault(input: String?, default: Int): Int {
        return parseColor(input) ?: default
    }

    fun toHexString(color: Int): String {
        return String.format("#%06X", 0xFFFFFF and color)
    }
}
