package com.devson.syntaxtor.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_files")
data class RecentFileEntity(
    @PrimaryKey
    val uriString: String,
    val fileName: String,
    val lastOpened: Long,
    val fileType: String
)
