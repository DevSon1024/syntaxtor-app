package com.devson.syntaxtor.domain.usecase

import com.devson.syntaxtor.data.db.entity.RecentFileEntity
import com.devson.syntaxtor.data.repository.RecentFilesRepository
import kotlinx.coroutines.flow.Flow

class GetRecentFilesUseCase(private val repository: RecentFilesRepository) {
    operator fun invoke(): Flow<List<RecentFileEntity>> {
        return repository.observeRecentFiles()
    }
}
