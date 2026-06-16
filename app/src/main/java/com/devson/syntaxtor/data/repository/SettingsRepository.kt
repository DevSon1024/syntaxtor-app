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

    private val _overlayDuration = MutableStateFlow(getOverlayDurationPreference())
    val overlayDuration: StateFlow<Float> = _overlayDuration.asStateFlow()

    private val _hideSystemBarsInLandscape = MutableStateFlow(getHideSystemBarsInLandscapePreference())
    val hideSystemBarsInLandscape: StateFlow<Boolean> = _hideSystemBarsInLandscape.asStateFlow()

    private val _showFileExtensions = MutableStateFlow(getShowFileExtensionsPreference())
    val showFileExtensions: StateFlow<Boolean> = _showFileExtensions.asStateFlow()

    private val _zenModeEnabled = MutableStateFlow(getZenModePreference())
    val zenModeEnabled: StateFlow<Boolean> = _zenModeEnabled.asStateFlow()

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

    fun getOverlayDurationPreference(): Float {
        return prefs.getFloat("key_overlay_duration", 1.0f)
    }

    fun setOverlayDurationPreference(duration: Float) {
        prefs.edit().putFloat("key_overlay_duration", duration).apply()
        _overlayDuration.value = duration
    }

    fun getHideSystemBarsInLandscapePreference(): Boolean {
        return prefs.getBoolean("key_hide_system_bars_landscape", true)
    }

    fun setHideSystemBarsInLandscapePreference(hide: Boolean) {
        prefs.edit().putBoolean("key_hide_system_bars_landscape", hide).apply()
        _hideSystemBarsInLandscape.value = hide
    }

    fun getShowFileExtensionsPreference(): Boolean {
        return prefs.getBoolean("key_show_file_extensions", true)
    }

    fun setShowFileExtensionsPreference(show: Boolean) {
        prefs.edit().putBoolean("key_show_file_extensions", show).apply()
        _showFileExtensions.value = show
    }

    fun getZenModePreference(): Boolean {
        return prefs.getBoolean("key_zen_mode", true)
    }

    fun setZenModePreference(enabled: Boolean) {
        prefs.edit().putBoolean("key_zen_mode", enabled).apply()
        _zenModeEnabled.value = enabled
    }
}
