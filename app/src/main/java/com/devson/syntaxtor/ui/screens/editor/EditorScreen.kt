package com.devson.syntaxtor.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.syntaxtor.domain.model.EditorFile
import com.devson.syntaxtor.preview.PreviewPlaceholder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onOpenFileSelection: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            EditorTopBar(
                uiState = uiState,
                onSave = { viewModel.saveCurrentFile() },
                onWordWrapToggle = { viewModel.toggleWordWrap() },
                onOpenFile = onOpenFileSelection
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
                            onTabSelected = { viewModel.selectTab(it) }
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
    onOpenFile: () -> Unit
) {
    var title = "Syntaxtor"
    if (uiState is EditorUiState.Ready && uiState.selectedFileIndex >= 0) {
        val file = uiState.openFiles[uiState.selectedFileIndex]
        title = file.name + if (file.isModified) "*" else ""
    }

    TopAppBar(
        title = { Text(title) },
        actions = {
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
    onTabSelected: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TabBar(
            files = state.openFiles,
            selectedIndex = state.selectedFileIndex,
            onTabSelected = onTabSelected
        )
        
        val currentFile = state.openFiles.getOrNull(state.selectedFileIndex)
        if (currentFile != null) {
            val scrollState = rememberScrollState()
            val textModifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .let {
                    if (!state.wordWrapEnabled) it.horizontalScroll(rememberScrollState()) else it
                }
            
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = currentFile.content,
                    onValueChange = onContentChange,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    modifier = textModifier.padding(8.dp),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                )
            }
            
            if (currentFile.fileType == ".html") {
                PreviewPlaceholder(content = currentFile.content)
            }
        }
    }
}

@Composable
fun TabBar(
    files: List<EditorFile>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = 8.dp
    ) {
        files.forEachIndexed { index, file ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onTabSelected(index) },
                text = { Text(file.name + if (file.isModified) "*" else "") }
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
