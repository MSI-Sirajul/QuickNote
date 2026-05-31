package com.example.data

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("quicknote_preferences", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_THEME = "app_theme"
        private const val KEY_LAYOUT = "notes_layout"
        private const val KEY_PIN = "security_pin"
        private const val KEY_BIOMETRIC_LOCK = "biometric_lock_enabled"
        private const val KEY_APP_LOCKED = "app_locked_v2"
    }

    var theme: String
        get() = prefs.getString(KEY_THEME, "System") ?: "System"
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    var layout: String
        get() = prefs.getString(KEY_LAYOUT, "Grid") ?: "Grid"
        set(value) = prefs.edit().putString(KEY_LAYOUT, value).apply()

    var pin: String
        get() = prefs.getString(KEY_PIN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PIN, value).apply()

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_LOCK, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_LOCK, value).apply()

    var isAppLocked: Boolean
        get() = prefs.getBoolean(KEY_APP_LOCKED, false)
        set(value) = prefs.edit().putBoolean(KEY_APP_LOCKED, value).apply()
}
