package com.devson.syntaxtor.editor.suggestion

class WordSuggestionProvider(private val extension: String) : SuggestionProvider {
    override val language: String = extension

    private val jsonKeywords = listOf("true", "false", "null")
    private val kotlinKeywords = listOf(
        "abstract", "annotation", "as", "break", "by", "catch", "class", "companion", "const", "constructor", "continue",
        "crossinline", "data", "delegate", "do", "dynamic", "else", "enum", "expect", "external", "false", "final", "finally",
        "for", "fun", "get", "if", "import", "in", "infix", "init", "inline", "inner", "interface", "internal", "is", "lateinit",
        "noinline", "null", "object", "open", "operator", "out", "override", "package", "private", "protected", "public",
        "reified", "return", "sealed", "set", "super", "suspend", "tailrec", "this", "throw", "true", "try", "typealias",
        "val", "var", "vararg", "when", "where", "while"
    )
    private val htmlKeywords = listOf(
        "html", "head", "body", "div", "span", "a", "p", "script", "style", "h1", "h2", "h3", "h4", "h5", "h6",
        "img", "ul", "ol", "li", "table", "tr", "td", "th", "br", "hr", "link", "meta", "title", "nav", "header",
        "footer", "main", "section", "article", "aside", "form", "input", "button", "select", "option", "textarea"
    )

    private val keywords = when (extension.replace(".", "").lowercase()) {
        "json" -> jsonKeywords
        "kt", "java" -> kotlinKeywords
        "html", "htm" -> htmlKeywords
        else -> emptyList()
    }

    override suspend fun getSuggestions(wordPrefix: String): List<String> {
        if (wordPrefix.isBlank() || keywords.isEmpty()) return emptyList()
        val lowerPrefix = wordPrefix.lowercase()
        return keywords.filter { it.startsWith(lowerPrefix) && it.length > lowerPrefix.length }
    }
}
