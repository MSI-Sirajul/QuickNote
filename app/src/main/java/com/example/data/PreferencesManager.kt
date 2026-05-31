package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "quicknote_settings")

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

class PreferencesManager(private val context: Context) {

    companion object {
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        val LAYOUT_GRID_KEY = booleanPreferencesKey("layout_grid")
        val FONT_SIZE_SCALE_KEY = floatPreferencesKey("font_size_scale")
        val BIOMETRIC_LOCK_KEY = booleanPreferencesKey("biometric_lock")
    }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        val modeStr = preferences[THEME_MODE_KEY] ?: ThemeMode.SYSTEM.name
        try {
            ThemeMode.valueOf(modeStr)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    val layoutGridFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[LAYOUT_GRID_KEY] ?: true // default to grid view
    }

    val fontSizeScaleFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[FONT_SIZE_SCALE_KEY] ?: 1.0f // default text size scale
    }

    val biometricLockFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[BIOMETRIC_LOCK_KEY] ?: false // default to unlocked
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.name
        }
    }

    suspend fun setLayoutGrid(isGrid: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[LAYOUT_GRID_KEY] = isGrid
        }
    }

    suspend fun setFontSizeScale(scale: Float) {
        context.dataStore.edit { preferences ->
            preferences[FONT_SIZE_SCALE_KEY] = scale
        }
    }

    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[BIOMETRIC_LOCK_KEY] = enabled
        }
    }
}
