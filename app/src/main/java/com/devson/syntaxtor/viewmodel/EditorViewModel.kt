package com.devson.syntaxtor.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devson.syntaxtor.data.db.entity.FileHistoryEntity
import com.devson.syntaxtor.data.repository.SettingsRepository
import com.devson.syntaxtor.domain.model.EditorFile
import com.devson.syntaxtor.domain.usecase.AddRecentFileUseCase
import com.devson.syntaxtor.domain.usecase.GetHistoryUseCase
import com.devson.syntaxtor.domain.usecase.OpenFileUseCase
import com.devson.syntaxtor.domain.usecase.RestoreVersionUseCase
import com.devson.syntaxtor.domain.usecase.SaveCheckpointUseCase
import com.devson.syntaxtor.domain.usecase.SaveFileUseCase
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
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
        // Version history
        val isVersionHistoryEnabled: Boolean = true,
        val showHistorySheet: Boolean = false,
        val historyEntries: List<FileHistoryEntity> = emptyList(),
        // Save indicator
        val isSaving: Boolean = false,
        // File currently pending close confirmation dialog
        val filePendingClose: EditorFile? = null,
        // Split-pane mode (landscape): which file index goes in each pane
        val splitLeftIndex: Int = 0,
        val splitRightIndex: Int = 1,
        // Custom save dialog
        val showSaveDialogForFile: EditorFile? = null,
        val closeAfterSave: Boolean = false,
    ) : EditorUiState()
}

// ViewModel
class EditorViewModel(
    private val openFileUseCase: OpenFileUseCase,
    private val saveFileUseCase: SaveFileUseCase,
    private val saveCheckpointUseCase: SaveCheckpointUseCase,
    private val getHistoryUseCase: GetHistoryUseCase,
    private val restoreVersionUseCase: RestoreVersionUseCase,
    private val addRecentFileUseCase: AddRecentFileUseCase,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Idle)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val editorRefs = HashMap<String, WeakReference<CodeEditor>>()

    private var historyObserveJob: Job? = null

    // Event bus for global Snackbars
    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    val overlayDuration: StateFlow<Float> = settingsRepository.overlayDuration
    val hideSystemBarsInLandscape: StateFlow<Boolean> = settingsRepository.hideSystemBarsInLandscape
    val showFileExtensions: StateFlow<Boolean> = settingsRepository.showFileExtensions
    val zenModeEnabled: StateFlow<Boolean> = settingsRepository.zenModeEnabled

    // Navigation events
    sealed interface NavigationEvent {
        object NavigateToEditor : NavigationEvent
        data class NavigateToPreview(val fileUri: String) : NavigationEvent
    }

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

    init {
        // React to version history enabled setting changes
        viewModelScope.launch {
            settingsRepository.versionHistoryEnabled.collect { enabled ->
                _uiState.update { state ->
                    if (state is EditorUiState.Ready) {
                        state.copy(isVersionHistoryEnabled = enabled)
                    } else {
                        state
                    }
                }
            }
        }
    }

    // Get live content for file rendering (preview)
    fun getLiveContent(uri: String): String {
        val state = _uiState.value as? EditorUiState.Ready ?: return ""
        val index = state.openFiles.indexOfFirst { it.uri.toString() == uri }
        if (index < 0) return ""
        val file = state.openFiles[index]
        return if (index == state.selectedFileIndex) {
            currentEditor()?.text?.toString() ?: file.content
        } else {
            file.content
        }
    }

    fun triggerHtmlPreview(uriString: String) {
        val state = _uiState.value as? EditorUiState.Ready ?: return
        val index = state.openFiles.indexOfFirst { it.uri.toString() == uriString }
        if (index >= 0) {
            val file = state.openFiles[index]
            val liveContent = if (index == state.selectedFileIndex) {
                currentEditor()?.text?.toString() ?: file.content
            } else {
                file.content
            }
            val updatedFile = file.copy(content = liveContent)
            _uiState.update { s ->
                if (s is EditorUiState.Ready) {
                    val newFiles = s.openFiles.toMutableList().apply {
                        set(index, updatedFile)
                    }
                    s.copy(openFiles = newFiles)
                } else {
                    s
                }
            }
        }
        viewModelScope.launch {
            _navigationEvent.emit(NavigationEvent.NavigateToPreview(uriString))
        }
    }

    //  File / Tab operations

    fun openFile(uri: Uri) {
        viewModelScope.launch {
            // Snapshot existing state BEFORE emitting Loading so we don't lose open tabs
            val prevState = _uiState.value as? EditorUiState.Ready
            val currentFiles = prevState?.openFiles ?: emptyList()
            val isWordWrap = prevState?.wordWrapEnabled ?: false

            _uiState.value = EditorUiState.Loading
            openFileUseCase(uri)
                .onSuccess { file ->
                    val histEnabled = settingsRepository.getVersionHistoryPreference()

                    // Avoid duplicate tabs
                    val alreadyOpen = currentFiles.indexOfFirst { it.uri == file.uri }
                    if (alreadyOpen >= 0) {
                        // Restore from the snapshot captured before Loading was emitted.
                        // At this point _uiState is Loading, so casting it to Ready crashes -
                        // use prevState directly instead.
                        _uiState.value = (prevState ?: EditorUiState.Ready()).copy(
                            selectedFileIndex = alreadyOpen
                        )
                        startObservingHistory(file.uri.toString())
                        addRecentFileUseCase(file.uri.toString(), file.name, file.fileType)
                        _navigationEvent.emit(NavigationEvent.NavigateToEditor)
                        _snackbarMessage.emit("File Opened")
                        return@onSuccess
                    }

                    val newFiles = currentFiles + file
                    // Preserve existing split assignments; right pane defaults to the new file
                    val newSplitLeft = (prevState?.splitLeftIndex ?: 0)
                        .coerceIn(0, newFiles.lastIndex)
                    val newSplitRight = newFiles.lastIndex // newest file lands in right pane
                    _uiState.value = EditorUiState.Ready(
                        openFiles = newFiles,
                        selectedFileIndex = newFiles.lastIndex,
                        wordWrapEnabled = isWordWrap,
                        isVersionHistoryEnabled = histEnabled,
                        splitLeftIndex = newSplitLeft,
                        splitRightIndex = newSplitRight,
                    )
                    startObservingHistory(file.uri.toString())
                    addRecentFileUseCase(file.uri.toString(), file.name, file.fileType)
                    _navigationEvent.emit(NavigationEvent.NavigateToEditor)
                    _snackbarMessage.emit("File Opened")
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

    fun setSplitLeft(index: Int) {
        (_uiState.value as? EditorUiState.Ready)?.let { state ->
            if (index !in state.openFiles.indices) return
            _uiState.update { state.copy(splitLeftIndex = index, selectedFileIndex = index) }
            startObservingHistory(state.openFiles[index].uri.toString())
        }
    }

    fun setSplitRight(index: Int) {
        (_uiState.value as? EditorUiState.Ready)?.let { state ->
            if (index !in state.openFiles.indices) return
            _uiState.update { state.copy(splitRightIndex = index, selectedFileIndex = index) }
            startObservingHistory(state.openFiles[index].uri.toString())
        }
    }

    fun closeTab(index: Int) {
        val state = _uiState.value as? EditorUiState.Ready ?: return
        if (index !in state.openFiles.indices) return

        val uri = state.openFiles[index].uri.toString()
        editorRefs.remove(uri)

        val newFiles = state.openFiles.toMutableList().apply { removeAt(index) }
        viewModelScope.launch {
            _snackbarMessage.emit("File Closed")
        }

        if (newFiles.isEmpty()) {
            _uiState.value = EditorUiState.Idle
            return
        }

        fun remapIndex(old: Int): Int = when {
            index < old  -> old - 1
            index == old -> (old - 1).coerceAtLeast(0)
            else         -> old
        }.coerceIn(0, newFiles.lastIndex)

        val newSelected   = remapIndex(state.selectedFileIndex)
        val newSplitLeft  = remapIndex(state.splitLeftIndex)
        val newSplitRight = remapIndex(state.splitRightIndex)

        _uiState.update {
            state.copy(
                openFiles = newFiles,
                selectedFileIndex = newSelected,
                splitLeftIndex = newSplitLeft,
                splitRightIndex = newSplitRight,
            )
        }
        startObservingHistory(newFiles[newSelected].uri.toString())
    }

    fun closeFile(uri: String) {
        val state = _uiState.value as? EditorUiState.Ready ?: return
        val index = state.openFiles.indexOfFirst { it.uri.toString() == uri }
        if (index >= 0) {
            closeTab(index)
        }
    }

    fun requestCloseFile(uri: String) {
        val state = _uiState.value as? EditorUiState.Ready ?: return
        val file = state.openFiles.find { it.uri.toString() == uri } ?: return
        if (file.isModified) {
            _uiState.update { state.copy(filePendingClose = file) }
        } else {
            closeFile(uri)
        }
    }

    fun cancelCloseFile() {
        _uiState.update { state ->
            if (state is EditorUiState.Ready) {
                state.copy(filePendingClose = null)
            } else {
                state
            }
        }
    }

    fun discardAndCloseFile(uri: String) {
        closeFile(uri)
        _uiState.update { state ->
            if (state is EditorUiState.Ready) {
                state.copy(filePendingClose = null)
            } else {
                state
            }
        }
    }

    fun saveAndCloseFile(uri: String) {
        val state = _uiState.value as? EditorUiState.Ready ?: return
        val file = state.openFiles.find { it.uri.toString() == uri } ?: return
        val latestContent = currentEditor()?.text?.toString() ?: file.content

        _uiState.update { (it as? EditorUiState.Ready)?.copy(isSaving = true) ?: it }

        viewModelScope.launch {
            saveFileUseCase(file.copy(content = latestContent)).onSuccess {
                val updatedFile = file.copy(content = latestContent, isModified = false)
                _uiState.update { s ->
                    if (s is EditorUiState.Ready) {
                        val newFiles = s.openFiles.toMutableList().apply {
                            val idx = indexOfFirst { it.uri.toString() == uri }
                            if (idx >= 0) {
                                set(idx, updatedFile)
                            }
                        }
                        s.copy(openFiles = newFiles, isSaving = false, filePendingClose = null)
                    } else {
                        s
                    }
                }
                closeFile(uri)
                if (state.isVersionHistoryEnabled) {
                    saveCheckpointUseCase(file.uri.toString(), latestContent)
                }
                _snackbarMessage.emit("File Saved & Closed")
            }.onFailure {
                _uiState.update { s ->
                    (s as? EditorUiState.Ready)?.copy(isSaving = false) ?: s
                }
            }
        }
    }

    //  Editor registration (called from AndroidView factory/update)
    fun registerEditorForFile(uri: String, editor: CodeEditor?) {
        if (editor == null) editorRefs.remove(uri)
        else editorRefs[uri] = WeakReference(editor)
    }

    fun currentEditor(): CodeEditor? {
        val state = _uiState.value as? EditorUiState.Ready ?: return null
        val uri = state.openFiles.getOrNull(state.selectedFileIndex)?.uri?.toString() ?: return null
        return editorRefs[uri]?.get()
    }

    //  Content / Save
    fun onContentChanged() {
        (_uiState.value as? EditorUiState.Ready)?.let { state ->
            if (state.selectedFileIndex < 0) return
            val file = state.openFiles.getOrNull(state.selectedFileIndex) ?: return
            if (file.isModified) return // already marked - avoid redundant recomposition
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

    fun createNewFile() {
        viewModelScope.launch {
            val prevState = _uiState.value as? EditorUiState.Ready
            val currentFiles = prevState?.openFiles ?: emptyList()
            val isWordWrap = prevState?.wordWrapEnabled ?: false
            val histEnabled = settingsRepository.getVersionHistoryPreference()

            val tempUri = Uri.parse("syntaxtor://new-file/untitled_${System.currentTimeMillis()}")
            val newFile = EditorFile(
                uri = tempUri,
                name = "untitled",
                content = "",
                isModified = false,
                fileType = ".txt"
            )

            val newFiles = currentFiles + newFile
            val newSplitLeft = (prevState?.splitLeftIndex ?: 0).coerceIn(0, newFiles.lastIndex)
            val newSplitRight = newFiles.lastIndex
            
            _uiState.value = EditorUiState.Ready(
                openFiles = newFiles,
                selectedFileIndex = newFiles.lastIndex,
                wordWrapEnabled = isWordWrap,
                isVersionHistoryEnabled = histEnabled,
                splitLeftIndex = newSplitLeft,
                splitRightIndex = newSplitRight
            )
            _navigationEvent.emit(NavigationEvent.NavigateToEditor)
        }
    }

    fun triggerSaveDialog(uri: String, closeAfterSave: Boolean) {
        val state = _uiState.value as? EditorUiState.Ready ?: return
        val file = state.openFiles.find { it.uri.toString() == uri } ?: return
        _uiState.update {
            state.copy(
                showSaveDialogForFile = file,
                closeAfterSave = closeAfterSave,
                filePendingClose = null
            )
        }
    }

    fun cancelSaveDialog() {
        _uiState.update { state ->
            if (state is EditorUiState.Ready) {
                state.copy(showSaveDialogForFile = null, closeAfterSave = false)
            } else {
                state
            }
        }
    }

    fun saveNewFile(uriString: String, nameInput: String, extensionInput: String, closeAfterSave: Boolean) {
        val state = _uiState.value as? EditorUiState.Ready ?: return
        val fileIndex = state.openFiles.indexOfFirst { it.uri.toString() == uriString }
        if (fileIndex < 0) return
        val tempFile = state.openFiles[fileIndex]
        val latestContent = currentEditor()?.text?.toString() ?: tempFile.content

        viewModelScope.launch {
            try {
                val parentDir = File(
                    android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOCUMENTS
                    ),
                    "Syntaxtor"
                )
                if (!parentDir.exists()) {
                    parentDir.mkdirs()
                }

                val ext = if (extensionInput.startsWith(".")) extensionInput else ".$extensionInput"
                val baseName = if (nameInput.isBlank()) "SynDoc" else nameInput

                var targetFile = File(parentDir, "$baseName$ext")
                if (targetFile.exists()) {
                    var counter = 1
                    while (true) {
                        targetFile = File(parentDir, "$baseName($counter)$ext")
                        if (!targetFile.exists()) {
                            break
                        }
                        counter++
                    }
                }

                targetFile.writeText(latestContent)

                val newUri = Uri.fromFile(targetFile)
                val newName = targetFile.name
                val finalExt = targetFile.extension
                val fileType = if (finalExt.isNotEmpty()) ".$finalExt".lowercase() else ""

                val savedFile = EditorFile(
                    uri = newUri,
                    name = newName,
                    content = latestContent,
                    isModified = false,
                    fileType = fileType
                )

                val updatedFiles = state.openFiles.toMutableList()
                updatedFiles[fileIndex] = savedFile

                editorRefs.remove(uriString)
                addRecentFileUseCase(newUri.toString(), newName, fileType)

                if (state.isVersionHistoryEnabled) {
                    saveCheckpointUseCase(newUri.toString(), latestContent)
                }

                _uiState.update { s ->
                    if (s is EditorUiState.Ready) {
                        s.copy(
                            openFiles = updatedFiles,
                            showSaveDialogForFile = null,
                            closeAfterSave = false
                        )
                    } else {
                        s
                    }
                }

                if (closeAfterSave) {
                    closeFile(newUri.toString())
                    _snackbarMessage.emit("File Created & Closed")
                } else {
                    startObservingHistory(newUri.toString())
                    _snackbarMessage.emit("File Saved to Documents/Syntaxtor")
                }
            } catch (e: Exception) {
                _snackbarMessage.emit("Error saving file: ${e.message}")
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

        if (file.uri.scheme == "syntaxtor") {
            _uiState.update {
                state.copy(
                    showSaveDialogForFile = file,
                    closeAfterSave = false
                )
            }
        } else {
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
                    _snackbarMessage.emit("File Saved")
                }.onFailure {
                    _uiState.update { s ->
                        (s as? EditorUiState.Ready)?.copy(isSaving = false) ?: s
                    }
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
                _snackbarMessage.emit("File Saved")
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

    // Trigger external snackbar message
    fun triggerSnackbar(message: String) {
        viewModelScope.launch {
            _snackbarMessage.emit(message)
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
     * Does NOT auto-save - the user should explicitly save after reviewing the restored version.
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
                    _snackbarMessage.emit("Version Restored")
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

    //  Undo / Redo  (delegated to Sora's built-in manager)

    fun undo() { currentEditor()?.undo() }
    fun redo() { currentEditor()?.redo() }

    //  Search  (delegated to Sora's built-in searcher)

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

    fun nextSearchMatch() {
        try {
            currentEditor()?.searcher?.gotoNext()
        } catch (e: IllegalStateException) {
            triggerSnackbar("No search pattern set")
        }
    }

    fun previousSearchMatch() {
        try {
            currentEditor()?.searcher?.gotoPrevious()
        } catch (e: IllegalStateException) {
            triggerSnackbar("No search pattern set")
        }
    }
}
