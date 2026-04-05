package com.devson.syntaxtor.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.devson.syntaxtor.domain.model.EditorFile
import com.devson.syntaxtor.preview.PreviewPlaceholder
import com.devson.syntaxtor.editor.syntax.SyntaxHighlighterFactory
import com.devson.syntaxtor.editor.syntax.SyntaxVisualTransformation
import com.devson.syntaxtor.editor.suggestion.WordSuggestionProvider
import com.google.mlkit.vision.segmentation.subject.Subject

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
                onPreviewToggle = { isPreviewVisible = !isPreviewVisible }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (val state = uiState) {
                is EditorUiState.Idle -> {
                    CenterText("No file opened. Open one from the menu.")
                }
                is EditorUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
                }
                is EditorUiState.Error -> {
                    CenterText("Error: ${state.message}")
                }
                is EditorUiState.Ready -> {
                    if (state.openFiles.isEmpty()) {
                        CenterText("No file opened.")
                    } else {
                        EditorContent(
                            state = state,
                            onContentChange = { viewModel.updateContent(it) },
                            onTabSelected = { viewModel.selectTab(it) },
                            onTabClosed = { viewModel.closeTab(it) },
                            isPreviewVisible = isPreviewVisible
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTopBar(
    uiState: EditorUiState,
    onSave: () -> Unit,
    onWordWrapToggle: () -> Unit,
    onOpenFile: () -> Unit,
    isPreviewVisible: Boolean,
    onPreviewToggle: () -> Unit
) {
    var title = "Syntaxtor"
    var showPreviewToggle = false

    if (uiState is EditorUiState.Ready && uiState.selectedFileIndex >= 0) {
        val file = uiState.openFiles.getOrNull(uiState.selectedFileIndex)
        if (file != null) {
            title = file.name + if (file.isModified) "*" else ""
            showPreviewToggle = file.fileType == ".html" || file.fileType == ".htm"
        }
    }

    TopAppBar(
        title = { Text(title) },
        actions = {
            if (showPreviewToggle) {
                IconButton(onClick = onPreviewToggle) {
                    Icon(
                        imageVector = if (isPreviewVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle HTML Preview"
                    )
                }
            }
            IconButton(onClick = onWordWrapToggle) {
                val isWrapEnabled = uiState is EditorUiState.Ready && uiState.wordWrapEnabled
                Icon(
                    imageVector = Icons.Default.Menu,
                    tint = if (isWrapEnabled) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    contentDescription = "Toggle word wrap"
                )
            }
            IconButton(onClick = onSave) {
                Icon(Icons.Default.Save, contentDescription = "Save file")
            }
            IconButton(onClick = onOpenFile) {
                Icon(Icons.Default.MoreVert, contentDescription = "Open file")
                // Simplified menu action for now
            }
        }
    )
}

@Composable
fun EditorContent(
    state: EditorUiState.Ready,
    onContentChange: (String) -> Unit,
    onTabSelected: (Int) -> Unit,
    onTabClosed: (Int) -> Unit,
    isPreviewVisible: Boolean
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TabBar(
            files = state.openFiles,
            selectedIndex = state.selectedFileIndex,
            onTabSelected = onTabSelected,
            onTabClosed = onTabClosed
        )
        
        val currentFile = state.openFiles.getOrNull(state.selectedFileIndex)
        if (currentFile != null) {
            var textFieldValue by remember(currentFile.uri) {
                mutableStateOf(TextFieldValue(currentFile.content))
            }
            LaunchedEffect(currentFile.content) {
                if (textFieldValue.text != currentFile.content) {
                    textFieldValue = textFieldValue.copy(text = currentFile.content)
                }
            }

            val highlighter = remember(currentFile.fileType) {
                SyntaxHighlighterFactory.getHighlighter(currentFile.fileType)
            }
            val visualTransformation = remember(highlighter) {
                SyntaxVisualTransformation(highlighter)
            }

            var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
            val suggestionProvider = remember(currentFile.fileType) {
                WordSuggestionProvider(currentFile.fileType)
            }
            
            LaunchedEffect(textFieldValue.selection, textFieldValue.text) {
                val cursor = textFieldValue.selection.start
                if (cursor > 0 && cursor <= textFieldValue.text.length) {
                    val textUntilCursor = textFieldValue.text.substring(0, cursor)
                    val lastWord = textUntilCursor.split(Regex("\\W+")).lastOrNull() ?: ""
                    if (lastWord.length > 1) {
                        suggestions = suggestionProvider.getSuggestions(lastWord)
                    } else {
                        suggestions = emptyList()
                    }
                } else {
                    suggestions = emptyList()
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                val scrollState = rememberScrollState()

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(scrollState)
                ) {
                    val lineCount = currentFile.content.count { it == '\n' } + 1
                    val lineNumbers = (1..lineCount).joinToString("\n")

                    Text(
                        text = lineNumbers,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End
                        ),
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    )

                    val textModifier = Modifier
                        .weight(1f)
                        .let {
                            if (!state.wordWrapEnabled) it.horizontalScroll(rememberScrollState()) else it
                        }

                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { newValue ->
                            textFieldValue = newValue
                            onContentChange(newValue.text)
                        },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        ),
                        modifier = textModifier.padding(8.dp),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        visualTransformation = visualTransformation
                    )
                }

                if (suggestions.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(androidx.compose.ui.Alignment.BottomCenter)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(8.dp)
                    ) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(suggestions) { suggestion ->
                                AssistChip(
                                    onClick = { 
                                        val cursor = textFieldValue.selection.start
                                        val text = textFieldValue.text
                                        val textUntilCursor = text.substring(0, cursor)
                                        val lastWord = textUntilCursor.split(Regex("\\W+")).lastOrNull() ?: ""
                                        
                                        val newText = text.substring(0, cursor - lastWord.length) + suggestion + text.substring(cursor)
                                        val newCursor = cursor - lastWord.length + suggestion.length
                                        
                                        textFieldValue = TextFieldValue(newText, TextRange(newCursor))
                                        onContentChange(newText)
                                        suggestions = emptyList()
                                    },
                                    label = { Text(suggestion) }
                                )
                            }
                        }
                    }
                }
            }
            
            if (currentFile.fileType == ".html" && isPreviewVisible) {
                PreviewPlaceholder(content = currentFile.content)
            }
        }
    }
}

@Composable
fun TabBar(
    files: List<EditorFile>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    onTabClosed: (Int) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = 8.dp
    ) {
        files.forEachIndexed { index, file ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onTabSelected(index) },
                text = { 
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(file.name + if (file.isModified) "*" else "")
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close tab",
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onTabClosed(index) },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun CenterText(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text(text = text, color = MaterialTheme.colorScheme.onBackground)
    }
}
