package com.devson.syntaxtor.domain.usecase

import android.net.Uri
import com.devson.syntaxtor.data.repository.FileRepository
import com.devson.syntaxtor.domain.model.EditorFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OpenFileUseCase(private val fileRepository: FileRepository) {
    suspend operator fun invoke(uri: Uri): Result<EditorFile> = withContext(Dispatchers.IO) {
        fileRepository.readFile(uri)
    }
}
