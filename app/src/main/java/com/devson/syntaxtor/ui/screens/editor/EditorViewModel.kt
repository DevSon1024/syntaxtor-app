package com.devson.syntaxtor.ui.screens.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devson.syntaxtor.domain.model.EditorFile
import com.devson.syntaxtor.domain.usecase.OpenFileUseCase
import com.devson.syntaxtor.domain.usecase.SaveFileUseCase
import io.github.rosemoe.sora.widget.CodeEditor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

// UI State
sealed class EditorUiState {
    object Idle : EditorUiState()
    object Loading : EditorUiState()
    data class Error(val message: String) : EditorUiState()
    data class Ready(
        val openFiles: List<EditorFile> = emptyList(),
        val selectedFileIndex: Int = -1,
        val wordWrapEnabled: Boolean = false,
        // Search
        val searchQuery: String = "",
        val searchMatchCount: Int = 0,
        val searchMatchIndex: Int = -1,
        val isSearchVisible: Boolean = false,
    ) : EditorUiState()
}

// ViewModel
class EditorViewModel(
    private val openFileUseCase: OpenFileUseCase,
    private val saveFileUseCase: SaveFileUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Idle)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    /**
     * WeakReference to each tab's CodeEditor, keyed by URI string.
     * Using WeakReference so we don't leak Views after they are destroyed.
     */
    private val editorRefs = HashMap<String, WeakReference<CodeEditor>>()

    //  File / Tab operations 

    fun openFile(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = EditorUiState.Loading
            openFileUseCase(uri)
                .onSuccess { file ->
                    val currentState = _uiState.value
                    val currentFiles = (currentState as? EditorUiState.Ready)?.openFiles ?: emptyList()
                    val isWordWrap = (currentState as? EditorUiState.Ready)?.wordWrapEnabled ?: false

                    // Avoid duplicate tabs
                    val alreadyOpen = currentFiles.indexOfFirst { it.uri == file.uri }
                    if (alreadyOpen >= 0) {
                        _uiState.update {
                            (it as EditorUiState.Ready).copy(selectedFileIndex = alreadyOpen)
                        }
                        return@onSuccess
                    }

                    val newFiles = currentFiles + file
                    _uiState.value = EditorUiState.Ready(
                        openFiles = newFiles,
                        selectedFileIndex = newFiles.lastIndex,
                        wordWrapEnabled = isWordWrap,
                    )
                }
                .onFailure { error ->
                    _uiState.value = EditorUiState.Error(error.message ?: "Failed to open file")
                }
        }
    }

    fun selectTab(index: Int) {
        (_uiState.value as? EditorUiState.Ready)?.let { state ->
            if (index !in state.openFiles.indices) return
            _uiState.update {
                state.copy(
                    selectedFileIndex = index,
                    searchQuery = "",
                    searchMatchCount = 0,
                    searchMatchIndex = -1,
                    isSearchVisible = false
                )
            }
        }
    }

    fun closeTab(index: Int) {
        val state = _uiState.value as? EditorUiState.Ready ?: return
        if (index !in state.openFiles.indices) return

        val uri = state.openFiles[index].uri.toString()
        editorRefs.remove(uri)

        val newFiles = state.openFiles.toMutableList().apply { removeAt(index) }
        if (newFiles.isEmpty()) {
            _uiState.value = EditorUiState.Idle
            return
        }
        val newIndex = when {
            index < state.selectedFileIndex -> state.selectedFileIndex - 1
            index == state.selectedFileIndex -> (index - 1).coerceAtLeast(0)
            else -> state.selectedFileIndex
        }.coerceIn(0, newFiles.lastIndex)

        _uiState.update {
            state.copy(openFiles = newFiles, selectedFileIndex = newIndex)
        }
    }

    //  Editor registration (called from AndroidView factory/update) 

    /**
     * The composable calls this when the CodeEditor view is created or destroyed for a tab.
     * Registering null clears the reference.
     */
    fun registerEditorForFile(uri: String, editor: CodeEditor?) {
        if (editor == null) {
            editorRefs.remove(uri)
        } else {
            editorRefs[uri] = WeakReference(editor)
        }
    }

    /** Returns the live CodeEditor for the currently selected tab, or null. */
    fun currentEditor(): CodeEditor? {
        val state = _uiState.value as? EditorUiState.Ready ?: return null
        val uri = state.openFiles.getOrNull(state.selectedFileIndex)?.uri?.toString() ?: return null
        return editorRefs[uri]?.get()
    }

    //  Content / Save 

    /**
     * Marks the current file as modified when the editor content changes.
     * Sora fires this via ContentChangeEvent; we just keep [isModified] in sync.
     */
    fun onContentChanged() {
        (_uiState.value as? EditorUiState.Ready)?.let { state ->
            if (state.selectedFileIndex < 0) return
            val file = state.openFiles.getOrNull(state.selectedFileIndex) ?: return
            if (file.isModified) return // already marked – avoid redundant recomposition
            val updated = file.copy(isModified = true)
            _uiState.update {
                state.copy(
                    openFiles = state.openFiles.toMutableList().apply {
                        set(state.selectedFileIndex, updated)
                    }
                )
            }
        }
    }

    fun saveCurrentFile() {
        val state = _uiState.value as? EditorUiState.Ready ?: return
        if (state.selectedFileIndex < 0) return

        val file = state.openFiles[state.selectedFileIndex]
        val editor = currentEditor()

        // Grab latest text from Sora's internal content model
        val latestContent = editor?.text?.toString() ?: file.content

        viewModelScope.launch {
            saveFileUseCase(file.copy(content = latestContent)).onSuccess {
                val updatedFile = file.copy(content = latestContent, isModified = false)
                _uiState.update {
                    state.copy(
                        openFiles = state.openFiles.toMutableList().apply {
                            set(state.selectedFileIndex, updatedFile)
                        }
                    )
                }
            }
        }
    }

    fun toggleWordWrap() {
        (_uiState.value as? EditorUiState.Ready)?.let { state ->
            val newWrap = !state.wordWrapEnabled
            _uiState.update { state.copy(wordWrapEnabled = newWrap) }
            // Apply immediately to the active editor
            currentEditor()?.isWordwrap = newWrap
        }
    }

    //  Undo / Redo - delegated to Sora's built-in manager 

    fun undo() {
        currentEditor()?.undo()
    }

    fun redo() {
        currentEditor()?.redo()
    }

    //  Search - delegated to Sora's built-in searcher 

    fun toggleSearch() {
        (_uiState.value as? EditorUiState.Ready)?.let { state ->
            val nowVisible = !state.isSearchVisible
            if (!nowVisible) {
                // Clear Sora search highlight when bar is closed
                currentEditor()?.searcher?.stopSearch()
            }
            _uiState.update {
                state.copy(
                    isSearchVisible = nowVisible,
                    searchQuery = "",
                    searchMatchCount = 0,
                    searchMatchIndex = -1
                )
            }
        }
    }

    fun updateSearchQuery(query: String) {
        (_uiState.value as? EditorUiState.Ready)?.let { state ->
            _uiState.update { state.copy(searchQuery = query) }
            val editor = currentEditor() ?: return
            if (query.isBlank()) {
                editor.searcher.stopSearch()
                _uiState.update { (it as? EditorUiState.Ready)?.copy(searchMatchCount = 0, searchMatchIndex = -1) ?: it }
            } else {
                editor.searcher.search(
                    query,
                    io.github.rosemoe.sora.widget.EditorSearcher.SearchOptions(false, false)
                )
            }
        }
    }

    fun nextSearchMatch() {
        currentEditor()?.searcher?.gotoNext()
    }

    fun previousSearchMatch() {
        currentEditor()?.searcher?.gotoPrevious()
    }
}
