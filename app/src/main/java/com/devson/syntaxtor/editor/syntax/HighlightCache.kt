package com.devson.syntaxtor.editor.syntax

import androidx.compose.ui.text.AnnotatedString
import com.devson.syntaxtor.editor.core.SyntaxHighlighter

/**
 * Per-line highlight cache.
 *
 * Only invalidated for lines that actually changed, making partial re-highlighting
 * O(changed_lines) instead of O(document_size).
 */
class HighlightCache(private val highlighter: SyntaxHighlighter?) {

    private val cache = HashMap<Int, CachedEntry>(256)

    data class CachedEntry(val source: String, val annotated: AnnotatedString)

    fun getLine(lineIndex: Int, lineText: String): AnnotatedString {
        val entry = cache[lineIndex]
        if (entry != null && entry.source == lineText) return entry.annotated

        val annotated = if (highlighter != null) {
            highlighter.highlight(lineText)
        } else {
            AnnotatedString(lineText)
        }
        cache[lineIndex] = CachedEntry(lineText, annotated)
        return annotated
    }

    /**
     * Invalidate all cached entries at and after [fromLine].
     * Call this when lines are inserted or deleted.
     */
    fun invalidateFrom(fromLine: Int) {
        val keysToRemove = cache.keys.filter { it >= fromLine }
        keysToRemove.forEach { cache.remove(it) }
    }

    /** Full cache wipe (e.g., on file type change). */
    fun invalidateAll() = cache.clear()

    /** Current number of cached entries (for diagnostics). */
    fun size() = cache.size
}
