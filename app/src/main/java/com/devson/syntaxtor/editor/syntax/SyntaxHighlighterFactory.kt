package com.devson.syntaxtor.editor.syntax

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.devson.syntaxtor.editor.core.SyntaxHighlighter

object SyntaxHighlighterFactory {
    fun getHighlighter(extension: String): SyntaxHighlighter? {
        val ext = extension.replace(".", "").lowercase()
        return when (ext) {
            "json" -> JsonHighlighter()
            "html", "htm" -> HtmlHighlighter()
            "kt", "java" -> KotlinHighlighter()
            else -> null
        }
    }
}

class JsonHighlighter : SyntaxHighlighter {
    override val language = "json"
    private val keyRegex = "\"\\w+\"\\s*:".toRegex()
    private val stringRegex = "\".*?\"".toRegex()
    private val numberRegex = "\\b\\d+(\\.\\d+)?\\b".toRegex()
    private val booleanRegex = "\\b(true|false|null)\\b".toRegex()

    override fun highlight(text: String): AnnotatedString = buildAnnotatedString {
        append(text)
        stringRegex.findAll(text).forEach { addStyle(SpanStyle(color = Color(0xFF6A8759)), it.range.first, it.range.last + 1) }
        keyRegex.findAll(text).forEach { addStyle(SpanStyle(color = Color(0xFFCC7832)), it.range.first, it.range.last) }
        numberRegex.findAll(text).forEach { addStyle(SpanStyle(color = Color(0xFF6897BB)), it.range.first, it.range.last + 1) }
        booleanRegex.findAll(text).forEach { addStyle(SpanStyle(color = Color(0xFFCC7832)), it.range.first, it.range.last + 1) }
    }
}

class HtmlHighlighter : SyntaxHighlighter {
    override val language = "html"
    private val tagRegex = "</?.*?>".toRegex()
    
    override fun highlight(text: String): AnnotatedString = buildAnnotatedString {
        append(text)
        tagRegex.findAll(text).forEach { addStyle(SpanStyle(color = Color(0xFFE8BF6A)), it.range.first, it.range.last + 1) }
    }
}

class KotlinHighlighter : SyntaxHighlighter {
    override val language = "kotlin"
    private val keywords = "\\b(abstract|annotation|as|break|by|catch|class|companion|const|constructor|continue|crossinline|data|delegate|do|dynamic|else|enum|expect|external|false|final|finally|for|fun|get|if|import|in|infix|init|inline|inner|interface|internal|is|lateinit|noinline|null|object|open|operator|out|override|package|private|protected|public|reified|return|sealed|set|super|suspend|tailrec|this|throw|true|try|typealias|val|var|vararg|when|where|while)\\b".toRegex()
    
    override fun highlight(text: String): AnnotatedString = buildAnnotatedString {
        append(text)
        keywords.findAll(text).forEach { addStyle(SpanStyle(color = Color(0xFFCC7832)), it.range.first, it.range.last + 1) }
    }
}
