package com.devson.syntaxtor.domain.usecase

import com.devson.syntaxtor.data.db.entity.FileHistoryEntity
import com.devson.syntaxtor.data.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow

/** Returns a live stream of version-history entries for a file, newest-first. */
class GetHistoryUseCase(private val historyRepository: HistoryRepository) {
    operator fun invoke(uri: String): Flow<List<FileHistoryEntity>> =
        historyRepository.observeHistory(uri)
}
