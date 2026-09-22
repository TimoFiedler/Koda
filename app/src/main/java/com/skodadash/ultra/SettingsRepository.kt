package com.skodadash.ultra

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val apiKeyKey = stringPreferencesKey("api_key")
    private val vinKey = stringPreferencesKey("vin")
    private val engineTypeKey = stringPreferencesKey("engine_type")
    private val autoTripKey = booleanPreferencesKey("auto_trip_enabled")
    private val autoTripThresholdKey = intPreferencesKey("auto_trip_threshold")
    private val appThemeKey = stringPreferencesKey("app_theme")
    private val widgetAccentKey = stringPreferencesKey("widget_accent")
    private val appAccentKey = stringPreferencesKey("app_accent")
    private val customBgKey = stringPreferencesKey("custom_bg_hex")
    private val customAccentKey = stringPreferencesKey("custom_accent_hex")
    private val customTextKey = stringPreferencesKey("custom_text_hex")
    private val widgetCustomBgKey = stringPreferencesKey("widget_custom_bg")
    private val widgetCustomAccentKey = stringPreferencesKey("widget_custom_accent")
    private val widgetCustomTextKey = stringPreferencesKey("widget_custom_text")
    private val carModelKey = stringPreferencesKey("car_model")
    private val profileImageKey = stringPreferencesKey("profile_image_uri")

    suspend fun saveApiKey(key: String) { context.dataStore.edit { it[apiKeyKey] = key } }
    suspend fun saveVin(vin: String) { context.dataStore.edit { it[vinKey] = vin } }
    suspend fun getApiKey(): String = context.dataStore.data.first()[apiKeyKey] ?: ""
    suspend fun getVin(): String = context.dataStore.data.first()[vinKey] ?: ""

    suspend fun saveEngineType(type: String) { context.dataStore.edit { it[engineTypeKey] = type } }
    suspend fun getEngineType(): String = context.dataStore.data.first()[engineTypeKey] ?: "electric"

    suspend fun saveAutoTripEnabled(enabled: Boolean) { context.dataStore.edit { it[autoTripKey] = enabled } }
    suspend fun getAutoTripEnabled(): Boolean = context.dataStore.data.first()[autoTripKey] ?: false

    suspend fun saveAutoTripThreshold(threshold: Int) { context.dataStore.edit { it[autoTripThresholdKey] = threshold } }
    suspend fun getAutoTripThreshold(): Int = context.dataStore.data.first()[autoTripThresholdKey] ?: 15

    suspend fun saveAppTheme(theme: String) { context.dataStore.edit { it[appThemeKey] = theme } }
    suspend fun getAppTheme(): String = context.dataStore.data.first()[appThemeKey] ?: "creme"

    suspend fun saveWidgetAccent(accent: String) { context.dataStore.edit { it[widgetAccentKey] = accent } }
    suspend fun getWidgetAccent(): String = context.dataStore.data.first()[widgetAccentKey] ?: "brown"

    suspend fun saveAppAccent(accent: String) { context.dataStore.edit { it[appAccentKey] = accent } }
    suspend fun getAppAccent(): String = context.dataStore.data.first()[appAccentKey] ?: "brown"

    suspend fun saveCustomBgHex(hex: String) { context.dataStore.edit { it[customBgKey] = hex } }
    suspend fun getCustomBgHex(): String = context.dataStore.data.first()[customBgKey] ?: ""

    suspend fun saveCustomAccentHex(hex: String) { context.dataStore.edit { it[customAccentKey] = hex } }
    suspend fun getCustomAccentHex(): String = context.dataStore.data.first()[customAccentKey] ?: ""

    suspend fun saveCustomTextHex(hex: String) { context.dataStore.edit { it[customTextKey] = hex } }
    suspend fun getCustomTextHex(): String = context.dataStore.data.first()[customTextKey] ?: ""

    suspend fun saveWidgetCustomBg(hex: String) { context.dataStore.edit { it[widgetCustomBgKey] = hex } }
    suspend fun getWidgetCustomBg(): String = context.dataStore.data.first()[widgetCustomBgKey] ?: ""

    suspend fun saveWidgetCustomAccent(hex: String) { context.dataStore.edit { it[widgetCustomAccentKey] = hex } }
    suspend fun getWidgetCustomAccent(): String = context.dataStore.data.first()[widgetCustomAccentKey] ?: ""

    suspend fun saveWidgetCustomText(hex: String) { context.dataStore.edit { it[widgetCustomTextKey] = hex } }
    suspend fun getWidgetCustomText(): String = context.dataStore.data.first()[widgetCustomTextKey] ?: ""

    suspend fun saveCarModel(model: String) { context.dataStore.edit { it[carModelKey] = model } }
    suspend fun getCarModel(): String = context.dataStore.data.first()[carModelKey] ?: "SCALA"

    suspend fun saveProfileImageUri(uri: String) { context.dataStore.edit { it[profileImageKey] = uri } }
    suspend fun getProfileImageUri(): String = context.dataStore.data.first()[profileImageKey] ?: ""

    suspend fun clear() { context.dataStore.edit { it.clear() } }
}
