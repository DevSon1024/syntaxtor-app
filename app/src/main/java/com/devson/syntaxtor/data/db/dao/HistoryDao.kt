package com.devson.syntaxtor.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.devson.syntaxtor.data.db.entity.FileHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entity: FileHistoryEntity): Long

    /**
     * Live list of all checkpoints for a file, newest first.
     * Used by the History bottom sheet (observes changes in real-time).
     */
    @Query("SELECT * FROM file_history WHERE fileUriString = :uri ORDER BY timestamp DESC")
    fun observeHistoryForFile(uri: String): Flow<List<FileHistoryEntity>>

    /**
     * Full ordered list (oldest first) for patch reconstruction.
     * We need oldest-first so we can replay patches forward.
     */
    @Query("SELECT * FROM file_history WHERE fileUriString = :uri ORDER BY timestamp ASC")
    suspend fun getAllHistoryForFile(uri: String): List<FileHistoryEntity>

    /**
     * Count of checkpoints for a file - used to decide when to create a new base snapshot.
     */
    @Query("SELECT COUNT(*) FROM file_history WHERE fileUriString = :uri")
    suspend fun countForFile(uri: String): Int

    /** Delete all history for a file (e.g., when the file is removed from the editor). */
    @Query("DELETE FROM file_history WHERE fileUriString = :uri")
    suspend fun deleteHistoryForFile(uri: String)

    /** Delete a single checkpoint by ID. */
    @Query("DELETE FROM file_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM file_history")
    suspend fun clearAllHistory()
}
