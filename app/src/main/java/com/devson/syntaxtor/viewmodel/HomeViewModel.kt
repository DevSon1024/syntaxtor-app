package com.devson.syntaxtor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devson.syntaxtor.data.db.entity.RecentFileEntity
import com.devson.syntaxtor.data.repository.RecentFilesRepository
import com.devson.syntaxtor.domain.usecase.GetRecentFilesUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    getRecentFilesUseCase: GetRecentFilesUseCase,
    private val recentFilesRepository: RecentFilesRepository,
) : ViewModel() {

    val recentFiles: StateFlow<List<RecentFileEntity>> = getRecentFilesUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun removeRecentFile(uriString: String) {
        viewModelScope.launch {
            recentFilesRepository.removeRecentFile(uriString)
        }
    }
}
