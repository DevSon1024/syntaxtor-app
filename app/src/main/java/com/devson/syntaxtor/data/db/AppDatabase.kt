package com.devson.syntaxtor.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.devson.syntaxtor.data.db.dao.HistoryDao
import com.devson.syntaxtor.data.db.entity.FileHistoryEntity

import com.devson.syntaxtor.data.db.dao.NoteDao
import com.devson.syntaxtor.data.db.entity.NoteEntity
import com.devson.syntaxtor.data.db.dao.RecentFileDao
import com.devson.syntaxtor.data.db.entity.RecentFileEntity

@Database(
    entities = [FileHistoryEntity::class, RecentFileEntity::class, NoteEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao
    abstract fun recentFileDao(): RecentFileDao
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "syntaxtor_history.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
