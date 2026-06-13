package com.devson.syntaxtor.data.repository

import com.devson.syntaxtor.data.db.dao.RecentFileDao
import com.devson.syntaxtor.data.db.entity.RecentFileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class RecentFilesRepository(private val dao: RecentFileDao) {

    fun observeRecentFiles(): Flow<List<RecentFileEntity>> = dao.observeRecentFiles()

    suspend fun addRecentFile(uriString: String, fileName: String, fileType: String) =
        withContext(Dispatchers.IO) {
            dao.insertRecentFile(
                RecentFileEntity(
                    uriString = uriString,
                    fileName = fileName,
                    lastOpened = System.currentTimeMillis(),
                    fileType = fileType
                )
            )
        }

    suspend fun removeRecentFile(uriString: String) =
        withContext(Dispatchers.IO) {
            dao.deleteRecentFile(uriString)
        }

    suspend fun clearAllRecentFiles() =
        withContext(Dispatchers.IO) {
            dao.clearAllRecentFiles()
        }
}
