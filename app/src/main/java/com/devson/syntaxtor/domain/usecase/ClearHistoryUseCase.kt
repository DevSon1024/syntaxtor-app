package com.devson.syntaxtor.domain.usecase

import com.devson.syntaxtor.data.repository.HistoryRepository

class ClearHistoryUseCase(private val repository: HistoryRepository) {
    suspend operator fun invoke() {
        repository.clearAllHistory()
    }
}
