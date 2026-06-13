package com.devson.syntaxtor.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devson.syntaxtor.data.db.entity.FileHistoryEntity
import com.devson.syntaxtor.domain.model.EditorFile
import com.devson.syntaxtor.domain.usecase.GetHistoryUseCase
import com.devson.syntaxtor.domain.usecase.OpenFileUseCase
import com.devson.syntaxtor.domain.usecase.RestoreVersionUseCase
import com.devson.syntaxtor.domain.usecase.SaveCheckpointUseCase
import com.devson.syntaxtor.domain.usecase.SaveFileUseCase
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

// ---------------------------------------------------------------------------
// UI State
// ---------------------------------------------------------------------------
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
        // Version history
        val isVersionHistoryEnabled: Boolean = false,
        val showHistorySheet: Boolean = false,
        val historyEntries: List<FileHistoryEntity> = emptyList(),
        // Save indicator
        val isSaving: Boolean = false,
    ) : EditorUiState()
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------
class EditorViewModel(
    private val openFileUseCase: OpenFileUseCase,
    private val saveFileUseCase: SaveFileUseCase,
    private val saveCheckpointUseCase: SaveCheckpointUseCase,
    private val getHistoryUseCase: GetHistoryUseCase,
    private val restoreVersionUseCase: RestoreVersionUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Idle)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    /**
     * WeakReference to each tab's CodeEditor, keyed by URI string.
     * WeakReference prevents View leaks after the composable leaves composition.
     */
    private val editorRefs = HashMap<String, WeakReference<CodeEditor>>()

    /** Active collection job for history, cancelled when the file changes. */
    private var historyObserveJob: Job? = null

    // -----------------------------------------------------------------------
    //  File / Tab operations
    // -----------------------------------------------------------------------

    fun openFile(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = EditorUiState.Loading
            openFileUseCase(uri)
                .onSuccess { file ->
                    val currentState = _uiState.value
                    val currentFiles = (currentState as? EditorUiState.Ready)?.openFiles ?: emptyList()
                    val isWordWrap = (currentState as? EditorUiState.Ready)?.wordWrapEnabled ?: false
                    val histEnabled = (currentState as? EditorUiState.Ready)?.isVersionHistoryEnabled ?: false

                    // Avoid duplicate tabs
                    val alreadyOpen = currentFiles.indexOfFirst { it.uri == file.uri }
                    if (alreadyOpen >= 0) {
                        _uiState.update {
                            (it as EditorUiState.Ready).copy(selectedFileIndex = alreadyOpen)
                        }
                        startObservingHistory(file.uri.toString())
                        return@onSuccess
                    }

                    val newFiles = currentFiles + file
                    _uiState.value = EditorUiState.Ready(
                        openFiles = newFiles,
                        selectedFileIndex = newFiles.lastIndex,
                        wordWrapEnabled = isWordWrap,
                        isVersionHistoryEnabled = histEnabled,
                    )
                    startObservingHistory(file.uri.toString())
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
                    isSearchVisible = false,
                    showHistorySheet = false,
                )
            }
            startObservingHistory(state.openFiles[index].uri.toString())
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
        startObservingHistory(newFiles[newIndex].uri.toString())
    }

    // -----------------------------------------------------------------------
    //  Editor registration (called from AndroidView factory/update)
    // -----------------------------------------------------------------------

    fun registerEditorForFile(uri: String, editor: CodeEditor?) {
        if (editor == null) editorRefs.remove(uri)
        else editorRefs[uri] = WeakReference(editor)
    }

    fun currentEditor(): CodeEditor? {
        val state = _uiState.value as? EditorUiState.Ready ?: return null
        val uri = state.openFiles.getOrNull(state.selectedFileIndex)?.uri?.toString() ?: return null
        return editorRefs[uri]?.get()
    }

    // -----------------------------------------------------------------------
    //  Content / Save
    // -----------------------------------------------------------------------

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

    /**
     * Saves the current file.
     * If version history is enabled, also creates a checkpoint.
     */
    fun saveCurrentFile() {
        val state = _uiState.value as? EditorUiState.Ready ?: return
        if (state.selectedFileIndex < 0) return

        val file = state.openFiles[state.selectedFileIndex]
        val latestContent = currentEditor()?.text?.toString() ?: file.content

        _uiState.update { (it as? EditorUiState.Ready)?.copy(isSaving = true) ?: it }

        viewModelScope.launch {
            saveFileUseCase(file.copy(content = latestContent)).onSuccess {
                val updatedFile = file.copy(content = latestContent, isModified = false)
                _uiState.update { s ->
                    (s as? EditorUiState.Ready)?.copy(
                        openFiles = s.openFiles.toMutableList().apply {
                            set(s.selectedFileIndex, updatedFile)
                        },
                        isSaving = false
                    ) ?: s
                }

                // Save checkpoint if history is enabled
                if (state.isVersionHistoryEnabled) {
                    saveCheckpointUseCase(file.uri.toString(), latestContent)
                }
            }.onFailure {
                _uiState.update { s ->
                    (s as? EditorUiState.Ready)?.copy(isSaving = false) ?: s
                }
            }
        }
    }

    /**
     * Called by the BackHandler.
     * Saves the current file (if modified) and invokes [onDone] when finished.
     */
    fun autoSaveAndPop(onDone: () -> Unit) {
        val state = _uiState.value as? EditorUiState.Ready
        val file = state?.openFiles?.getOrNull(state.selectedFileIndex)

        if (file == null || !file.isModified) {
            onDone()
            return
        }

        val latestContent = currentEditor()?.text?.toString() ?: file.content
        _uiState.update { (it as? EditorUiState.Ready)?.copy(isSaving = true) ?: it }

        viewModelScope.launch {
            saveFileUseCase(file.copy(content = latestContent)).onSuccess {
                val updatedFile = file.copy(content = latestContent, isModified = false)
                _uiState.update { s ->
                    (s as? EditorUiState.Ready)?.copy(
                        openFiles = s.openFiles.toMutableList().apply {
                            set(s.selectedFileIndex, updatedFile)
                        },
                        isSaving = false
                    ) ?: s
                }
                if (state.isVersionHistoryEnabled) {
                    saveCheckpointUseCase(file.uri.toString(), latestContent)
                }
            }.onFailure {
                _uiState.update { s ->
                    (s as? EditorUiState.Ready)?.copy(isSaving = false) ?: s
                }
            }
            onDone()
        }
    }

    fun toggleWordWrap() {
        (_uiState.value as? EditorUiState.Ready)?.let { state ->
            val newWrap = !state.wordWrapEnabled
            _uiState.update { state.copy(wordWrapEnabled = newWrap) }
            currentEditor()?.isWordwrap = newWrap
        }
    }

    // -----------------------------------------------------------------------
    //  Version History
    // -----------------------------------------------------------------------

    fun toggleVersionHistory() {
        (_uiState.value as? EditorUiState.Ready)?.let { state ->
            _uiState.update { state.copy(isVersionHistoryEnabled = !state.isVersionHistoryEnabled) }
        }
    }

    fun toggleHistorySheet() {
        (_uiState.value as? EditorUiState.Ready)?.let { state ->
            _uiState.update { state.copy(showHistorySheet = !state.showHistorySheet) }
        }
    }

    fun dismissHistorySheet() {
        (_uiState.value as? EditorUiState.Ready)?.let { state ->
            _uiState.update { state.copy(showHistorySheet = false) }
        }
    }

    /**
     * Reconstructs the file at [checkpointId] and loads it into the active Sora editor.
     * Does NOT auto-save — the user should explicitly save after reviewing the restored version.
     */
    fun restoreVersion(checkpointId: Long) {
        val state = _uiState.value as? EditorUiState.Ready ?: return
        val file = state.openFiles.getOrNull(state.selectedFileIndex) ?: return

        viewModelScope.launch {
            restoreVersionUseCase(file.uri.toString(), checkpointId)
                .onSuccess { restoredText ->
                    // Push text into the live Sora editor
                    currentEditor()?.setText(restoredText)
                    // Mark as modified so the user sees there are unsaved changes
                    val updated = file.copy(content = restoredText, isModified = true)
                    _uiState.update { s ->
                        (s as? EditorUiState.Ready)?.copy(
                            openFiles = s.openFiles.toMutableList().apply {
                                set(s.selectedFileIndex, updated)
                            },
                            showHistorySheet = false
                        ) ?: s
                    }
                }
        }
    }

    private fun startObservingHistory(uri: String) {
        historyObserveJob?.cancel()
        historyObserveJob = viewModelScope.launch {
            getHistoryUseCase(uri).collect { entries ->
                _uiState.update { s ->
                    (s as? EditorUiState.Ready)?.copy(historyEntries = entries) ?: s
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Undo / Redo  (delegated to Sora's built-in manager)
    // -----------------------------------------------------------------------

    fun undo() { currentEditor()?.undo() }
    fun redo() { currentEditor()?.redo() }

    // -----------------------------------------------------------------------
    //  Search  (delegated to Sora's built-in searcher)
    // -----------------------------------------------------------------------

    fun toggleSearch() {
        (_uiState.value as? EditorUiState.Ready)?.let { state ->
            val nowVisible = !state.isSearchVisible
            if (!nowVisible) currentEditor()?.searcher?.stopSearch()
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
                _uiState.update {
                    (it as? EditorUiState.Ready)?.copy(
                        searchMatchCount = 0,
                        searchMatchIndex = -1
                    ) ?: it
                }
            } else {
                editor.searcher.search(query, EditorSearcher.SearchOptions(false, false))
            }
        }
    }

    fun nextSearchMatch() { currentEditor()?.searcher?.gotoNext() }
    fun previousSearchMatch() { currentEditor()?.searcher?.gotoPrevious() }
}
