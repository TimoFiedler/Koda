package com.skodadash.ultra

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.skodadash.ultra.databinding.ActivityWidgetConfigBinding

class WidgetConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWidgetConfigBinding
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWidgetConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val prefs = getSharedPreferences("widget_config_$widgetId", MODE_PRIVATE)
        binding.cbBattery.isChecked = prefs.getBoolean("show_battery", true)
        binding.cbRange.isChecked = prefs.getBoolean("show_range", true)
        binding.cbLock.isChecked = prefs.getBoolean("show_lock", true)

        binding.btnSave.setOnClickListener {
            prefs.edit().apply {
                putBoolean("show_battery", binding.cbBattery.isChecked)
                putBoolean("show_range", binding.cbRange.isChecked)
                putBoolean("show_lock", binding.cbLock.isChecked)
                apply()
            }

            val appWidgetManager = AppWidgetManager.getInstance(this)
            SkodaWidgetProvider.updateWidget(this, appWidgetManager, widgetId)

            val result = Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            setResult(RESULT_OK, result)
            finish()
        }
    }
}
