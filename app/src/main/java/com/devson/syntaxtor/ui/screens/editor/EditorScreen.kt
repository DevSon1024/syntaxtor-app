package com.devson.syntaxtor.ui.screens.editor

import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.devson.syntaxtor.domain.model.EditorFile
import com.devson.syntaxtor.preview.PreviewPlaceholder
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.subscribeEvent

// Root Screen

@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onOpenFileSelection: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var isPreviewVisible by remember { mutableStateOf(false) }

    // Edge-to-edge: consume only top+bottom insets inside the screen itself.
    // The outer Scaffold in MainActivity already gives us the status-bar slot;
    // we simply fill everything without extra padding here.
    Scaffold(
        topBar = {
            EditorTopBar(
                uiState = uiState,
                onSave = { viewModel.saveCurrentFile() },
                onWordWrapToggle = { viewModel.toggleWordWrap() },
                onOpenFile = onOpenFileSelection,
                isPreviewVisible = isPreviewVisible,
                onPreviewToggle = { isPreviewVisible = !isPreviewVisible },
                onToggleSearch = { viewModel.toggleSearch() },
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() }
            )
        },
        // Let the Scaffold handle insets from its built-in contentWindowInsets
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is EditorUiState.Idle -> CenterText("No file opened. Tap ⋮ to open one.")
                is EditorUiState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
                is EditorUiState.Error -> CenterText("Error: ${state.message}")
                is EditorUiState.Ready -> {
                    if (state.openFiles.isEmpty()) {
                        CenterText("No file opened.")
                    } else {
                        EditorContent(
                            state = state,
                            viewModel = viewModel,
                            onTabSelected = { viewModel.selectTab(it) },
                            onTabClosed = { viewModel.closeTab(it) },
                            onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                            onNextMatch = { viewModel.nextSearchMatch() },
                            onPrevMatch = { viewModel.previousSearchMatch() },
                            isPreviewVisible = isPreviewVisible
                        )
                    }
                }
            }
        }
    }
}

// Top Bar
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTopBar(
    uiState: EditorUiState,
    onSave: () -> Unit,
    onWordWrapToggle: () -> Unit,
    onOpenFile: () -> Unit,
    isPreviewVisible: Boolean,
    onPreviewToggle: () -> Unit,
    onToggleSearch: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit
) {
    val readyState = uiState as? EditorUiState.Ready
    val file = readyState?.openFiles?.getOrNull(readyState.selectedFileIndex)
    val title = file?.let { it.name + if (it.isModified) " •" else "" } ?: "Syntaxtor"
    val showPreview = file?.fileType?.let { it == ".html" || it == ".htm" } ?: false

    TopAppBar(
        title = { Text(title, maxLines = 1) },
        actions = {
            IconButton(onClick = onUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = onRedo) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
            }
            IconButton(onClick = onToggleSearch) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search",
                    tint = if (readyState?.isSearchVisible == true) MaterialTheme.colorScheme.primary
                    else LocalContentColor.current
                )
            }
            IconButton(onClick = onWordWrapToggle) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "Word wrap",
                    tint = if (readyState?.wordWrapEnabled == true) MaterialTheme.colorScheme.primary
                    else LocalContentColor.current
                )
            }
            if (showPreview) {
                IconButton(onClick = onPreviewToggle) {
                    Icon(
                        if (isPreviewVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "HTML preview"
                    )
                }
            }
            IconButton(onClick = onSave) {
                Icon(Icons.Default.Save, contentDescription = "Save")
            }
            IconButton(onClick = onOpenFile) {
                Icon(Icons.Default.VisibilityOff, contentDescription = "Open file")
            }
        }
    )
}

// Main Editor Content

@Composable
fun EditorContent(
    state: EditorUiState.Ready,
    viewModel: EditorViewModel,
    onTabSelected: (Int) -> Unit,
    onTabClosed: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onNextMatch: () -> Unit,
    onPrevMatch: () -> Unit,
    isPreviewVisible: Boolean
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TabBar(
            files = state.openFiles,
            selectedIndex = state.selectedFileIndex,
            onTabSelected = onTabSelected,
            onTabClosed = onTabClosed
        )

        AnimatedVisibility(
            visible = state.isSearchVisible,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            SearchBar(
                query = state.searchQuery,
                matchCount = state.searchMatchCount,
                currentMatchIndex = state.searchMatchIndex,
                onQueryChange = onSearchQueryChange,
                onNext = onNextMatch,
                onPrev = onPrevMatch
            )
        }

        val currentFile = state.openFiles.getOrNull(state.selectedFileIndex)
        if (currentFile == null) {
            CenterText("Select a file to start editing")
            return@Column
        }

        if (currentFile.fileType == ".html" && isPreviewVisible) {
            PreviewPlaceholder(content = currentFile.content)
        } else {
            SoraCodeEditor(
                file = currentFile,
                viewModel = viewModel,
                wordWrap = state.wordWrapEnabled,
                searchQuery = state.searchQuery,
                isSearchVisible = state.isSearchVisible
            )
        }
    }
}

// Sora CodeEditor via AndroidView

@Composable
fun SoraCodeEditor(
    file: EditorFile,
    viewModel: EditorViewModel,
    wordWrap: Boolean,
    searchQuery: String,
    isSearchVisible: Boolean
) {
    val backgroundColor = MaterialTheme.colorScheme.background.toArgb()
    val textColor       = MaterialTheme.colorScheme.onBackground.toArgb()
    val lineNumBgColor  = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    val lineNumColor    = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val cursorColor     = MaterialTheme.colorScheme.primary.toArgb()
    val selectionColor  = MaterialTheme.colorScheme.primaryContainer.toArgb()
    val matchColor      = MaterialTheme.colorScheme.tertiaryContainer.toArgb()

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            CodeEditor(context).apply {
                //  Initial text 
                setText(file.content)

                //  Word wrap 
                isWordwrap = wordWrap

                //  Monospace font 
                typefaceText = Typeface.MONOSPACE
                typefaceLineNumber = Typeface.MONOSPACE
                setTextSize(14f)

                //  Colors 
                applyColorScheme(
                    backgroundColor = backgroundColor,
                    textColor       = textColor,
                    lineNumBgColor  = lineNumBgColor,
                    lineNumColor    = lineNumColor,
                    cursorColor     = cursorColor,
                    selectionColor  = selectionColor,
                    matchColor      = matchColor
                )

                //  Content change → mark file as modified 
                subscribeEvent<ContentChangeEvent> { _, unsubscribe ->
                    viewModel.onContentChanged()
                }

                //  Register with ViewModel 
                viewModel.registerEditorForFile(file.uri.toString(), this)
            }
        },
        update = { editor ->
            //  Word wrap 
            if (editor.isWordwrap != wordWrap) {
                editor.isWordwrap = wordWrap
            }

            //  Re-apply colors (handles dark/light theme switch) 
            editor.applyColorScheme(
                backgroundColor = backgroundColor,
                textColor       = textColor,
                lineNumBgColor  = lineNumBgColor,
                lineNumColor    = lineNumColor,
                cursorColor     = cursorColor,
                selectionColor  = selectionColor,
                matchColor      = matchColor
            )

            //  Drive search 
            if (isSearchVisible && searchQuery.isNotBlank()) {
                editor.searcher.search(
                    searchQuery,
                    EditorSearcher.SearchOptions(false, false)
                )
            } else {
                editor.searcher.stopSearch()
            }
        }
    )

    // Clean up the editor reference when this composable leaves composition
    DisposableEffect(file.uri) {
        onDispose {
            viewModel.registerEditorForFile(file.uri.toString(), null)
        }
    }
}

// Color scheme helper - applies Material3 colors to Sora's EditorColorScheme
private fun CodeEditor.applyColorScheme(
    backgroundColor: Int,
    textColor: Int,
    lineNumBgColor: Int,
    lineNumColor: Int,
    cursorColor: Int,
    selectionColor: Int,
    matchColor: Int
) {
    val scheme = colorScheme
    scheme.setColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.WHOLE_BACKGROUND, backgroundColor)
    scheme.setColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.TEXT_NORMAL, textColor)
    scheme.setColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.LINE_NUMBER_BACKGROUND, lineNumBgColor)
    scheme.setColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.LINE_NUMBER, lineNumColor)
    scheme.setColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.SELECTION_INSERT, cursorColor)
    scheme.setColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.SELECTION_HANDLE, cursorColor)
    scheme.setColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.SELECTED_TEXT_BACKGROUND, selectionColor)
    scheme.setColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.MATCHED_TEXT_BACKGROUND, matchColor)
    // setColor() automatically triggers a redraw; no explicit notify needed.
    invalidate()
}

// Search Bar

@Composable
fun SearchBar(
    query: String,
    matchCount: Int,
    currentMatchIndex: Int,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit
) {
    Surface(
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search
                ),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text("Find in file…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                    inner()
                }
            )
            Spacer(Modifier.width(8.dp))
            if (matchCount > 0) {
                Text(
                    "${currentMatchIndex + 1}/$matchCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onPrev, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous match",
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next match",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// Tab Bar
@Composable
fun TabBar(
    files: List<EditorFile>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    onTabClosed: (Int) -> Unit
) {
    if (files.isEmpty()) return
    ScrollableTabRow(selectedTabIndex = selectedIndex, edgePadding = 0.dp) {
        files.forEachIndexed { index, file ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onTabSelected(index) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = file.name + if (file.isModified) " •" else "",
                            maxLines = 1
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close tab",
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { onTabClosed(index) },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    }
}

// Helpers
@Composable
fun CenterText(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
    }
}
