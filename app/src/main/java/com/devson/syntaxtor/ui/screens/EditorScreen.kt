package com.devson.syntaxtor.ui.screens

import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.devson.syntaxtor.domain.model.EditorFile
import com.devson.syntaxtor.navigation.Screen
import com.devson.syntaxtor.ui.components.HistoryBottomSheet
import com.devson.syntaxtor.ui.preview.PreviewPlaceholder
import com.devson.syntaxtor.ui.utils.formatAsFileName
import com.devson.syntaxtor.viewmodel.EditorUiState
import com.devson.syntaxtor.viewmodel.EditorViewModel
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.subscribeEvent

// Root Screen

@Composable
fun EditorScreen(
    navController: NavController,
    viewModel: EditorViewModel,
    onOpenFileSelection: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    var isPreviewVisible by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    //  BackHandler: auto-save before leaving 
    val readyState = uiState as? EditorUiState.Ready
    val currentFile = readyState?.openFiles?.getOrNull(readyState.selectedFileIndex)
    BackHandler(enabled = currentFile?.isModified == true) {
        viewModel.autoSaveAndPop {
            navController.popBackStack()
        }
    }

    // Collect global snackbar messages
    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Auto navigate back to HomeScreen when there are no open files
    val openFilesCount = (uiState as? EditorUiState.Ready)?.openFiles?.size ?: 0
    LaunchedEffect(openFilesCount, uiState) {
        if (uiState is EditorUiState.Idle || (uiState is EditorUiState.Ready && openFilesCount == 0)) {
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Home.route) { inclusive = true }
            }
        }
    }

    //  History bottom sheet 
    if (readyState?.showHistorySheet == true) {
        HistoryBottomSheet(
            entries = readyState.historyEntries,
            onRestore = { id -> viewModel.restoreVersion(id) },
            onDismiss = { viewModel.dismissHistorySheet() },
        )
    }

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
                onRedo = { viewModel.redo() },
                onShowHistory = { viewModel.toggleHistorySheet() },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0.dp),
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            when (val state = uiState) {
                is EditorUiState.Idle -> CenterText("No file opened.\nTap ⋮ → Open File to begin.")
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
                            isPreviewVisible = isPreviewVisible,
                        )
                    }
                }
            }

            //  "Saving…" banner (slides in from top of content area) 
            AnimatedVisibility(
                visible = (uiState as? EditorUiState.Ready)?.isSaving == true,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Saving…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}

// Top App Bar - sleek M3 CenterAlignedTopAppBar with overflow menu

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
    onRedo: () -> Unit,
    onShowHistory: () -> Unit,
) {
    val readyState = uiState as? EditorUiState.Ready
    val file = readyState?.openFiles?.getOrNull(readyState.selectedFileIndex)
    // Display only formatted filename in the top bar. If none, show empty.
    val title = file?.let { it.name.formatAsFileName() + if (it.isModified) " •" else "" } ?: ""
    val showPreview = file?.fileType?.let { it == ".html" || it == ".htm" } ?: false
    var overflowExpanded by remember { mutableStateOf(false) }

    CenterAlignedTopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        //  Primary actions (always visible) 
        actions = {
            IconButton(onClick = onUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = onRedo) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
            }
            IconButton(onClick = onSave) {
                Icon(Icons.Default.Save, contentDescription = "Save")
            }

            //  Overflow menu (secondary actions) 
            Box {
                IconButton(onClick = { overflowExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(
                    expanded = overflowExpanded,
                    onDismissRequest = { overflowExpanded = false },
                ) {
                    // Search
                    DropdownMenuItem(
                        text = { Text("Find in file") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search, null,
                                tint = if (readyState?.isSearchVisible == true)
                                    MaterialTheme.colorScheme.primary
                                else
                                    LocalContentColor.current,
                            )
                        },
                        onClick = { overflowExpanded = false; onToggleSearch() },
                    )
                    // Word wrap
                    DropdownMenuItem(
                        text = { Text("Word wrap") },
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.WrapText, null,
                                tint = if (readyState?.wordWrapEnabled == true)
                                    MaterialTheme.colorScheme.primary
                                else
                                    LocalContentColor.current,
                            )
                        },
                        trailingIcon = {
                            if (readyState?.wordWrapEnabled == true)
                                Icon(Icons.Default.Check, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp))
                        },
                        onClick = { overflowExpanded = false; onWordWrapToggle() },
                    )
                    // HTML preview (only shown for .html / .htm)
                    if (showPreview) {
                        DropdownMenuItem(
                            text = { Text(if (isPreviewVisible) "Hide preview" else "Show preview") },
                            leadingIcon = {
                                Icon(
                                    if (isPreviewVisible) Icons.Default.VisibilityOff
                                    else Icons.Default.Visibility, null
                                )
                            },
                            onClick = { overflowExpanded = false; onPreviewToggle() },
                        )
                    }
                    HorizontalDivider()
                    // Show history sheet (only if enabled in settings)
                    DropdownMenuItem(
                        text = { Text("View history") },
                        leadingIcon = { Icon(Icons.Default.Restore, null) },
                        enabled = readyState?.isVersionHistoryEnabled == true,
                        onClick = { overflowExpanded = false; onShowHistory() },
                    )
                    HorizontalDivider()
                    // Open file
                    DropdownMenuItem(
                        text = { Text("Open file") },
                        leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                        onClick = { overflowExpanded = false; onOpenFile() },
                    )
                }
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface,
        ),
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
    isPreviewVisible: Boolean,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TabBar(
            files = state.openFiles,
            selectedIndex = state.selectedFileIndex,
            onTabSelected = onTabSelected,
            onTabClosed = onTabClosed,
        )

        AnimatedVisibility(
            visible = state.isSearchVisible,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            SearchBar(
                query = state.searchQuery,
                matchCount = state.searchMatchCount,
                currentMatchIndex = state.searchMatchIndex,
                onQueryChange = onSearchQueryChange,
                onNext = onNextMatch,
                onPrev = onPrevMatch,
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
                isSearchVisible = state.isSearchVisible,
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
    isSearchVisible: Boolean,
) {
    val backgroundColor = MaterialTheme.colorScheme.background.toArgb()
    val textColor       = MaterialTheme.colorScheme.onBackground.toArgb()
    val lineNumBgColor  = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    val lineNumColor    = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val cursorColor     = MaterialTheme.colorScheme.primary.toArgb()
    val selectionColor  = MaterialTheme.colorScheme.primaryContainer.toArgb()
    val matchColor      = MaterialTheme.colorScheme.tertiaryContainer.toArgb()

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val density = LocalDensity.current
    val navBarPaddingPx = with(density) { navBarPadding.roundToPx() }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            CodeEditor(context).apply {
                setText(file.content)
                isWordwrap = wordWrap

                // Monospace font at 14sp - IDE standard
                typefaceText = Typeface.MONOSPACE
                typefaceLineNumber = Typeface.MONOSPACE
                setTextSize(14f)

                applyColorScheme(
                    backgroundColor = backgroundColor,
                    textColor       = textColor,
                    lineNumBgColor  = lineNumBgColor,
                    lineNumColor    = lineNumColor,
                    cursorColor     = cursorColor,
                    selectionColor  = selectionColor,
                    matchColor      = matchColor,
                )

                setPadding(paddingLeft, paddingTop, paddingRight, navBarPaddingPx)

                subscribeEvent<ContentChangeEvent> { _, _ ->
                    viewModel.onContentChanged()
                }

                viewModel.registerEditorForFile(file.uri.toString(), this)
            }
        },
        update = { editor ->
            if (editor.isWordwrap != wordWrap) editor.isWordwrap = wordWrap

            editor.applyColorScheme(
                backgroundColor = backgroundColor,
                textColor       = textColor,
                lineNumBgColor  = lineNumBgColor,
                lineNumColor    = lineNumColor,
                cursorColor     = cursorColor,
                selectionColor  = selectionColor,
                matchColor      = matchColor,
            )

            editor.setPadding(editor.paddingLeft, editor.paddingTop, editor.paddingRight, navBarPaddingPx)

            if (isSearchVisible && searchQuery.isNotBlank()) {
                editor.searcher.search(searchQuery, EditorSearcher.SearchOptions(false, false))
            } else {
                editor.searcher.stopSearch()
            }
        },
    )

    DisposableEffect(file.uri) {
        onDispose { viewModel.registerEditorForFile(file.uri.toString(), null) }
    }
}

// ============================================================================
// Color scheme helper - maps Material3 tokens → Sora's EditorColorScheme
// ============================================================================

private fun CodeEditor.applyColorScheme(
    backgroundColor: Int,
    textColor: Int,
    lineNumBgColor: Int,
    lineNumColor: Int,
    cursorColor: Int,
    selectionColor: Int,
    matchColor: Int,
) {
    val scheme = colorScheme
    scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, backgroundColor)
    scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, lineNumBgColor)
    scheme.setColor(EditorColorScheme.LINE_DIVIDER, lineNumBgColor)
    scheme.setColor(EditorColorScheme.LINE_NUMBER, lineNumColor)
    scheme.setColor(EditorColorScheme.TEXT_NORMAL, textColor)
    scheme.setColor(EditorColorScheme.SELECTION_INSERT, cursorColor)
    scheme.setColor(EditorColorScheme.SELECTION_HANDLE, cursorColor)
    scheme.setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, selectionColor)
    scheme.setColor(EditorColorScheme.MATCHED_TEXT_BACKGROUND, matchColor)
    invalidate()
}

// ============================================================================
// Search Bar
// ============================================================================

@Composable
fun SearchBar(
    query: String,
    matchCount: Int,
    currentMatchIndex: Int,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
) {
    Surface(
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search,
                ),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            "Find in file…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                        )
                    }
                    inner()
                },
            )
            Spacer(Modifier.width(8.dp))
            if (matchCount > 0) {
                Text(
                    "${currentMatchIndex + 1}/$matchCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onPrev, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous match",
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next match",
                    modifier = Modifier.size(16.dp),
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
    onTabClosed: (Int) -> Unit,
) {
    if (files.isEmpty()) return
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = 0.dp,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        files.forEachIndexed { index, file ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onTabSelected(index) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = file.name.formatAsFileName() + if (file.isModified) " •" else "",
                            maxLines = 1,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close tab",
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { onTabClosed(index) },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }
}

// Helpers

@Composable
fun CenterText(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
