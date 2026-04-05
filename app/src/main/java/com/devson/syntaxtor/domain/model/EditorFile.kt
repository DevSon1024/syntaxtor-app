package com.devson.syntaxtor.domain.model

import android.net.Uri

data class EditorFile(
    val uri: Uri,
    val name: String,
    val content: String,
    val isModified: Boolean = false,
    val fileType: String = "" // e.g., ".txt", ".html"
)
