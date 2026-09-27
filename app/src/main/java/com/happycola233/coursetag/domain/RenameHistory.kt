package com.happycola233.coursetag.domain

import com.happycola233.coursetag.data.RenameBatch

private const val MAX_HISTORY_BATCHES = 50

/** 记录本次成功的改名，并保留部分撤销的原批次，避免达到历史上限后丢失重试入口。 */
fun List<RenameBatch>.withRecordedRename(batch: RenameBatch, revertedBatchId: String?): List<RenameBatch> {
    val restoredIds = batch.records.mapTo(HashSet()) { it.mediaId }
    val updated = map { previous ->
        if (previous.id == revertedBatchId) previous.withRevertedPhotos(restoredIds) else previous
    }
    val retryBatch = updated.firstOrNull { it.id == revertedBatchId && !it.undone }
    return (listOfNotNull(batch, retryBatch) + updated.filterNot { it.id == retryBatch?.id })
        .take(MAX_HISTORY_BATCHES)
}
