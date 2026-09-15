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

        binding.cbName.isChecked = prefs.getBoolean("show_name", true)
        binding.cbBattery.isChecked = prefs.getBoolean("show_battery", true)
        binding.cbRange.isChecked = prefs.getBoolean("show_range", true)
        binding.cbOdo.isChecked = prefs.getBoolean("show_odo", false)
        binding.cbLock.isChecked = prefs.getBoolean("show_lock", true)
        binding.cbCharging.isChecked = prefs.getBoolean("show_charging", true)

        when (prefs.getString("bg_style", "light")) {
            "dark" -> binding.rbDark.isChecked = true
            "transparent" -> binding.rbTransparent.isChecked = true
            else -> binding.rbLight.isChecked = true
        }

        binding.btnSave.setOnClickListener {
            val bgStyle = when (binding.rgBackground.checkedRadioButtonId) {
                R.id.rbDark -> "dark"
                R.id.rbTransparent -> "transparent"
                else -> "light"
            }

            prefs.edit().apply {
                putBoolean("show_name", binding.cbName.isChecked)
                putBoolean("show_battery", binding.cbBattery.isChecked)
                putBoolean("show_range", binding.cbRange.isChecked)
                putBoolean("show_odo", binding.cbOdo.isChecked)
                putBoolean("show_lock", binding.cbLock.isChecked)
                putBoolean("show_charging", binding.cbCharging.isChecked)
                putString("bg_style", bgStyle)
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
