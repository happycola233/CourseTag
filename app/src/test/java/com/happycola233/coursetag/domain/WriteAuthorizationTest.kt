package com.happycola233.coursetag.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WriteAuthorizationTest {
    @Test
    fun respectsSystemLimitWithoutLosingOrRepeatingPhotos() = runBlocking {
        for (size in listOf(1, 2_000, 2_001, 4_001)) {
            val ids = (1L..size.toLong()).toList()
            val requested = mutableListOf<Long>()
            val batchNumbers = mutableListOf<Int>()
            assertTrue(authorizePhotoWrites(ids) { batch, number, total ->
                assertTrue(batch.size <= 2_000)
                assertEquals((size + 1_999) / 2_000, total)
                requested += batch
                batchNumbers += number
                true
            })
            assertEquals(ids, requested)
            assertEquals((1..batchNumbers.size).toList(), batchNumbers)
        }
    }

    @Test
    fun cancellingAnyBatchStopsFurtherRequestsAndPreventsWriting() = runBlocking {
        for (cancelAt in 1..3) {
            var requests = 0
            var wrotePhotos = false
            if (authorizePhotoWrites((1L..4_001L).toList()) { _, number, _ ->
                requests++
                number != cancelAt
            }) wrotePhotos = true
            assertEquals(cancelAt, requests)
            assertFalse(wrotePhotos)
        }
    }

    @Test
    fun waitsForEverySystemResultBeforeAllowingWrites() = runBlocking {
        val replies = List(2) { CompletableDeferred<Boolean>() }
        var requestedBatches = 0
        val result = async {
            authorizePhotoWrites((1L..2_001L).toList()) { _, number, _ ->
                requestedBatches++
                replies[number - 1].await()
            }
        }
        yield()
        assertEquals(1, requestedBatches)
        assertFalse(result.isCompleted)
        replies[0].complete(true)
        yield()
        assertEquals(2, requestedBatches)
        assertFalse(result.isCompleted)
        replies[1].complete(true)
        assertTrue(result.await())
    }
}
