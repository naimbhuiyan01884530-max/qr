package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.scanPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "scanvault_prefs"
)

data class ScannerPreferences(
    val autoCopyOnScan: Boolean = false,
    val hapticFeedback: Boolean = true,
    val continuousMode: Boolean = false,
    val isDarkTheme: Boolean = true,
    val languageCode: String = "en"
)

class ScanPreferencesRepository(private val context: Context) {

    private object Keys {
        val AUTO_COPY = booleanPreferencesKey("auto_copy_on_scan")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val CONTINUOUS_MODE = booleanPreferencesKey("continuous_mode")
        val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
    }

    val preferences: Flow<ScannerPreferences> = context.scanPrefsDataStore.data.map { prefs ->
        ScannerPreferences(
            autoCopyOnScan = prefs[Keys.AUTO_COPY] ?: false,
            hapticFeedback = prefs[Keys.HAPTIC_FEEDBACK] ?: true,
            continuousMode = prefs[Keys.CONTINUOUS_MODE] ?: false,
            isDarkTheme = prefs[Keys.IS_DARK_THEME] ?: true,
            languageCode = prefs[Keys.LANGUAGE_CODE] ?: "en"
        )
    }

    suspend fun setAutoCopy(enabled: Boolean) {
        context.scanPrefsDataStore.edit { it[Keys.AUTO_COPY] = enabled }
    }

    suspend fun setHapticFeedback(enabled: Boolean) {
        context.scanPrefsDataStore.edit { it[Keys.HAPTIC_FEEDBACK] = enabled }
    }

    suspend fun setContinuousMode(enabled: Boolean) {
        context.scanPrefsDataStore.edit { it[Keys.CONTINUOUS_MODE] = enabled }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.scanPrefsDataStore.edit { it[Keys.IS_DARK_THEME] = enabled }
    }

    suspend fun toggleLanguage() {
        context.scanPrefsDataStore.edit { prefs ->
            val current = prefs[Keys.LANGUAGE_CODE] ?: "en"
            prefs[Keys.LANGUAGE_CODE] = if (current == "en") "bn" else "en"
        }
    }
}
