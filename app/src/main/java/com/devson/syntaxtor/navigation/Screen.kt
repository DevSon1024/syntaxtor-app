package com.devson.syntaxtor.navigation

sealed class Screen(val route: String) {
    object Editor : Screen("editor")
    object Settings : Screen("settings")
}
