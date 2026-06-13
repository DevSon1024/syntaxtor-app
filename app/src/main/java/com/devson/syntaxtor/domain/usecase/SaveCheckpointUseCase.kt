package com.devson.syntaxtor.domain.usecase

import com.devson.syntaxtor.data.repository.HistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Saves a new version checkpoint for the given file URI and content. */
class SaveCheckpointUseCase(private val historyRepository: HistoryRepository) {
    suspend operator fun invoke(uri: String, content: String) = withContext(Dispatchers.IO) {
        historyRepository.saveCheckpoint(uri, content)
    }
}
