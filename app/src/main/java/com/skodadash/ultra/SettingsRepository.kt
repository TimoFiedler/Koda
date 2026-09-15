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

    suspend fun saveApiKey(key: String) {
        context.dataStore.edit { it[apiKeyKey] = key }
    }

    suspend fun saveVin(vin: String) {
        context.dataStore.edit { it[vinKey] = vin }
    }

    suspend fun getApiKey(): String {
        return context.dataStore.data.first()[apiKeyKey] ?: ""
    }

    suspend fun getVin(): String {
        return context.dataStore.data.first()[vinKey] ?: ""
    }

    suspend fun saveEngineType(type: String) {
        context.dataStore.edit { it[engineTypeKey] = type }
    }

    suspend fun getEngineType(): String {
        return context.dataStore.data.first()[engineTypeKey] ?: "electric"
    }

    suspend fun saveAutoTripEnabled(enabled: Boolean) {
        context.dataStore.edit { it[autoTripKey] = enabled }
    }

    suspend fun getAutoTripEnabled(): Boolean {
        return context.dataStore.data.first()[autoTripKey] ?: false
    }

    suspend fun saveAutoTripThreshold(threshold: Int) {
        context.dataStore.edit { it[autoTripThresholdKey] = threshold }
    }

    suspend fun getAutoTripThreshold(): Int {
        return context.dataStore.data.first()[autoTripThresholdKey] ?: 15
    }

    suspend fun saveAppTheme(theme: String) {
        context.dataStore.edit { it[appThemeKey] = theme }
    }

    suspend fun getAppTheme(): String {
        return context.dataStore.data.first()[appThemeKey] ?: "creme"
    }

    suspend fun saveWidgetAccent(accent: String) {
        context.dataStore.edit { it[widgetAccentKey] = accent }
    }

    suspend fun getWidgetAccent(): String {
        return context.dataStore.data.first()[widgetAccentKey] ?: "brown"
    }

    suspend fun saveAppAccent(accent: String) {
        context.dataStore.edit { it[appAccentKey] = accent }
    }

    suspend fun getAppAccent(): String {
        return context.dataStore.data.first()[appAccentKey] ?: "brown"
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
