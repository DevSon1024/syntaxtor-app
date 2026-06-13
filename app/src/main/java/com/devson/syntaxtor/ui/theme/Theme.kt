package com.devson.syntaxtor.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

//  Dark color scheme  (GitHub Dark) 
private val SyntaxtorDarkColorScheme = darkColorScheme(
    primary             = AccentBlueDark,
    onPrimary           = DarkBackground,
    primaryContainer    = Color(0xFF1C3A5E),
    onPrimaryContainer  = AccentBlueDark,

    secondary           = SlateDark,
    onSecondary         = DarkBackground,
    secondaryContainer  = Color(0xFF21262D),
    onSecondaryContainer= DarkOnSurface,

    tertiary            = TokenGreenDark,
    onTertiary          = DarkBackground,
    tertiaryContainer   = Color(0xFF0D3328),
    onTertiaryContainer = TokenGreenDark,

    background          = DarkBackground,
    onBackground        = DarkOnBackground,

    surface             = DarkSurface,
    onSurface           = DarkOnSurface,
    surfaceVariant      = DarkSurfaceVar,
    onSurfaceVariant    = DarkOnSurfaceVar,

    outline             = DarkOutline,
    outlineVariant      = DarkSurfaceVar,

    error               = Color(0xFFFF7B72),
    onError             = DarkBackground,
)

//  Light color scheme  (VS Code Light) 
private val SyntaxtorLightColorScheme = lightColorScheme(
    primary             = AccentBlueLight,
    onPrimary           = LightBackground,
    primaryContainer    = Color(0xFFDCEEFD),
    onPrimaryContainer  = AccentBlueLight,

    secondary           = SlateLight,
    onSecondary         = LightBackground,
    secondaryContainer  = LightSurfaceVar,
    onSecondaryContainer= LightOnSurface,

    tertiary            = TokenGreen,
    onTertiary          = LightBackground,
    tertiaryContainer   = Color(0xFFD4F4E8),
    onTertiaryContainer = TokenGreen,

    background          = LightBackground,
    onBackground        = LightOnBackground,

    surface             = LightSurface,
    onSurface           = LightOnSurface,
    surfaceVariant      = LightSurfaceVar,
    onSurfaceVariant    = LightOnSurfaceVar,

    outline             = LightOutline,
    outlineVariant      = LightSurfaceVar,

    error               = Color(0xFFCF222E),
    onError             = LightBackground,
)

@Composable
fun SyntaxtorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color disabled - we use a branded IDE palette.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) SyntaxtorDarkColorScheme else SyntaxtorLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}