package com.devson.syntaxtor.ui.screens.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devson.syntaxtor.domain.model.EditorFile
import com.devson.syntaxtor.domain.usecase.OpenFileUseCase
import com.devson.syntaxtor.domain.usecase.SaveFileUseCase
import com.devson.syntaxtor.editor.engine.EditorEngine
import com.devson.syntaxtor.editor.engine.SearchEngine
import com.devson.syntaxtor.editor.engine.SearchMatch
import com.devson.syntaxtor.editor.suggestion.WordSuggestionProvider
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
        val searchMatches: List<SearchMatch> = emptyList(),
        val searchMatchIndex: Int = -1,
        val isSearchVisible: Boolean = false,
        // Suggestions
        val suggestions: List<String> = emptyList(),
        // Undo / Redo availability
        val canUndo: Boolean = false,
        val canRedo: Boolean = false
    ) : EditorUiState()
}

// ViewModel
@OptIn(FlowPreview::class)
class EditorViewModel(
    private val openFileUseCase: OpenFileUseCase,
    private val saveFileUseCase: SaveFileUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Idle)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    /** One engine per open tab, keyed by URI string. */
    private val engines = HashMap<String, EditorEngine>()

    /** Suggestion providers keyed by file-extension. */
    private val suggestionProviders = HashMap<String, WordSuggestionProvider>()

    private var suggestionJob: Job? = null

    //  File / Tab operations 

    fun openFile(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = EditorUiState.Loading
            openFileUseCase(uri)
                .onSuccess { file ->
                    val engine = EditorEngine().also { it.loadContent(file.content) }
                    engines[uri.toString()] = engine

                    val currentState = _uiState.value
                    val currentFiles = (currentState as? EditorUiState.Ready)?.openFiles ?: emptyList()
                    val isWordWrap = (currentState as? EditorUiState.Ready)?.wordWrapEnabled ?: false

                    val newFiles = currentFiles + file
                    _uiState.value = EditorUiState.Ready(
                        openFiles = newFiles,
                        selectedFileIndex = newFiles.lastIndex,
                        wordWrapEnabled = isWordWrap,
                        canUndo = false,
                        canRedo = false
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
            val engine = engineForIndex(state, index)
            _uiState.update {
                state.copy(
                    selectedFileIndex = index,
                    canUndo = engine?.canUndo() ?: false,
                    canRedo = engine?.canRedo() ?: false,
                    suggestions = emptyList(),
                    searchMatches = emptyList(),
                    searchMatchIndex = -1,
                    isSearchVisible = false
                )
            }
        }
    }

    fun closeTab(index: Int) {
        val state = _uiState.value as? EditorUiState.Ready ?: return
        if (index !in state.openFiles.indices) return

        // Clean up engine
        val uri = state.openFiles[index].uri.toString()
        engines.remove(uri)

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
            state.copy(
                openFiles = newFiles,
                selectedFileIndex = newIndex
            )
        }
    }

    //  Content editing 

    /**
     * Called by the composable on every keystroke.
     * Delegates to the engine for line diffing + undo tracking.
     * Uses debouncing for suggestions.
     */
    fun updateContent(newContent: String, cursorOffset: Int) {
        val state = _uiState.value as? EditorUiState.Ready ?: return
        if (state.selectedFileIndex < 0) return

        val engine = engineForIndex(state, state.selectedFileIndex) ?: return
        engine.applyFullUpdate(newContent, cursorOffset)

        val currentFile = state.openFiles[state.selectedFileIndex]
        val updatedFile = currentFile.copy(content = newContent, isModified = true)
        val newFiles = state.openFiles.toMutableList().apply {
            set(state.selectedFileIndex, updatedFile)
        }

        // Re-run search if active
        val newMatches = if (state.searchQuery.isNotBlank()) {
            SearchEngine.findAll(engine.lines, state.searchQuery)
        } else emptyList()

        _uiState.update {
            state.copy(
                openFiles = newFiles,
                canUndo = engine.canUndo(),
                canRedo = engine.canRedo(),
                searchMatches = newMatches,
                searchMatchIndex = if (newMatches.isEmpty()) -1 else 0
            )
        }

        scheduleSuggestions(newContent, cursorOffset, currentFile.fileType)
    }

    fun saveCurrentFile() {
        val state = _uiState.value as? EditorUiState.Ready ?: return
        if (state.selectedFileIndex < 0) return

        val currentFile = state.openFiles[state.selectedFileIndex]
        viewModelScope.launch {
            saveFileUseCase(currentFile).onSuccess {
                val updatedFile = currentFile.copy(isModified = false)
                val newFiles = state.openFiles.toMutableList().apply {
                    set(state.selectedFileIndex, updatedFile)
                }
                _uiState.update { state.copy(openFiles = newFiles) }
            }
        }
    }

    fun toggleWordWrap() {
        (_uiState.value as? EditorUiState.Ready)?.let { state ->
            _uiState.update { state.copy(wordWrapEnabled = !state.wordWrapEnabled) }
        }
    }

    //  Undo / Redo 

    fun undo() {
        val state = _uiState.value as? EditorUiState.Ready ?: return
        val engine = engineForIndex(state, state.selectedFileIndex) ?: return
        val newContent = engine.undo() ?: return
        syncEngineContentToState(state, newContent, engine)
    }

    fun redo() {
        val state = _uiState.value as? EditorUiState.Ready ?: return
        val engine = engineForIndex(state, state.selectedFileIndex) ?: return
        val newContent = engine.redo() ?: return
        syncEngineContentToState(state, newContent, engine)
    }

    private fun syncEngineContentToState(state: EditorUiState.Ready, newContent: String, engine: EditorEngine) {
        val currentFile = state.openFiles[state.selectedFileIndex]
        val updatedFile = currentFile.copy(content = newContent, isModified = true)
        val newFiles = state.openFiles.toMutableList().apply {
            set(state.selectedFileIndex, updatedFile)
        }
        _uiState.update {
            state.copy(
                openFiles = newFiles,
                canUndo = engine.canUndo(),
                canRedo = engine.canRedo()
            )
        }
    }

    //  Search 

    fun toggleSearch() {
        (_uiState.value as? EditorUiState.Ready)?.let { state ->
            _uiState.update {
                state.copy(
                    isSearchVisible = !state.isSearchVisible,
                    searchQuery = "",
                    searchMatches = emptyList(),
                    searchMatchIndex = -1
                )
            }
        }
    }

    fun updateSearchQuery(query: String) {
        val state = _uiState.value as? EditorUiState.Ready ?: return
        val engine = engineForIndex(state, state.selectedFileIndex)
        val matches = if (query.isNotBlank() && engine != null) {
            SearchEngine.findAll(engine.lines, query)
        } else emptyList()

        _uiState.update {
            state.copy(
                searchQuery = query,
                searchMatches = matches,
                searchMatchIndex = if (matches.isEmpty()) -1 else 0
            )
        }
    }

    fun nextSearchMatch() {
        (_uiState.value as? EditorUiState.Ready)?.let { state ->
            if (state.searchMatches.isEmpty()) return
            val next = (state.searchMatchIndex + 1) % state.searchMatches.size
            _uiState.update { state.copy(searchMatchIndex = next) }
        }
    }

    fun previousSearchMatch() {
        (_uiState.value as? EditorUiState.Ready)?.let { state ->
            if (state.searchMatches.isEmpty()) return
            val prev = (state.searchMatchIndex - 1 + state.searchMatches.size) % state.searchMatches.size
            _uiState.update { state.copy(searchMatchIndex = prev) }
        }
    }

    //  Suggestions 

    fun applySuggestion(suggestion: String, cursorOffset: Int, currentText: String) {
        val textUntilCursor = currentText.substring(0, cursorOffset.coerceAtMost(currentText.length))
        val lastWord = textUntilCursor.split(Regex("\\W+")).lastOrNull() ?: ""
        val newText = currentText.substring(0, cursorOffset - lastWord.length) +
                suggestion +
                currentText.substring(cursorOffset.coerceAtMost(currentText.length))
        val newCursor = cursorOffset - lastWord.length + suggestion.length
        updateContent(newText, newCursor)
        clearSuggestions()
    }

    fun clearSuggestions() {
        (_uiState.value as? EditorUiState.Ready)?.let { state ->
            if (state.suggestions.isNotEmpty()) {
                _uiState.update { state.copy(suggestions = emptyList()) }
            }
        }
    }

    //  Engine access 

    /** Expose the engine for the currently selected file so the UI can read lines. */
    fun currentEngine(): EditorEngine? {
        val state = _uiState.value as? EditorUiState.Ready ?: return null
        return engineForIndex(state, state.selectedFileIndex)
    }

    //  Private helpers 

    private fun engineForIndex(state: EditorUiState.Ready, index: Int): EditorEngine? {
        val uri = state.openFiles.getOrNull(index)?.uri?.toString() ?: return null
        return engines[uri]
    }

    private fun scheduleSuggestions(content: String, cursorOffset: Int, fileType: String) {
        suggestionJob?.cancel()
        suggestionJob = viewModelScope.launch {
            delay(180L) // 180 ms debounce
            val provider = suggestionProviders.getOrPut(fileType) { WordSuggestionProvider(fileType) }
            val safeOffset = cursorOffset.coerceIn(0, content.length)
            val textUntilCursor = content.substring(0, safeOffset)
            val lastWord = textUntilCursor.split(Regex("\\W+")).lastOrNull() ?: ""
            val newSuggestions = if (lastWord.length > 1) {
                provider.getSuggestions(lastWord)
            } else emptyList()

            (_uiState.value as? EditorUiState.Ready)?.let { state ->
                _uiState.update { state.copy(suggestions = newSuggestions) }
            }
        }
    }
}
