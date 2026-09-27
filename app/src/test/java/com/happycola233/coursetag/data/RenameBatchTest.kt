package com.happycola233.coursetag.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RenameBatchTest {
    private val batch = RenameBatch("batch", "添加课程", 0, listOf(
        RenameRecord(1, "a.jpg", "a（数学）.jpg"),
        RenameRecord(2, "b.jpg", "b（数学）.jpg"),
        RenameRecord(3, "c.jpg", "c（数学）.jpg"),
    ))

    @Test
    fun partialUndoSurvivesReloadAndOnlyRetriesRemainingPhotos() {
        val partial = batch.withRevertedPhotos(setOf(1, 3))
        val restored = Json.decodeFromString<RenameBatch>(Json.encodeToString(partial))
        assertFalse(restored.undone)
        assertEquals(listOf(batch.records[1]), restored.pendingRecords)
        assertEquals(batch.records, restored.records)
        val complete = restored.withRevertedPhotos(setOf(2))
        assertTrue(complete.undone)
        assertTrue(complete.pendingRecords.isEmpty())
    }

    @Test
    fun readsHistoryWrittenBeforePerPhotoUndoTracking() {
        val legacy = """{"id":"old","title":"旧记录","createdAt":0,"records":[{"mediaId":1,"from":"a.jpg","to":"b.jpg"}],"undone":true}"""
        val restored = Json.decodeFromString<RenameBatch>(legacy)
        assertTrue(restored.pendingRecords.isEmpty())
        assertTrue(restored.revertedMediaIds.isEmpty())
    }
}
