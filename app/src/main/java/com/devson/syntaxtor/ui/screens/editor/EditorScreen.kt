package com.devson.syntaxtor.ui.screens.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.syntaxtor.domain.model.EditorFile
import com.devson.syntaxtor.editor.engine.EditorEngine
import com.devson.syntaxtor.editor.engine.SearchMatch
import com.devson.syntaxtor.editor.syntax.HighlightCache
import com.devson.syntaxtor.editor.syntax.SyntaxHighlighterFactory
import com.devson.syntaxtor.preview.PreviewPlaceholder

// Root screen
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onOpenFileSelection: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var isPreviewVisible by remember { mutableStateOf(false) }

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
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
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
                            engine = viewModel.currentEngine(),
                            onContentChange = { text, offset -> viewModel.updateContent(text, offset) },
                            onTabSelected = { viewModel.selectTab(it) },
                            onTabClosed = { viewModel.closeTab(it) },
                            onSuggestionApplied = { suggestion, offset, text ->
                                viewModel.applySuggestion(suggestion, offset, text)
                            },
                            onSuggestionDismiss = { viewModel.clearSuggestions() },
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

// Top bar
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
            // Undo
            IconButton(onClick = onUndo, enabled = readyState?.canUndo == true) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
            }
            // Redo
            IconButton(onClick = onRedo, enabled = readyState?.canRedo == true) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
            }
            // Search
            IconButton(onClick = onToggleSearch) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search",
                    tint = if (readyState?.isSearchVisible == true) MaterialTheme.colorScheme.primary
                    else LocalContentColor.current
                )
            }
            // Word wrap
            IconButton(onClick = onWordWrapToggle) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "Word wrap",
                    tint = if (readyState?.wordWrapEnabled == true) MaterialTheme.colorScheme.primary
                    else LocalContentColor.current
                )
            }
            // HTML preview
            if (showPreview) {
                IconButton(onClick = onPreviewToggle) {
                    Icon(
                        if (isPreviewVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "HTML preview"
                    )
                }
            }
            // Save
            IconButton(onClick = onSave) {
                Icon(Icons.Default.Save, contentDescription = "Save")
            }
            // Open
            IconButton(onClick = onOpenFile) {
                Icon(Icons.Default.VisibilityOff, contentDescription = "Open file")
            }
        }
    )
}

// Main editor content
@Composable
fun EditorContent(
    state: EditorUiState.Ready,
    engine: EditorEngine?,
    onContentChange: (String, Int) -> Unit,
    onTabSelected: (Int) -> Unit,
    onTabClosed: (Int) -> Unit,
    onSuggestionApplied: (String, Int, String) -> Unit,
    onSuggestionDismiss: () -> Unit,
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

        // Search bar (animated)
        AnimatedVisibility(
            visible = state.isSearchVisible,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            SearchBar(
                query = state.searchQuery,
                matchCount = state.searchMatches.size,
                currentMatchIndex = state.searchMatchIndex,
                onQueryChange = onSearchQueryChange,
                onNext = onNextMatch,
                onPrev = onPrevMatch
            )
        }

        val currentFile = state.openFiles.getOrNull(state.selectedFileIndex)
        if (currentFile == null || engine == null) {
            CenterText("Select a file to start editing")
            return@Column
        }

        // Suggestion bar
        if (state.suggestions.isNotEmpty()) {
            SuggestionBar(
                suggestions = state.suggestions,
                onApply = { suggestion ->
                    // We'll pass cursor offset from the composed text field
                    onSuggestionApplied(suggestion, 0, currentFile.content)
                },
                onDismiss = onSuggestionDismiss
            )
        }

        if (currentFile.fileType == ".html" && isPreviewVisible) {
            PreviewPlaceholder(content = currentFile.content)
        } else {
            VirtualizedEditor(
                file = currentFile,
                engine = engine,
                wordWrap = state.wordWrapEnabled,
                searchMatches = state.searchMatches,
                activeMatchIndex = state.searchMatchIndex,
                suggestions = state.suggestions,
                onContentChange = onContentChange,
                onSuggestionApplied = onSuggestionApplied,
                onSuggestionDismiss = onSuggestionDismiss
            )
        }
    }
}

// Virtualized Editor (LazyColumn)
@Composable
fun VirtualizedEditor(
    file: EditorFile,
    engine: EditorEngine,
    wordWrap: Boolean,
    searchMatches: List<SearchMatch>,
    activeMatchIndex: Int,
    suggestions: List<String>,
    onContentChange: (String, Int) -> Unit,
    onSuggestionApplied: (String, Int, String) -> Unit,
    onSuggestionDismiss: () -> Unit
) {
    // HighlightCache is remembered per file-type so it survives recompositions
    val highlighter = remember(file.fileType) {
        SyntaxHighlighterFactory.getHighlighter(file.fileType)
    }
    val highlightCache = remember(file.fileType) { HighlightCache(highlighter) }

    // TextFieldValue drives both the rich editor and content change callbacks
    var textFieldValue by remember(file.uri) {
        mutableStateOf(TextFieldValue(file.content))
    }

    // Keep in sync if content changes externally (undo/redo)
    LaunchedEffect(file.content) {
        if (textFieldValue.text != file.content) {
            textFieldValue = TextFieldValue(file.content)
        }
    }

    // Build search match sets for fast per-line lookup
    val matchesByLine: Map<Int, List<SearchMatch>> = remember(searchMatches) {
        searchMatches.groupBy { it.line }
    }

    val cursorLine by remember(textFieldValue.selection) {
        derivedStateOf {
            engine.offsetToPosition(textFieldValue.selection.start).line
        }
    }

    val lazyListState = rememberLazyListState()

    // Auto-scroll to active search match
    LaunchedEffect(activeMatchIndex) {
        if (activeMatchIndex >= 0 && searchMatches.isNotEmpty()) {
            val targetLine = searchMatches[activeMatchIndex].line
            lazyListState.animateScrollToItem(targetLine)
        }
    }

    // We use ONE BasicTextField for actual editing (hidden) and LazyColumn for visual rendering.
    // This is the standard pattern for large-file editors on Android.
    Box(modifier = Modifier.fillMaxSize()) {
        // The real, invisible BasicTextField — handles all input
        BasicTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                textFieldValue = newValue
                onContentChange(newValue.text, newValue.selection.start)
                onSuggestionDismiss()
            },
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent),
            textStyle = TextStyle(color = Color.Transparent, fontSize = 14.sp),
            cursorBrush = SolidColor(Color.Transparent),
            visualTransformation = VisualTransformation.None
        )

        // Virtualized display layer
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Line numbers gutter
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .width(48.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                userScrollEnabled = false
            ) {
                itemsIndexed(engine.lines) { index, _ ->
                    Text(
                        text = "${index + 1}",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = if (index == cursorLine)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp, top = 2.dp, bottom = 2.dp)
                    )
                }
            }

            // Code display — scrollable, reads from engine.lines
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                itemsIndexed(engine.lines) { index, lineText ->
                    val lineMatches = matchesByLine[index]
                    val isActiveLine = index == cursorLine

                    val displayText = remember(lineText, lineMatches, activeMatchIndex) {
                        buildHighlightedLine(
                            text = lineText,
                            cachedAnnotated = highlightCache.getLine(index, lineText),
                            lineMatches = lineMatches,
                            activeMatchIndex = activeMatchIndex,
                            globalMatches = searchMatches
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isActiveLine) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = displayText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            softWrap = wordWrap,
                            maxLines = if (wordWrap) Int.MAX_VALUE else 1
                        )
                    }
                }
            }
        }

        // Real blinking cursor overlay (bottom of box)
        // The invisible BasicTextField above handles actual cursor display
    }
}

// Highlight builder — merges syntax + search highlights
private fun buildHighlightedLine(
    text: String,
    cachedAnnotated: AnnotatedString,
    lineMatches: List<SearchMatch>?,
    activeMatchIndex: Int,
    globalMatches: List<SearchMatch>
): AnnotatedString {
    if (lineMatches.isNullOrEmpty()) return cachedAnnotated

    return buildAnnotatedString {
        append(cachedAnnotated)
        lineMatches.forEach { match ->
            val isActive = globalMatches.indexOf(match) == activeMatchIndex
            addStyle(
                SpanStyle(
                    background = if (isActive) Color(0xFFFFD700) else Color(0xFFFFD70060),
                    color = if (isActive) Color.Black else Color.Unspecified
                ),
                start = match.startColumn.coerceIn(0, text.length),
                end = match.endColumn.coerceIn(0, text.length)
            )
        }
    }
}

// Search bar
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous match", modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next match", modifier = Modifier.size(16.dp))
            }
        }
    }
}

// Suggestion bar
@Composable
fun SuggestionBar(
    suggestions: List<String>,
    onApply: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        LazyRow(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(suggestions) { suggestion ->
                AssistChip(
                    onClick = { onApply(suggestion) },
                    label = { Text(suggestion, fontFamily = FontFamily.Monospace) }
                )
            }
            item {
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss suggestions", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// Tab bar
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
