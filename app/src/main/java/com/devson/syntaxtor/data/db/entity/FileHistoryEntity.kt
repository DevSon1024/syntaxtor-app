package com.devson.syntaxtor.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single version-history checkpoint for one file.
 *
 * isBaseSnapshot = true  → patchJson holds the FULL file text (used as baseline).
 * isBaseSnapshot = false → patchJson holds a serialized list of unified-diff patches
 *                          produced by java-diff-utils, applied on top of the last baseline.
 *
 * Indexed by fileUriString so history queries are fast.
 */
@Entity(
    tableName = "file_history",
    indices = [Index(value = ["fileUriString", "timestamp"])]
)
data class FileHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Stringified content URI, e.g. "content://…/document/abc". */
    val fileUriString: String,

    /** Epoch millis of when the checkpoint was saved. */
    val timestamp: Long,

    /** Human-readable label shown in the History sheet. */
    val label: String,

    /**
     * Either full file text (isBaseSnapshot = true)
     * or a JSON-encoded unified-diff patch (isBaseSnapshot = false).
     */
    val patchJson: String,

    /** Whether this entry is a full-text snapshot. */
    val isBaseSnapshot: Boolean,

    val addedChars: Int = 0,
    val removedChars: Int = 0
)
