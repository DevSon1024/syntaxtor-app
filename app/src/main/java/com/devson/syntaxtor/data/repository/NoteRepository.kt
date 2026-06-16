package com.devson.syntaxtor.data.repository

import com.devson.syntaxtor.data.db.dao.NoteDao
import com.devson.syntaxtor.data.db.entity.NoteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class NoteRepository(private val noteDao: NoteDao) {

    fun observeNotes(): Flow<List<NoteEntity>> = noteDao.observeNotes()

    suspend fun saveNote(note: NoteEntity) = withContext(Dispatchers.IO) {
        if (note.id == 0) {
            noteDao.insertNote(note)
        } else {
            noteDao.updateNote(note)
        }
    }

    suspend fun deleteNote(id: Int) = withContext(Dispatchers.IO) {
        noteDao.deleteNote(id)
    }
}
