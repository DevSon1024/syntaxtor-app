package com.devson.syntaxtor.ui.screens.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devson.syntaxtor.domain.model.EditorFile
import com.devson.syntaxtor.domain.usecase.OpenFileUseCase
import com.devson.syntaxtor.domain.usecase.SaveFileUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class EditorUiState {
    object Idle : EditorUiState()
    object Loading : EditorUiState()
    data class Error(val message: String) : EditorUiState()
    data class Ready(
        val openFiles: List<EditorFile> = emptyList(),
        val selectedFileIndex: Int = -1,
        val wordWrapEnabled: Boolean = false
    ) : EditorUiState()
}

class EditorViewModel(
    private val openFileUseCase: OpenFileUseCase,
    private val saveFileUseCase: SaveFileUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Idle)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    fun openFile(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = EditorUiState.Loading
            val result = openFileUseCase(uri)
            result.onSuccess { file ->
                val currentState = _uiState.value
                val currentFiles = if (currentState is EditorUiState.Ready) currentState.openFiles else emptyList()
                val isWordWrapEnabled = if (currentState is EditorUiState.Ready) currentState.wordWrapEnabled else false
                
                val newFiles = currentFiles + file
                _uiState.value = EditorUiState.Ready(
                    openFiles = newFiles,
                    selectedFileIndex = newFiles.lastIndex,
                    wordWrapEnabled = isWordWrapEnabled
                )
            }.onFailure { error ->
                _uiState.value = EditorUiState.Error(error.message ?: "Failed to open file")
            }
        }
    }

    fun updateContent(newContent: String) {
        val currentState = _uiState.value as? EditorUiState.Ready ?: return
        if (currentState.selectedFileIndex < 0) return

        val currentFile = currentState.openFiles[currentState.selectedFileIndex]
        val updatedFile = currentFile.copy(content = newContent, isModified = true)
        
        val newFiles = currentState.openFiles.toMutableList().apply {
            set(currentState.selectedFileIndex, updatedFile)
        }

        _uiState.update { 
            currentState.copy(openFiles = newFiles)
        }
    }

    fun saveCurrentFile() {
        val currentState = _uiState.value as? EditorUiState.Ready ?: return
        if (currentState.selectedFileIndex < 0) return

        val currentFile = currentState.openFiles[currentState.selectedFileIndex]
        viewModelScope.launch {
            saveFileUseCase(currentFile).onSuccess {
                val updatedFile = currentFile.copy(isModified = false)
                val newFiles = currentState.openFiles.toMutableList().apply {
                    set(currentState.selectedFileIndex, updatedFile)
                }
                _uiState.update { currentState.copy(openFiles = newFiles) }
            }
        }
    }

    fun toggleWordWrap() {
        (_uiState.value as? EditorUiState.Ready)?.let { currentState ->
            _uiState.update { currentState.copy(wordWrapEnabled = !currentState.wordWrapEnabled) }
        }
    }
    
    fun selectTab(index: Int) {
        (_uiState.value as? EditorUiState.Ready)?.let { currentState ->
            if (index in currentState.openFiles.indices) {
                _uiState.update { currentState.copy(selectedFileIndex = index) }
            }
        }
    }

    fun closeTab(index: Int) {
        val currentState = _uiState.value as? EditorUiState.Ready ?: return
        if (index !in currentState.openFiles.indices) return
        
        val newFiles = currentState.openFiles.toMutableList().apply { removeAt(index) }
        if (newFiles.isEmpty()) {
            _uiState.value = EditorUiState.Ready(newFiles, -1, currentState.wordWrapEnabled)
        } else {
            val newIndex = if (index <= currentState.selectedFileIndex && currentState.selectedFileIndex > 0) {
                currentState.selectedFileIndex - 1
            } else {
                if (index < currentState.selectedFileIndex) currentState.selectedFileIndex - 1 else currentState.selectedFileIndex
            }
            _uiState.value = currentState.copy(openFiles = newFiles, selectedFileIndex = newIndex)
        }
    }
}
