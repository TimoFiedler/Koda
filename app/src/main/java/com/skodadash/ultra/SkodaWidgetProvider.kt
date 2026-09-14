package com.skodadash.ultra

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
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
            val prefs = context.getSharedPreferences("widget_data", Context.MODE_PRIVATE)
            val settingsPrefs = context.getSharedPreferences("widget_config_$widgetId", Context.MODE_PRIVATE)

            val battery = prefs.getInt("battery", 0)
            val range = prefs.getInt("range", 0)
            val locked = prefs.getBoolean("locked", false)
            val charging = prefs.getString("charging", "") ?: ""
            val name = prefs.getString("name", "Skoda") ?: "Skoda"

            val showBattery = settingsPrefs.getBoolean("show_battery", true)
            val showRange = settingsPrefs.getBoolean("show_range", true)
            val showLock = settingsPrefs.getBoolean("show_lock", true)

            views.setTextViewText(R.id.tvWidgetTitle, name)
            views.setTextViewText(R.id.tvWidgetBattery, if (showBattery) "Akku: $battery%" else "")
            views.setTextViewText(R.id.tvWidgetRange, if (showRange) "Reichweite: $range km" else "")
            views.setTextViewText(R.id.tvWidgetLock, if (showLock) if (locked) "🔒 Verriegelt" else "🔓 Offen" else "")
            views.setTextViewText(R.id.tvWidgetCharging, when (charging) {
                "CHARGING" -> "⚡ Lädt"
                "CHARGED" -> "✅ Voll"
                else -> ""
            })

            val intent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.widgetRoot, pending)

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
