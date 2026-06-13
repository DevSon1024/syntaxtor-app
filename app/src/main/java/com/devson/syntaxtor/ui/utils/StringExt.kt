package com.devson.syntaxtor.ui.utils

/**
 * Truncates and formats raw SAF URI strings or filenames to display cleanly in UI.
 * Extracts the raw filename (the part after the last '/' or ':') and truncates the base name
 * if it exceeds [maxLength], retaining the file extension.
 * E.g., "primary:Documents/joker.txt" -> "joker.txt"
 * E.g., "12345678901234567.txt" -> "123456789012345... .txt"
 */
fun String.formatAsFileName(maxLength: Int = 15): String {
    val fileName = this.substringAfterLast('/').substringAfterLast(':')
    val dotIndex = fileName.lastIndexOf('.')
    val (baseName, extension) = if (dotIndex in 1 until fileName.length) {
        fileName.substring(0, dotIndex) to fileName.substring(dotIndex)
    } else {
        fileName to ""
    }
    return if (baseName.length > maxLength) {
        val extPart = if (extension.isNotEmpty()) " $extension" else ""
        "${baseName.substring(0, maxLength)}...$extPart"
    } else {
        fileName
    }
}
