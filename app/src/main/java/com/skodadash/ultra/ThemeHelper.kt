package com.skodadash.ultra

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

object ThemeHelper {

    fun applyTheme(context: Context, rootView: View?) {
        // Load colors synchronously for quick apply
        val prefs = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        // Try DataStore via runBlocking for simplicity in UI thread (small)
        // Actually use SharedPreferences mirror if exists, else DataStore
        try {
            val settings = SettingsRepository(context)
            runBlocking {
                val customBg = settings.getCustomBgHex()
                val customAccent = settings.getCustomAccentHex()
                val appTheme = settings.getAppTheme()

                val bgColor = ColorHelper.parseColor(customBg) ?: ColorHelper.parseColor(appTheme) ?: Color.parseColor("#FFF8E7")
                val accentColor = ColorHelper.parseColor(customAccent) ?: ColorHelper.parseColor(settings.getWidgetAccent()) ?: Color.parseColor("#8B7355")

                // Apply to root if provided
                rootView?.setBackgroundColor(bgColor)

                // Save to shared prefs for quick access
                context.getSharedPreferences("theme_cache", Context.MODE_PRIVATE).edit().apply {
                    putInt("bg_color", bgColor)
                    putInt("accent_color", accentColor)
                    apply()
                }
            }
        } catch (_: Exception) {}
    }

    fun getBgColor(context: Context): Int {
        return context.getSharedPreferences("theme_cache", Context.MODE_PRIVATE).getInt("bg_color", Color.parseColor("#FFF8E7"))
    }

    fun getAccentColor(context: Context): Int {
        return context.getSharedPreferences("theme_cache", Context.MODE_PRIVATE).getInt("accent_color", Color.parseColor("#8B7355"))
    }

    fun styleCard(card: MaterialCardView, context: Context) {
        val bg = getBgColor(context)
        // Only style if custom bg is not default cream? Keep white cards but border accent
        // For minimal, keep cards white
    }

    fun stylePrimaryButton(button: MaterialButton, context: Context) {
        val accent = getAccentColor(context)
        button.backgroundTintList = android.content.res.ColorStateList.valueOf(accent)
    }
}
