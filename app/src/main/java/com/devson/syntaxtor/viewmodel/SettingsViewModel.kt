package com.devson.syntaxtor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devson.syntaxtor.data.repository.SettingsRepository
import com.devson.syntaxtor.domain.usecase.ClearHistoryUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val clearHistoryUseCase: ClearHistoryUseCase,
) : ViewModel() {

    val theme: StateFlow<String> = settingsRepository.theme
    val versionHistoryEnabled: StateFlow<Boolean> = settingsRepository.versionHistoryEnabled
    val overlayDuration: StateFlow<Float> = settingsRepository.overlayDuration
    val hideSystemBarsInLandscape: StateFlow<Boolean> = settingsRepository.hideSystemBarsInLandscape
    val showFileExtensions: StateFlow<Boolean> = settingsRepository.showFileExtensions
    val zenModeEnabled: StateFlow<Boolean> = settingsRepository.zenModeEnabled

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    fun setTheme(themeMode: String) {
        settingsRepository.setThemePreference(themeMode)
    }

    fun setVersionHistoryEnabled(enabled: Boolean) {
        settingsRepository.setVersionHistoryPreference(enabled)
    }

    fun setOverlayDuration(duration: Float) {
        settingsRepository.setOverlayDurationPreference(duration)
    }

    fun setHideSystemBarsInLandscape(hide: Boolean) {
        settingsRepository.setHideSystemBarsInLandscapePreference(hide)
    }

    fun setShowFileExtensions(show: Boolean) {
        settingsRepository.setShowFileExtensionsPreference(show)
    }

    fun setZenModeEnabled(enabled: Boolean) {
        settingsRepository.setZenModePreference(enabled)
    }

    fun clearVersionHistory() {
        viewModelScope.launch {
            clearHistoryUseCase()
            _snackbarMessage.emit("History Cleared")
        }
    }
}
