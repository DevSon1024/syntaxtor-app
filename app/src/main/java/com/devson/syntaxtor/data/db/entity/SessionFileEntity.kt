package com.devson.syntaxtor.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "session_files")
data class SessionFileEntity(
    @PrimaryKey
    val uriString: String,
    val name: String,
    val content: String,
    val isModified: Boolean,
    val fileType: String,
    val tabIndex: Int
)
