package com.devson.syntaxtor.domain.usecase

import com.devson.syntaxtor.data.repository.RecentFilesRepository

class AddRecentFileUseCase(private val repository: RecentFilesRepository) {
    suspend operator fun invoke(uriString: String, fileName: String, fileType: String) {
        repository.addRecentFile(uriString, fileName, fileType)
    }
}
