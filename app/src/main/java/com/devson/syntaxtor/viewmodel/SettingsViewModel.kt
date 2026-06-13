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

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    fun setTheme(themeMode: String) {
        settingsRepository.setThemePreference(themeMode)
    }

    fun setVersionHistoryEnabled(enabled: Boolean) {
        settingsRepository.setVersionHistoryPreference(enabled)
    }

    fun clearVersionHistory() {
        viewModelScope.launch {
            clearHistoryUseCase()
            _snackbarMessage.emit("History Cleared")
        }
    }
}
