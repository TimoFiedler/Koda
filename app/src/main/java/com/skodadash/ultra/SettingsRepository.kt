package com.skodadash.ultra

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val apiKeyKey = stringPreferencesKey("api_key")
    private val vinKey = stringPreferencesKey("vin")

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

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
