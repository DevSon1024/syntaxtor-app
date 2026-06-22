package com.devson.syntaxtor.ui.screens

import com.devson.syntaxtor.ui.components.MarkdownPreviewPanel

import androidx.compose.foundation.shape.CircleShape
import com.devson.syntaxtor.ui.utils.repeatingClickable

import android.content.res.Configuration
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.app.Activity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.devson.syntaxtor.domain.model.EditorFile
import com.devson.syntaxtor.navigation.Screen
import com.devson.syntaxtor.ui.components.HistoryBottomSheet
import com.devson.syntaxtor.ui.utils.formatAsFileName
import com.devson.syntaxtor.viewmodel.EditorUiState
import com.devson.syntaxtor.viewmodel.EditorViewModel
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.subscribeEvent
import kotlinx.coroutines.launch

// Root Screen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    navController: NavController,
    viewModel: EditorViewModel,
    onOpenFileSelection: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Keyboard and bottom options island states
    var isIslandExpanded by remember { mutableStateOf(true) }
    val isImeVisible = WindowInsets.isImeVisible

    LaunchedEffect(isImeVisible) {
        isIslandExpanded = !isImeVisible
    }

    // Dynamic Immersive landscape mode
    val hideSystemBarsInLandscape by viewModel.hideSystemBarsInLandscape.collectAsState()
    val orientation = LocalConfiguration.current.orientation
    val view = LocalView.current
    val isZenModeEnabled by viewModel.zenModeEnabled.collectAsState()
    val isZenModeActive = isZenModeEnabled && isImeVisible

    LaunchedEffect(orientation, hideSystemBarsInLandscape) {
        val activity = view.context as? Activity ?: return@LaunchedEffect
        val window = activity.window
        val insetsController = WindowCompat.getInsetsController(window, view)
        
        // Ensure the app draws behind system bars smoothly
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape && hideSystemBarsInLandscape) {
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val readyState = uiState as? EditorUiState.Ready
    val currentFile = readyState?.openFiles?.getOrNull(readyState.selectedFileIndex)

    // Unsaved changes back logic
    val showFileExtensions by viewModel.showFileExtensions.collectAsState()
    val handleBack: () -> Unit = {
        if (currentFile?.isModified == true) {
            viewModel.requestCloseFile(currentFile.uri.toString())
        } else {
            navController.popBackStack(Screen.Home.route, false)
            Unit
        }
    }

    //  BackHandler
    BackHandler(enabled = true) {
        handleBack()
    }

    // Tab Full Name Overlay State
    var overlayText by remember { mutableStateOf<String?>(null) }
    val overlayDuration by viewModel.overlayDuration.collectAsState()

    LaunchedEffect(overlayText) {
        if (overlayText != null) {
            kotlinx.coroutines.delay((overlayDuration * 1000).toLong())
            overlayText = null
        }
    }

    // Collect global snackbar messages
    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Auto navigate back to HomeScreen when there are no open files
    val openFilesCount = readyState?.openFiles?.size ?: 0
    LaunchedEffect(openFilesCount, uiState) {
        if (uiState is EditorUiState.Idle || (uiState is EditorUiState.Ready && openFilesCount == 0)) {
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Home.route) { inclusive = true }
            }
        }
    }

    // Unsaved Changes Confirmation Dialog
    val filePendingClose = readyState?.filePendingClose
    if (filePendingClose != null) {
        if (filePendingClose.uri.scheme == "syntaxtor") {
            AlertDialog(
                onDismissRequest = { viewModel.cancelCloseFile() },
                title = { Text("Unsaved Changes") },
                text = { Text("Do you want to save \"${filePendingClose.name}\" before closing?") },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.triggerSaveDialog(filePendingClose.uri.toString(), closeAfterSave = true) }
                    ) {
                        Text("Save & Close")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.discardAndCloseFile(filePendingClose.uri.toString()) }
                    ) {
                        Text("Discard", color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { viewModel.cancelCloseFile() },
                title = { Text("Unsaved Changes") },
                text = { Text("Do you want to save changes to \"${filePendingClose.name.formatAsFileName(showFileExtensions)}\" before closing?") },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.saveAndCloseFile(filePendingClose.uri.toString()) }
                    ) {
                        Text("Save & Close")
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = { viewModel.discardAndCloseFile(filePendingClose.uri.toString()) }
                        ) {
                            Text("Close Without Saving", color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = { viewModel.cancelCloseFile() }
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            )
        }
    }

    // Custom Save Dialog for New Files
    val showSaveDialogForFile = readyState?.showSaveDialogForFile
    if (showSaveDialogForFile != null) {
        var fileName by remember { mutableStateOf("") }
        var extension by remember { mutableStateOf(".txt") }

        AlertDialog(
            onDismissRequest = { viewModel.cancelSaveDialog() },
            title = { Text("Save File") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        label = { Text("File Name") },
                        placeholder = { Text("SynDoc") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = extension,
                        onValueChange = { extension = it },
                        label = { Text("Extension") },
                        placeholder = { Text(".txt") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveNewFile(
                            uriString = showSaveDialogForFile.uri.toString(),
                            nameInput = fileName,
                            extensionInput = extension,
                            closeAfterSave = readyState.closeAfterSave
                        )
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.cancelSaveDialog() }
                ) {
                    Text("Cancel")
                }
            }
        )
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0.dp),
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            AnimatedVisibility(
                visible = !isZenModeActive,
                enter = slideInVertically(animationSpec = tween(300)) { -it } + fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
                exit = slideOutVertically(animationSpec = tween(300)) { -it } + fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300))
            ) {
                EditorTopBar(
                    uiState = uiState,
                    showFileExtensions = showFileExtensions,
                    onSave = { viewModel.saveCurrentFile() },
                    onOpenFile = onOpenFileSelection,
                    onUndo = { viewModel.undo() },
                    onRedo = { viewModel.redo() },
                    onBackClick = handleBack,
                    onTabSelected = { index ->
                        viewModel.selectTab(index)
                        readyState?.openFiles?.getOrNull(index)?.let { file ->
                            overlayText = file.name
                        }
                    },
                    onTabClosed = { index ->
                        readyState?.openFiles?.getOrNull(index)?.let { file ->
                            viewModel.requestCloseFile(file.uri.toString())
                        }
                    },
                    isMarkdownPreviewVisible = readyState?.isMarkdownPreviewVisible == true,
                    onPreviewToggle = { viewModel.toggleMarkdownPreview() }
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
            val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            when (val state = uiState) {
                is EditorUiState.Idle -> CenterText("No file opened.\nTap Open File button to begin.")
                is EditorUiState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
                is EditorUiState.Error -> CenterText("Error: ${state.message}")
                is EditorUiState.Ready -> {
                    if (state.openFiles.isEmpty()) {
                        CenterText("No file opened.")
                    } else if (isLandscape && state.openFiles.size >= 2 && !state.isMarkdownPreviewVisible) {
                        SplitPaneContent(
                            state = state,
                            viewModel = viewModel,
                            showFileExtensions = showFileExtensions,
                            onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                            onNextMatch = { viewModel.nextSearchMatch() },
                            onPrevMatch = { viewModel.previousSearchMatch() },
                        )
                    } else {
                        EditorContent(
                            state = state,
                            viewModel = viewModel,
                            onTabSelected = { index ->
                                viewModel.selectTab(index)
                                state.openFiles.getOrNull(index)?.let { file ->
                                    overlayText = file.name
                                }
                            },
                            onTabClosed = { index ->
                                state.openFiles.getOrNull(index)?.let { file ->
                                    viewModel.requestCloseFile(file.uri.toString())
                                }
                            },
                            onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                            onNextMatch = { viewModel.nextSearchMatch() },
                            onPrevMatch = { viewModel.previousSearchMatch() },
                        )
                    }
                }
            }

            // Tab Full Name Overlay
            androidx.compose.animation.AnimatedVisibility(
                visible = overlayText != null,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    tonalElevation = 6.dp,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Text(
                        text = overlayText ?: "",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Bottom Options Island
            androidx.compose.animation.AnimatedVisibility(
                visible = !isZenModeActive && readyState != null && readyState.openFiles.isNotEmpty(),
                enter = slideInVertically(animationSpec = tween(300)) { it } + fadeIn(animationSpec = tween(300)),
                exit = slideOutVertically(animationSpec = tween(300)) { it } + fadeOut(animationSpec = tween(300)),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.ime)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 16.dp)
            ) {
                Box(
                    contentAlignment = if (isIslandExpanded) Alignment.Center else Alignment.CenterEnd
                ) {
                    AnimatedContent(
                        targetState = isIslandExpanded,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                        },
                        label = "BottomIslandTransition"
                    ) { expanded ->
                        if (expanded) {
                            Surface(
                                shape = RoundedCornerShape(percent = 50),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                tonalElevation = 8.dp,
                                shadowElevation = 6.dp,
                                modifier = Modifier.wrapContentSize()
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    IconButton(onClick = onOpenFileSelection) {
                                        Icon(Icons.Default.FolderOpen, contentDescription = "Open File")
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    IconButton(onClick = { viewModel.toggleSearch() }) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Find in File",
                                            tint = if (readyState?.isSearchVisible == true) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    IconButton(onClick = { viewModel.toggleWordWrap() }) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.WrapText,
                                            contentDescription = "Word Wrap",
                                            tint = if (readyState?.wordWrapEnabled == true) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    IconButton(
                                        onClick = { viewModel.toggleHistorySheet() },
                                        enabled = readyState?.isVersionHistoryEnabled == true
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = "View History"
                                        )
                                    }
                                    if (isImeVisible) {
                                        Spacer(modifier = Modifier.width(12.dp))
                                        IconButton(onClick = { isIslandExpanded = false }) {
                                            Icon(
                                                imageVector = Icons.Default.ChevronRight,
                                                contentDescription = "Collapse Tools"
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Surface(
                                onClick = { isIslandExpanded = true },
                                shape = RoundedCornerShape(percent = 50),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                tonalElevation = 8.dp,
                                shadowElevation = 6.dp,
                                modifier = Modifier
                                    .padding(end = 16.dp)
                                    .size(48.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronLeft,
                                        contentDescription = "Expand Tools",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }

            //  "Saving…" banner (slides in from top of content area) 
            androidx.compose.animation.AnimatedVisibility(
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
}

// Top App Bar - sleek M3 CenterAlignedTopAppBar with overflow menu

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTopBar(
    uiState: EditorUiState,
    showFileExtensions: Boolean,
    onSave: () -> Unit,
    onOpenFile: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onBackClick: () -> Unit,
    onTabSelected: (Int) -> Unit,
    onTabClosed: (Int) -> Unit,
    isMarkdownPreviewVisible: Boolean = false,
    onPreviewToggle: () -> Unit = {},
) {
    val readyState = uiState as? EditorUiState.Ready
    val files = readyState?.openFiles ?: emptyList()
    val selectedIndex = readyState?.selectedFileIndex ?: -1
    val file = readyState?.openFiles?.getOrNull(selectedIndex)
    val isMarkdownFile = file?.name?.endsWith(".md", ignoreCase = true) == true

    Column {
        CenterAlignedTopAppBar(
            title = { }, // Title is empty; file names are now managed in tabs below
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            //  Primary actions (always visible) 
            actions = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .repeatingClickable { onUndo() }
                        .padding(12.dp)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Redo",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .repeatingClickable { onRedo() }
                        .padding(12.dp)
                )
                IconButton(onClick = onSave) {
                    Icon(Icons.Default.Save, contentDescription = "Save")
                }

                if (isMarkdownFile) {
                    IconButton(onClick = onPreviewToggle) {
                        Icon(
                            imageVector = if (isMarkdownPreviewVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle Preview",
                            tint = if (isMarkdownPreviewVisible) MaterialTheme.colorScheme.primary else LocalContentColor.current
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

        // Custom Tab chip row situated directly below the top app bar
        val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (files.isNotEmpty() && !isLandscape) {
            TabBar(
                files = files,
                selectedIndex = selectedIndex,
                showFileExtensions = showFileExtensions,
                onTabSelected = onTabSelected,
                onTabClosed = onTabClosed,
            )
        }
    }
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
) {
    val liveMarkdownText by viewModel.liveMarkdownText.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
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

        val bringIntoViewRequester = remember { BringIntoViewRequester() }
        val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .imePadding()
                .bringIntoViewRequester(bringIntoViewRequester)
        ) {
            if (isLandscape && state.isMarkdownPreviewVisible) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        key(currentFile.uri) {
                            SoraCodeEditor(
                                file = currentFile,
                                viewModel = viewModel,
                                wordWrap = state.wordWrapEnabled,
                                searchQuery = state.searchQuery,
                                isSearchVisible = state.isSearchVisible,
                                bringIntoViewRequester = bringIntoViewRequester,
                            )
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        MarkdownPreviewPanel(
                            rawMarkdown = liveMarkdownText,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else {
                key(currentFile.uri) {
                    SoraCodeEditor(
                        file = currentFile,
                        viewModel = viewModel,
                        wordWrap = state.wordWrapEnabled,
                        searchQuery = state.searchQuery,
                        isSearchVisible = state.isSearchVisible,
                        bringIntoViewRequester = bringIntoViewRequester,
                    )
                }

                if (state.isMarkdownPreviewVisible) {
                    MarkdownPreviewPanel(
                        rawMarkdown = liveMarkdownText,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

// Sora CodeEditor via AndroidView

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SoraCodeEditor(
    file: EditorFile,
    viewModel: EditorViewModel,
    wordWrap: Boolean,
    searchQuery: String,
    isSearchVisible: Boolean,
    bringIntoViewRequester: BringIntoViewRequester? = null,
) {
    val backgroundColor = MaterialTheme.colorScheme.background.toArgb()
    val textColor       = MaterialTheme.colorScheme.onBackground.toArgb()
    val lineNumBgColor  = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    val lineNumColor    = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val cursorColor     = MaterialTheme.colorScheme.primary.toArgb()
    val selectionColor  = MaterialTheme.colorScheme.primaryContainer.toArgb()
    val matchColor      = MaterialTheme.colorScheme.tertiaryContainer.toArgb()

    val isImeVisible = WindowInsets.isImeVisible
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val density = LocalDensity.current
    // Add extra padding so keyboard or bottom options island doesn't block text
    val extraBottomPadding = if (isImeVisible) 76.dp else (navBarPadding + 76.dp)
    val extraBottomPaddingPx = with(density) { extraBottomPadding.roundToPx() }
    val coroutineScope = rememberCoroutineScope()

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

                setPadding(paddingLeft, paddingTop, paddingRight, extraBottomPaddingPx)

                subscribeEvent<ContentChangeEvent> { _, _ ->
                    viewModel.onContentChanged()
                    viewModel.updateLiveText(text.toString())
                }

                subscribeEvent<SelectionChangeEvent> { _, _ ->
                    val line = cursor.leftLine
                    val column = cursor.leftColumn
                    ensurePositionVisible(line, column)
                    if (bringIntoViewRequester != null) {
                        val paint = textPaint
                        val fontMetrics = paint.fontMetrics
                        val lineHeight = fontMetrics.descent - fontMetrics.ascent
                        val cursorY = line * lineHeight
                        val localY = cursorY - scrollY
                        coroutineScope.launch {
                            bringIntoViewRequester.bringIntoView(
                                rect = androidx.compose.ui.geometry.Rect(
                                    left = 0f,
                                    top = localY,
                                    right = 0f,
                                    bottom = localY + lineHeight
                                )
                            )
                        }
                    }
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

            editor.setPadding(editor.paddingLeft, editor.paddingTop, editor.paddingRight, extraBottomPaddingPx)

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

// Color scheme helper - maps Material3 tokens → Sora's EditorColorScheme

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

// Search Bar

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
    showFileExtensions: Boolean,
    onTabSelected: (Int) -> Unit,
    onTabClosed: (Int) -> Unit,
) {
    if (files.isEmpty()) return
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = 16.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        divider = {}, // Floating tabs without bottom line
        indicator = {} // Indicator is handled by selecting background
    ) {
        files.forEachIndexed { index, file ->
            val isSelected = selectedIndex == index
            Tab(
                selected = isSelected,
                onClick = { onTabSelected(index) },
                modifier = Modifier
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = file.name.formatAsFileName(showFileExtensions) + if (file.isModified) " •" else "",
                            maxLines = 1,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close tab",
                            modifier = Modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onTabClosed(index) },
                            tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
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
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

//  Split-Pane (Landscape) 

/**
 * Renders two side-by-side editor panes when the device is in landscape and
 * at least two files are open.  Each pane has:
 *  - A [PaneSelectorChip] to pick which open file to display.
 *  - An independent [SoraCodeEditor] for that file.
 * Tapping a pane selector makes that file the "active" one for save/undo.
 */
@Composable
fun SplitPaneContent(
    state: EditorUiState.Ready,
    viewModel: EditorViewModel,
    showFileExtensions: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onNextMatch: () -> Unit,
    onPrevMatch: () -> Unit,
) {
    val leftFile  = state.openFiles.getOrNull(state.splitLeftIndex)  ?: state.openFiles.first()
    val rightFile = state.openFiles.getOrNull(state.splitRightIndex) ?: state.openFiles.getOrNull(1) ?: leftFile

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar shared across both panes
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

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            //  Left pane 
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                PaneSelectorChip(
                    files = state.openFiles,
                    selectedIndex = state.splitLeftIndex,
                    isActive = state.selectedFileIndex == state.splitLeftIndex,
                    showFileExtensions = showFileExtensions,
                    onSelect = { index -> viewModel.setSplitLeft(index) },
                )
                val leftBringIntoViewRequester = remember { BringIntoViewRequester() }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .imePadding()
                        .bringIntoViewRequester(leftBringIntoViewRequester)
                ) {
                    key(leftFile.uri) {
                        SoraCodeEditor(
                            file = leftFile,
                            viewModel = viewModel,
                            wordWrap = state.wordWrapEnabled,
                            searchQuery = state.searchQuery,
                            isSearchVisible = state.isSearchVisible,
                            bringIntoViewRequester = leftBringIntoViewRequester,
                        )
                    }
                }
            }

            //  Divider 
            VerticalDivider(
                modifier = Modifier.fillMaxHeight(),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            //  Right pane 
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                PaneSelectorChip(
                    files = state.openFiles,
                    selectedIndex = state.splitRightIndex,
                    isActive = state.selectedFileIndex == state.splitRightIndex,
                    showFileExtensions = showFileExtensions,
                    onSelect = { index -> viewModel.setSplitRight(index) },
                )
                val rightBringIntoViewRequester = remember { BringIntoViewRequester() }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .imePadding()
                        .bringIntoViewRequester(rightBringIntoViewRequester)
                ) {
                    key(rightFile.uri) {
                        SoraCodeEditor(
                            file = rightFile,
                            viewModel = viewModel,
                            wordWrap = state.wordWrapEnabled,
                            searchQuery = state.searchQuery,
                            isSearchVisible = state.isSearchVisible,
                            bringIntoViewRequester = rightBringIntoViewRequester,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PaneSelectorChip(
    files: List<EditorFile>,
    selectedIndex: Int,
    isActive: Boolean,
    showFileExtensions: Boolean,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val file = files.getOrNull(selectedIndex)

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            color = if (isActive) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = if (isActive) 4.dp else 1.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = file?.name?.formatAsFileName(showFileExtensions)?.let {
                        it + if (file.isModified) " •" else ""
                    } ?: "—",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    ),
                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.UnfoldMore,
                    contentDescription = "Switch file in pane",
                    modifier = Modifier.size(16.dp),
                    tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            files.forEachIndexed { index, f ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = f.name.formatAsFileName(showFileExtensions) + if (f.isModified) " •" else "",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                        )
                    },
                    leadingIcon = if (index == selectedIndex) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    onClick = {
                        onSelect(index)
                        expanded = false
                    },
                )
            }
        }
    }
}
