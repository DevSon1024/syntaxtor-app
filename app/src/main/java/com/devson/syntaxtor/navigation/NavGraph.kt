package com.devson.syntaxtor.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.devson.syntaxtor.ui.screens.EditorScreen
import com.devson.syntaxtor.ui.screens.HomeScreen
import com.devson.syntaxtor.ui.screens.SettingsScreen
import com.devson.syntaxtor.viewmodel.EditorViewModel
import com.devson.syntaxtor.viewmodel.HomeViewModel
import com.devson.syntaxtor.viewmodel.SettingsViewModel

@Composable
fun NavGraph(
    navController: NavHostController,
    editorViewModel: EditorViewModel,
    homeViewModel: HomeViewModel,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    onOpenFileSelection: () -> Unit,
    startDestination: String = Screen.Home.route,
) {
    // Collect global editor navigation events
    LaunchedEffect(editorViewModel) {
        editorViewModel.navigationEvent.collect { event ->
            when (event) {
                is EditorViewModel.NavigationEvent.NavigateToEditor -> {
                    navController.navigate(Screen.Editor.route) {
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                navController = navController,
                homeViewModel = homeViewModel,
                editorViewModel = editorViewModel,
                onOpenFileSelection = onOpenFileSelection
            )
        }

        composable(Screen.Editor.route) {
            EditorScreen(
                navController = navController,
                viewModel = editorViewModel,
                onOpenFileSelection = onOpenFileSelection,
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                navController = navController,
                viewModel = settingsViewModel
            )
        }
    }
}
