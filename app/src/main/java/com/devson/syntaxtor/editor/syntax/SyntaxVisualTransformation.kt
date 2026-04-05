package com.devson.syntaxtor.editor.syntax

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.devson.syntaxtor.editor.core.SyntaxHighlighter

class SyntaxVisualTransformation(private val highlighter: SyntaxHighlighter?) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = highlighter?.highlight(text.text) ?: AnnotatedString(text.text)
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}
