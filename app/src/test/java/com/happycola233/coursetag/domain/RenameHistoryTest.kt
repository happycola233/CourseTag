package com.happycola233.coursetag.domain

import com.happycola233.coursetag.data.RenameBatch
import com.happycola233.coursetag.data.RenameRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RenameHistoryTest {
    private fun batch(id: String) = RenameBatch(id, "标记课程", 0, listOf(
        RenameRecord(1, "a.jpg", "a（数学）.jpg"),
        RenameRecord(2, "b.jpg", "b（数学）.jpg"),
    ))

    @Test
    fun oldestBatchRemainsRetryableWhenHistoryIsFull() {
        val history = (1..50).map { batch(it.toString()) }
        val undo = RenameBatch("undo", "撤销", 1, listOf(RenameRecord(1, "a（数学）.jpg", "a.jpg")))
        val updated = history.withRecordedRename(undo, "50")
        val original = updated.single { it.id == "50" }
        assertEquals(50, updated.size)
        assertFalse(original.undone)
        assertEquals(listOf(2L), original.pendingRecords.map { it.mediaId })
        assertEquals(listOf("undo", "50"), updated.take(2).map { it.id })
    }

    @Test
    fun successfulRetryCompletesOriginalBatchWithoutLosingAuditRecords() {
        val original = batch("original").withRevertedPhotos(setOf(1))
        val retry = RenameBatch("retry", "继续撤销", 1, listOf(RenameRecord(2, "b（数学）.jpg", "b.jpg")))
        val updated = listOf(original).withRecordedRename(retry, original.id)
        assertTrue(updated.single { it.id == original.id }.undone)
        assertEquals(original.records, updated.single { it.id == original.id }.records)
        assertEquals(retry, updated.first())
    }
}
