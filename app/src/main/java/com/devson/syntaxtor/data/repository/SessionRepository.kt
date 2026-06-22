package com.devson.syntaxtor.data.repository

import com.devson.syntaxtor.data.db.dao.SessionDao
import com.devson.syntaxtor.data.db.entity.SessionFileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SessionRepository(private val dao: SessionDao) {

    suspend fun saveSession(files: List<SessionFileEntity>) = withContext(Dispatchers.IO) {
        dao.clearSession()
        if (files.isNotEmpty()) {
            dao.insertSessionFiles(files)
        }
    }

    suspend fun getSessionFiles(): List<SessionFileEntity> = withContext(Dispatchers.IO) {
        dao.getSessionFiles()
    }

    suspend fun clearSession() = withContext(Dispatchers.IO) {
        dao.clearSession()
    }
}
