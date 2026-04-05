package com.devson.syntaxtor.editor.engine

data class SearchMatch(
    val line: Int,
    val startColumn: Int,
    val endColumn: Int
)

object SearchEngine {

    fun findAll(
        lines: List<String>,
        query: String,
        caseSensitive: Boolean = false,
        useRegex: Boolean = false
    ): List<SearchMatch> {
        if (query.isBlank()) return emptyList()
        val matches = mutableListOf<SearchMatch>()

        val regex = if (useRegex) {
            runCatching {
                if (caseSensitive) query.toRegex() else query.toRegex(RegexOption.IGNORE_CASE)
            }.getOrNull() ?: return emptyList()
        } else {
            val escaped = Regex.escape(query)
            if (caseSensitive) escaped.toRegex() else escaped.toRegex(RegexOption.IGNORE_CASE)
        }

        lines.forEachIndexed { lineIndex, lineText ->
            regex.findAll(lineText).forEach { result ->
                matches.add(
                    SearchMatch(
                        line = lineIndex,
                        startColumn = result.range.first,
                        endColumn = result.range.last + 1
                    )
                )
            }
        }
        return matches
    }

    /** Replace all occurrences and return new line list. */
    fun replaceAll(
        lines: List<String>,
        query: String,
        replacement: String,
        caseSensitive: Boolean = false
    ): List<String> {
        if (query.isBlank()) return lines
        val escaped = Regex.escape(query)
        val regex = if (caseSensitive) escaped.toRegex() else escaped.toRegex(RegexOption.IGNORE_CASE)
        return lines.map { it.replace(regex, replacement) }
    }
}
