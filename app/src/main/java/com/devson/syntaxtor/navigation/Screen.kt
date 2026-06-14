package com.devson.syntaxtor.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Editor : Screen("editor")
    object Settings : Screen("settings")
    object Preview : Screen("preview/{fileUri}") {
        fun createRoute(fileUri: String): String {
            val encodedUri = java.net.URLEncoder.encode(fileUri, "UTF-8")
            return "preview/$encodedUri"
        }
    }
}
