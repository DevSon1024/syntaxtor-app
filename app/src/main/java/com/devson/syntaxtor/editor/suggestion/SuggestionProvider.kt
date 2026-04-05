package com.devson.syntaxtor.editor.suggestion

interface SuggestionProvider {
    val language: String
    suspend fun getSuggestions(wordPrefix: String): List<String>
}
