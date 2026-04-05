package com.devson.syntaxtor.data.repository

import android.net.Uri
import com.devson.syntaxtor.domain.model.EditorFile

interface FileRepository {
    suspend fun readFile(uri: Uri): Result<EditorFile>
    suspend fun writeFile(uri: Uri, content: String): Result<Unit>
}
