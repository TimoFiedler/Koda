package com.skodadash.ultra

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

class SkodaWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    companion object {
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_skoda)
            val dataPrefs = context.getSharedPreferences("widget_data", Context.MODE_PRIVATE)
            val configPrefs = context.getSharedPreferences("widget_config_$widgetId", Context.MODE_PRIVATE)

            val battery = dataPrefs.getInt("battery", -1)
            val range = dataPrefs.getInt("range", -1)
            val odo = dataPrefs.getInt("odometer", -1)
            val locked = dataPrefs.getBoolean("locked", false)
            val hasLockData = dataPrefs.contains("locked")
            val charging = dataPrefs.getString("charging", "") ?: ""
            val name = dataPrefs.getString("name", "Skoda") ?: "Skoda"

            val showName = configPrefs.getBoolean("show_name", true)
            val showBattery = configPrefs.getBoolean("show_battery", true)
            val showRange = configPrefs.getBoolean("show_range", true)
            val showOdo = configPrefs.getBoolean("show_odo", false)
            val showLock = configPrefs.getBoolean("show_lock", true)
            val showCharging = configPrefs.getBoolean("show_charging", true)
            val bgStyle = configPrefs.getString("bg_style", "light") ?: "light"

            // Background style
            val bgRes = when (bgStyle) {
                "dark" -> R.drawable.widget_bg_dark
                "transparent" -> R.drawable.widget_bg_transparent
                else -> R.drawable.widget_bg
            }
            views.setInt(R.id.widgetRoot, "setBackgroundResource", bgRes)

            val titleColor = if (bgStyle == "dark") 0xFFFFFEF9.toInt() else 0xFF3E2723.toInt()
            val secondaryColor = if (bgStyle == "dark") 0xFFBCAAA4.toInt() else 0xFF8D6E63.toInt()

            // Title
            if (showName) {
                views.setViewVisibility(R.id.tvWidgetTitle, View.VISIBLE)
                views.setTextViewText(R.id.tvWidgetTitle, name.uppercase())
                views.setTextColor(R.id.tvWidgetTitle, titleColor)
            } else {
                views.setViewVisibility(R.id.tvWidgetTitle, View.GONE)
            }

            // Battery
            if (showBattery && battery >= 0) {
                views.setViewVisibility(R.id.tvWidgetBattery, View.VISIBLE)
                views.setTextViewText(R.id.tvWidgetBattery, "$battery%")
                views.setTextColor(R.id.tvWidgetBattery, titleColor)
            } else {
                views.setViewVisibility(R.id.tvWidgetBattery, View.GONE)
            }

            // Range
            if (showRange && range >= 0) {
                views.setViewVisibility(R.id.tvWidgetRange, View.VISIBLE)
                views.setTextViewText(R.id.tvWidgetRange, "$range km Reichweite")
                views.setTextColor(R.id.tvWidgetRange, secondaryColor)
            } else {
                views.setViewVisibility(R.id.tvWidgetRange, View.GONE)
            }

            // Odometer
            if (showOdo && odo >= 0) {
                views.setViewVisibility(R.id.tvWidgetOdo, View.VISIBLE)
                views.setTextViewText(R.id.tvWidgetOdo, "$odo km")
            } else {
                views.setViewVisibility(R.id.tvWidgetOdo, View.GONE)
            }

            // Lock
            if (showLock && hasLockData) {
                views.setViewVisibility(R.id.tvWidgetLock, View.VISIBLE)
                views.setTextViewText(R.id.tvWidgetLock, if (locked) "Verriegelt" else "Offen")
            } else {
                views.setViewVisibility(R.id.tvWidgetLock, View.GONE)
            }

            // Charging
            val chargingText = when (charging) {
                "CHARGING" -> "Laden"
                "CHARGED" -> "Vollstaendig"
                "READY_FOR_CHARGING" -> "Bereit"
                "CONSERVING" -> "Erhaltung"
                "CHARGING_INTERRUPTED" -> "Unterbrochen"
                else -> ""
            }
            if (showCharging && chargingText.isNotEmpty()) {
                views.setViewVisibility(R.id.tvWidgetCharging, View.VISIBLE)
                views.setTextViewText(R.id.tvWidgetCharging, chargingText)
            } else {
                views.setViewVisibility(R.id.tvWidgetCharging, View.GONE)
            }

            // Click opens app
            val intent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(context, widgetId, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.widgetRoot, pending)

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
