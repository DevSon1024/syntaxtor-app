package com.devson.syntaxtor.editor.engine

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

data class CursorPosition(val line: Int = 0, val column: Int = 0)

sealed class EditAction {
    data class Insert(val line: Int, val column: Int, val text: String) : EditAction()
    data class Delete(val line: Int, val column: Int, val text: String) : EditAction()
    data class ReplaceLine(val line: Int, val oldText: String, val newText: String) : EditAction()
    data class BulkReplace(val oldLines: List<String>, val newLines: List<String>) : EditAction()
}
class EditorEngine {

    /** The live document state — Compose observes this directly. */
    val lines: SnapshotStateList<String> = mutableStateListOf("")

    private val undoStack = ArrayDeque<EditAction>(MAX_HISTORY)
    private val redoStack = ArrayDeque<EditAction>(MAX_HISTORY)

    var cursorPosition: CursorPosition = CursorPosition()
        private set

    // Public API
    /** Replace entire document content (e.g., on file open). Does NOT push to undo. */
    fun loadContent(content: String) {
        val newLines = content.split('\n')
        lines.clear()
        lines.addAll(newLines)
        undoStack.clear()
        redoStack.clear()
        cursorPosition = CursorPosition(0, 0)
    }

    /** Return the flat string representation of the document. */
    fun getContent(): String = lines.joinToString("\n")

    /**
     * Full replacement from a TextFieldValue string — called on every keystroke from
     * the BasicTextField composable. Performs a line-level diff to minimise work.
     */
    fun applyFullUpdate(newContent: String, cursorOffset: Int) {
        val newLines = newContent.split('\n')

        // Record bulk replace for undo
        val action = EditAction.BulkReplace(
            oldLines = lines.toList(),
            newLines = newLines
        )
        pushUndo(action)

        lines.clear()
        lines.addAll(newLines)
        cursorPosition = offsetToPosition(cursorOffset)
    }

    fun moveCursor(newOffset: Int) {
        cursorPosition = offsetToPosition(newOffset)
    }

    // Undo / Redo

    fun canUndo() = undoStack.isNotEmpty()
    fun canRedo() = redoStack.isNotEmpty()

    /**
     * Undo last edit action and return the new flat content (or null if nothing to undo).
     */
    fun undo(): String? {
        val action = undoStack.removeLastOrNull() ?: return null
        applyInverse(action, toRedo = true)
        return getContent()
    }

    /**
     * Redo last undone action and return the new flat content.
     */
    fun redo(): String? {
        val action = redoStack.removeLastOrNull() ?: return null
        applyAction(action, toUndo = false)
        return getContent()
    }

    // Coordinate helpers
    /** Convert a flat character offset to (line, column). */
    fun offsetToPosition(offset: Int): CursorPosition {
        if (offset <= 0) return CursorPosition(0, 0)
        var remaining = offset
        for ((index, line) in lines.withIndex()) {
            val lineLength = line.length + 1 // +1 for \n
            if (remaining <= line.length) {
                return CursorPosition(index, remaining)
            }
            remaining -= lineLength
            if (remaining < 0) return CursorPosition(index, line.length)
        }
        val lastLine = lines.lastIndex.coerceAtLeast(0)
        return CursorPosition(lastLine, lines.getOrElse(lastLine) { "" }.length)
    }

    /** Convert a (line, column) position to a flat character offset. */
    fun positionToOffset(pos: CursorPosition): Int {
        var offset = 0
        for (i in 0 until pos.line.coerceAtMost(lines.lastIndex)) {
            offset += lines[i].length + 1 // +1 for \n
        }
        val col = pos.column.coerceAtMost(lines.getOrElse(pos.line) { "" }.length)
        return offset + col
    }

    /** How many lines the document currently has. */
    fun lineCount() = lines.size

    fun lineAt(index: Int) = lines.getOrElse(index) { "" }

    // Private helpers
    private fun pushUndo(action: EditAction) {
        if (undoStack.size >= MAX_HISTORY) undoStack.removeFirst()
        undoStack.addLast(action)
        redoStack.clear()
    }

    private fun applyInverse(action: EditAction, toRedo: Boolean) {
        when (action) {
            is EditAction.BulkReplace -> {
                if (toRedo) redoStack.addLast(action)
                lines.clear()
                lines.addAll(action.oldLines)
            }
            is EditAction.ReplaceLine -> {
                if (toRedo) redoStack.addLast(action)
                if (action.line in lines.indices) lines[action.line] = action.oldText
            }
            is EditAction.Insert -> {
                if (toRedo) redoStack.addLast(action)
                // Inverse of insert = delete
            }
            is EditAction.Delete -> {
                if (toRedo) redoStack.addLast(action)
                // Inverse of delete = insert
            }
        }
    }

    private fun applyAction(action: EditAction, toUndo: Boolean) {
        when (action) {
            is EditAction.BulkReplace -> {
                if (toUndo) undoStack.addLast(action)
                lines.clear()
                lines.addAll(action.newLines)
            }
            is EditAction.ReplaceLine -> {
                if (toUndo) undoStack.addLast(action)
                if (action.line in lines.indices) lines[action.line] = action.newText
            }
            else -> { /* other granular ops */ }
        }
    }

    companion object {
        private const val MAX_HISTORY = 200
    }
}
