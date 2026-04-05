package com.devson.syntaxtor.file.manager

import android.content.Context
import android.net.Uri
import com.devson.syntaxtor.data.repository.FileRepository
import com.devson.syntaxtor.domain.model.EditorFile

class SafFileManager(private val context: Context) : FileRepository {
    
    override suspend fun readFile(uri: Uri): Result<EditorFile> {
        return try {
            val content = context.contentResolver.openInputStream(uri)?.use { 
                it.bufferedReader().readText() 
            } ?: throw Exception("Cannot read file")
            
            val name = uri.lastPathSegment ?: "unknown"
            val extension = name.substringAfterLast('.', "")
            val fileType = if (extension.isNotEmpty()) ".$extension".lowercase() else ""
            
            Result.success(EditorFile(uri = uri, name = name, content = content, fileType = fileType))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun writeFile(uri: Uri, content: String): Result<Unit> {
        return try {
            context.contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(content.toByteArray())
            } ?: throw Exception("Cannot write file")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
