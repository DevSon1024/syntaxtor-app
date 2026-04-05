package com.devson.syntaxtor.editor.core

import androidx.compose.ui.text.AnnotatedString

interface SyntaxHighlighter {
    val language: String
    fun highlight(text: String): AnnotatedString
}
