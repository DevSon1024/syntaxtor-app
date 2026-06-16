package com.devson.syntaxtor.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.devson.syntaxtor.ui.preview.HtmlPreviewWebView
import com.devson.syntaxtor.ui.utils.formatAsFileName
import com.devson.syntaxtor.viewmodel.EditorUiState
import com.devson.syntaxtor.viewmodel.EditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HtmlPreviewScreen(
    navController: NavController,
    viewModel: EditorViewModel,
    fileUri: String
) {
    val uiState by viewModel.uiState.collectAsState()
    val showFileExtensions by viewModel.showFileExtensions.collectAsState()
    val readyState = uiState as? EditorUiState.Ready
    val file = readyState?.openFiles?.find { it.uri.toString() == fileUri }
    val fileName = file?.name ?: fileUri.substringAfterLast('/').substringAfterLast(':')
    val htmlContent = viewModel.getLiveContent(fileUri)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = fileName.formatAsFileName(showFileExtensions),
                        maxLines = 1,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0.dp)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            HtmlPreviewWebView(
                htmlContent = htmlContent,
                fileUri = fileUri,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
