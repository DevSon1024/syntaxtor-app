package com.devson.syntaxtor.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.devson.syntaxtor.ui.screens.editor.EditorScreen
import com.devson.syntaxtor.ui.screens.editor.EditorViewModel

@Composable
fun NavGraph(
    navController: NavHostController,
    editorViewModel: EditorViewModel,
    modifier: Modifier = Modifier,
    onOpenFileSelection: () -> Unit,
    startDestination: String = Screen.Editor.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Editor.route) {
            EditorScreen(
                viewModel = editorViewModel,
                onOpenFileSelection = onOpenFileSelection
            )
        }
        
        composable(Screen.Settings.route) {
            // Settings screen placeholder
        }
    }
}
