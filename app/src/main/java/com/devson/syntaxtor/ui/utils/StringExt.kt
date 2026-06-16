package com.devson.syntaxtor.ui.utils

fun String.formatAsFileName(showExtension: Boolean = true, maxLength: Int = 15): String {
    val fileName = this.substringAfterLast('/').substringAfterLast(':')
    val dotIndex = fileName.lastIndexOf('.')
    val (baseName, extension) = if (dotIndex in 1 until fileName.length) {
        fileName.substring(0, dotIndex) to fileName.substring(dotIndex)
    } else {
        fileName to ""
    }
    val nameToShow = if (showExtension) {
        fileName
    } else {
        baseName
    }
    return if (baseName.length > maxLength) {
        val extPart = if (showExtension && extension.isNotEmpty()) " $extension" else ""
        "${baseName.substring(0, maxLength)}...$extPart"
    } else {
        nameToShow
    }
}
