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

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.devson.syntaxtor.data.db.dao.SessionDao
import com.devson.syntaxtor.data.db.entity.SessionFileEntity

@Database(
    entities = [FileHistoryEntity::class, RecentFileEntity::class, NoteEntity::class, SessionFileEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao
    abstract fun recentFileDao(): RecentFileDao
    abstract fun noteDao(): NoteDao
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `session_files` (" +
                        "`uriString` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`content` TEXT NOT NULL, " +
                        "`isModified` INTEGER NOT NULL, " +
                        "`fileType` TEXT NOT NULL, " +
                        "`tabIndex` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`uriString`))"
                )
                db.execSQL("ALTER TABLE `file_history` ADD COLUMN `addedChars` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `file_history` ADD COLUMN `removedChars` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "syntaxtor_history.db"
                )
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
