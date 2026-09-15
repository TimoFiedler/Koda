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

        // Inhalte
        binding.cbName.isChecked = prefs.getBoolean("show_name", true)
        binding.cbBattery.isChecked = prefs.getBoolean("show_battery", true)
        binding.cbRange.isChecked = prefs.getBoolean("show_range", true)
        binding.cbOdo.isChecked = prefs.getBoolean("show_odo", false)
        binding.cbLock.isChecked = prefs.getBoolean("show_lock", true)
        binding.cbCharging.isChecked = prefs.getBoolean("show_charging", true)

        // Layout
        when (prefs.getString("layout_style", "compact")) {
            "detailed" -> binding.rbLayoutDetailed.isChecked = true
            "minimal" -> binding.rbLayoutMinimal.isChecked = true
            else -> binding.rbLayoutCompact.isChecked = true
        }

        when (prefs.getString("corner_radius", "medium")) {
            "small" -> binding.rbCornerSmall.isChecked = true
            "large" -> binding.rbCornerLarge.isChecked = true
            else -> binding.rbCornerMedium.isChecked = true
        }

        when (prefs.getString("text_size", "medium")) {
            "small" -> binding.rbTextSmall.isChecked = true
            "large" -> binding.rbTextLarge.isChecked = true
            else -> binding.rbTextMedium.isChecked = true
        }

        // Farben
        when (prefs.getString("bg_style", "light")) {
            "sand" -> binding.rbSand.isChecked = true
            "mocca" -> binding.rbMocca.isChecked = true
            "dark" -> binding.rbDark.isChecked = true
            "transparent" -> binding.rbTransparent.isChecked = true
            else -> binding.rbLight.isChecked = true
        }

        when (prefs.getString("accent_color", "brown")) {
            "gold" -> binding.rbAccentGold.isChecked = true
            "sage" -> binding.rbAccentSage.isChecked = true
            "olive" -> binding.rbAccentOlive.isChecked = true
            "graphite" -> binding.rbAccentGraphite.isChecked = true
            else -> binding.rbAccentBrown.isChecked = true
        }

        binding.btnSave.setOnClickListener {
            val bgStyle = when (binding.rgBackground.checkedRadioButtonId) {
                R.id.rbSand -> "sand"
                R.id.rbMocca -> "mocca"
                R.id.rbDark -> "dark"
                R.id.rbTransparent -> "transparent"
                else -> "light"
            }

            val accentColor = when (binding.rgAccent.checkedRadioButtonId) {
                R.id.rbAccentGold -> "gold"
                R.id.rbAccentSage -> "sage"
                R.id.rbAccentOlive -> "olive"
                R.id.rbAccentGraphite -> "graphite"
                else -> "brown"
            }

            val layoutStyle = when (binding.rgLayoutStyle.checkedRadioButtonId) {
                R.id.rbLayoutDetailed -> "detailed"
                R.id.rbLayoutMinimal -> "minimal"
                else -> "compact"
            }

            val cornerRadius = when (binding.rgCornerRadius.checkedRadioButtonId) {
                R.id.rbCornerSmall -> "small"
                R.id.rbCornerLarge -> "large"
                else -> "medium"
            }

            val textSize = when (binding.rgTextSize.checkedRadioButtonId) {
                R.id.rbTextSmall -> "small"
                R.id.rbTextLarge -> "large"
                else -> "medium"
            }

            prefs.edit().apply {
                putBoolean("show_name", binding.cbName.isChecked)
                putBoolean("show_battery", binding.cbBattery.isChecked)
                putBoolean("show_range", binding.cbRange.isChecked)
                putBoolean("show_odo", binding.cbOdo.isChecked)
                putBoolean("show_lock", binding.cbLock.isChecked)
                putBoolean("show_charging", binding.cbCharging.isChecked)
                putString("bg_style", bgStyle)
                putString("accent_color", accentColor)
                putString("layout_style", layoutStyle)
                putString("corner_radius", cornerRadius)
                putString("text_size", textSize)
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
