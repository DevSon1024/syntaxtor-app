package com.devson.syntaxtor.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("syntaxtor_prefs", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(getThemePreference())
    val theme: StateFlow<String> = _theme.asStateFlow()

    private val _versionHistoryEnabled = MutableStateFlow(getVersionHistoryPreference())
    val versionHistoryEnabled: StateFlow<Boolean> = _versionHistoryEnabled.asStateFlow()

    fun getThemePreference(): String {
        return prefs.getString("key_theme", "SYSTEM") ?: "SYSTEM"
    }

    fun setThemePreference(theme: String) {
        prefs.edit().putString("key_theme", theme).apply()
        _theme.value = theme
    }

    fun getVersionHistoryPreference(): Boolean {
        return prefs.getBoolean("key_version_history", true)
    }

    fun setVersionHistoryPreference(enabled: Boolean) {
        prefs.edit().putBoolean("key_version_history", enabled).apply()
        _versionHistoryEnabled.value = enabled
    }
}
