package com.happycola233.coursetag.domain

/** Android 16 起单次 MediaStore 写入授权最多包含 2,000 张照片；旧系统也使用同一批次大小。 */
const val MAX_WRITE_REQUEST_ITEMS = 2_000

/** 所有批次授权成功后才允许改名，取消任一批次都不会留下部分执行的操作。 */
suspend fun authorizePhotoWrites(
    photoIds: List<Long>,
    requestAccess: suspend (photoIds: List<Long>, batch: Int, totalBatches: Int) -> Boolean,
): Boolean {
    val batches = photoIds.chunked(MAX_WRITE_REQUEST_ITEMS)
    for ((index, batch) in batches.withIndex()) {
        if (!requestAccess(batch, index + 1, batches.size)) return false
    }
    return true
}
