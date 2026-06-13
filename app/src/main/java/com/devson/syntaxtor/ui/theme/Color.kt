package com.devson.syntaxtor.ui.theme

import androidx.compose.ui.graphics.Color

//  Brand palette 

// Accent - Azure-inspired blue (VS Code / GitHub Actions feel)
val AccentBlue        = Color(0xFF0078D4)
val AccentBlueDark    = Color(0xFF4DA3FF)   // softer on dark backgrounds
val AccentBlueLight   = Color(0xFF005A9E)   // deeper on light backgrounds

// Secondary - Muted slate
val SlateLight        = Color(0xFF6B7A8D)
val SlateDark         = Color(0xFF8D9BAA)

// Tertiary - Editor-token green (like VS Code string literals)
val TokenGreen        = Color(0xFF4EC994)
val TokenGreenDark    = Color(0xFF89D9B4)

//  Dark theme  (GitHub Dark inspired: #0D1117 base) 

val DarkBackground    = Color(0xFF0D1117)   // editor canvas
val DarkSurface       = Color(0xFF161B22)   // cards, sheets, dialogs
val DarkSurfaceVar    = Color(0xFF21262D)   // gutter, tab bar, dividers
val DarkOnBackground  = Color(0xFFE6EDF3)   // primary text
val DarkOnSurface     = Color(0xFFCDD9E5)   // secondary text
val DarkOnSurfaceVar  = Color(0xFF768390)   // hints, line numbers
val DarkOutline       = Color(0xFF30363D)   // borders / dividers

//  Light theme  (VS Code Light inspired) 

val LightBackground   = Color(0xFFFFFFFF)
val LightSurface      = Color(0xFFF3F3F3)
val LightSurfaceVar   = Color(0xFFE8E8E8)   // gutter background
val LightOnBackground = Color(0xFF1F1F1F)
val LightOnSurface    = Color(0xFF3B3B3B)
val LightOnSurfaceVar = Color(0xFF737373)   // line numbers / hints
val LightOutline      = Color(0xFFCCCCCC)

//  Kept for any remaining code that references the old names 
val Purple80          = Color(0xFFD0BCFF)
val PurpleGrey80      = Color(0xFFCCC2DC)
val Pink80            = Color(0xFFEFB8C8)
val Purple40          = Color(0xFF6650A4)
val PurpleGrey40      = Color(0xFF625B71)
val Pink40            = Color(0xFF7D5260)