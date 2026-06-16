package com.devson.syntaxtor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devson.syntaxtor.data.db.entity.NoteEntity
import com.devson.syntaxtor.data.repository.NoteRepository
import com.devson.syntaxtor.data.db.entity.RecentFileEntity
import com.devson.syntaxtor.data.repository.RecentFilesRepository
import com.devson.syntaxtor.domain.usecase.GetRecentFilesUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.devson.syntaxtor.data.repository.SettingsRepository

class HomeViewModel(
    getRecentFilesUseCase: GetRecentFilesUseCase,
    private val recentFilesRepository: RecentFilesRepository,
    private val noteRepository: NoteRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val showFileExtensions: StateFlow<Boolean> = settingsRepository.showFileExtensions

    val recentFiles: StateFlow<List<RecentFileEntity>> = getRecentFilesUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val notes: StateFlow<List<NoteEntity>> = noteRepository.observeNotes()
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

    fun saveNote(note: NoteEntity) {
        viewModelScope.launch {
            noteRepository.saveNote(note)
        }
    }

    fun deleteNote(id: Int) {
        viewModelScope.launch {
            noteRepository.deleteNote(id)
        }
    }
}
