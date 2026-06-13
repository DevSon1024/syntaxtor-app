package com.devson.syntaxtor.data.repository

import com.devson.syntaxtor.data.db.dao.HistoryDao
import com.devson.syntaxtor.data.db.entity.FileHistoryEntity
import com.github.difflib.DiffUtils
import com.github.difflib.patch.Patch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages the local version history for all open files.
 *
 * Strategy:
 *  - The very first checkpoint for a file (or every [BASE_SNAPSHOT_INTERVAL]-th one) is stored
 *    as a full-text "base snapshot".
 *  - Subsequent checkpoints store only the unified-diff patch between the previous text and the
 *    current text, serialised as a compact string.
 *  - Restoration: walk checkpoints oldest-first, find the latest base snapshot ≤ targetId,
 *    then replay each patch in order up to (and including) targetId.
 */
class HistoryRepository(private val dao: HistoryDao) {

    companion object {
        /** Save a full snapshot every N saves. */
        private const val BASE_SNAPSHOT_INTERVAL = 10

        private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss · dd MMM", Locale.getDefault())
    }

    fun observeHistory(uri: String): Flow<List<FileHistoryEntity>> =
        dao.observeHistoryForFile(uri)

    /**
     * Saves a new checkpoint for [uri].
     * Internally decides whether to store a full snapshot or a diff patch.
     */
    suspend fun saveCheckpoint(uri: String, currentContent: String) =
        withContext(Dispatchers.IO) {
            val count = dao.countForFile(uri)
            val isBase = (count == 0) || (count % BASE_SNAPSHOT_INTERVAL == 0)

            val patchJson: String
            if (isBase) {
                // Full-text baseline — no diffing needed.
                patchJson = currentContent
            } else {
                // Get all existing checkpoints and reconstruct the last saved text.
                val history = dao.getAllHistoryForFile(uri)
                val previousText = reconstructText(history, history.last().id)

                // Produce a unified diff patch
                val prevLines = previousText.lines()
                val currLines = currentContent.lines()
                val patch = DiffUtils.diff(prevLines, currLines)
                patchJson = serialisePatch(patch)
            }

            val changedChars = if (isBase) currentContent.length else patchJson.length
            val label = "${TIME_FORMAT.format(Date())}  ·  ~$changedChars chars"

            dao.insertHistory(
                FileHistoryEntity(
                    fileUriString = uri,
                    timestamp = System.currentTimeMillis(),
                    label = label,
                    patchJson = patchJson,
                    isBaseSnapshot = isBase
                )
            )
        }

    /**
     * Reconstructs the file content as it was at the checkpoint with [targetId].
     * Returns null if no history exists.
     */
    suspend fun reconstructAtCheckpoint(uri: String, targetId: Long): String? =
        withContext(Dispatchers.IO) {
            val history = dao.getAllHistoryForFile(uri)
            if (history.isEmpty()) return@withContext null

            // Keep only entries up to (and including) the target.
            val relevant = history.filter { it.id <= targetId }
            if (relevant.isEmpty()) return@withContext null

            reconstructText(relevant, relevant.last().id)
        }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Given an ASCENDING list of history entries, reconstructs the content at [targetId].
     */
    private fun reconstructText(history: List<FileHistoryEntity>, targetId: Long): String {
        // Find the latest base snapshot that is ≤ targetId.
        val baseIndex = history.indexOfLast { it.isBaseSnapshot && it.id <= targetId }
        if (baseIndex < 0) return ""

        var text = history[baseIndex].patchJson   // base snapshot stores raw text

        // Replay patches from the entry after the base up to targetId.
        for (i in (baseIndex + 1)..history.lastIndex) {
            val entry = history[i]
            if (entry.id > targetId) break
            if (!entry.isBaseSnapshot) {
                val patch = deserialisePatch(entry.patchJson)
                val lines = text.lines().toMutableList()
                text = try {
                    DiffUtils.patch(lines, patch).joinToString("\n")
                } catch (e: Exception) {
                    // If patch fails (e.g., content diverged), keep previous text.
                    text
                }
            }
        }
        return text
    }

    // ---- Patch serialisation ----
    // We encode each delta as:
    //   <TYPE>|<sourcePos>|<sourceLines…joined by \r>|<targetLines…joined by \r>
    // Deltas are separated by \u0000 (null byte, safe because text uses \n).

    private fun serialisePatch(patch: Patch<String>): String {
        return patch.deltas.joinToString("\u0000") { delta ->
            val type = delta.type.name          // INSERT, DELETE, CHANGE
            val srcPos = delta.source.position
            val srcLines = delta.source.lines.joinToString("\r")
            val tgtLines = delta.target.lines.joinToString("\r")
            "$type|$srcPos|$srcLines|$tgtLines"
        }
    }

    private fun deserialisePatch(json: String): Patch<String> {
        val patch = Patch<String>()
        if (json.isBlank()) return patch

        json.split("\u0000").forEach { raw ->
            val parts = raw.split("|", limit = 4)
            if (parts.size != 4) return@forEach

            val type = com.github.difflib.patch.DeltaType.valueOf(parts[0])
            val srcPos = parts[1].toIntOrNull() ?: return@forEach
            val srcLines = if (parts[2].isEmpty()) emptyList() else parts[2].split("\r")
            val tgtLines = if (parts[3].isEmpty()) emptyList() else parts[3].split("\r")

            val source = com.github.difflib.patch.Chunk<String>(srcPos, srcLines)
            val target = com.github.difflib.patch.Chunk<String>(srcPos, tgtLines)

            val delta: com.github.difflib.patch.AbstractDelta<String> = when (type) {
                com.github.difflib.patch.DeltaType.INSERT ->
                    com.github.difflib.patch.InsertDelta(source, target)
                com.github.difflib.patch.DeltaType.DELETE ->
                    com.github.difflib.patch.DeleteDelta(source, target)
                else ->
                    com.github.difflib.patch.ChangeDelta(source, target)
            }
            patch.addDelta(delta)
        }
        return patch
    }
}
