package com.devson.syntaxtor.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.devson.syntaxtor.data.db.entity.SessionFileEntity

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionFiles(files: List<SessionFileEntity>)

    @Query("DELETE FROM session_files")
    suspend fun clearSession()

    @Query("SELECT * FROM session_files ORDER BY tabIndex ASC")
    suspend fun getSessionFiles(): List<SessionFileEntity>
}
