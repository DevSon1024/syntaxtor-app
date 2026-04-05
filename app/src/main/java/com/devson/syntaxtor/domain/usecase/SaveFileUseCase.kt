package com.devson.syntaxtor.domain.usecase

import com.devson.syntaxtor.data.repository.FileRepository
import com.devson.syntaxtor.domain.model.EditorFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SaveFileUseCase(private val fileRepository: FileRepository) {
    suspend operator fun invoke(file: EditorFile): Result<Unit> = withContext(Dispatchers.IO) {
        fileRepository.writeFile(file.uri, file.content)
    }
}
