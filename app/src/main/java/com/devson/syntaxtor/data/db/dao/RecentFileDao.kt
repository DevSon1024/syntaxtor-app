package com.devson.syntaxtor.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.devson.syntaxtor.data.db.entity.RecentFileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentFileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentFile(recentFile: RecentFileEntity)

    @Query("SELECT * FROM recent_files ORDER BY lastOpened DESC")
    fun observeRecentFiles(): Flow<List<RecentFileEntity>>

    @Query("DELETE FROM recent_files WHERE uriString = :uriString")
    suspend fun deleteRecentFile(uriString: String)

    @Query("DELETE FROM recent_files")
    suspend fun clearAllRecentFiles()
}
