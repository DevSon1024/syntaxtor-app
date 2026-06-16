package com.devson.syntaxtor.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.devson.syntaxtor.data.db.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Query("SELECT * FROM notes ORDER BY lastUpdated DESC")
    fun observeNotes(): Flow<List<NoteEntity>>

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNote(id: Int)
}
