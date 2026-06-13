package com.devson.syntaxtor.domain.usecase

import com.devson.syntaxtor.data.repository.HistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reconstructs file content as it was at a given checkpoint and returns it. */
class RestoreVersionUseCase(private val historyRepository: HistoryRepository) {
    suspend operator fun invoke(uri: String, checkpointId: Long): Result<String> =
        withContext(Dispatchers.IO) {
            val text = historyRepository.reconstructAtCheckpoint(uri, checkpointId)
            if (text != null) Result.success(text)
            else Result.failure(Exception("No history found for this file"))
        }
}
