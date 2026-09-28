package com.happycola233.coursetag.data.media

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

data class Photo(
    val id: Long,
    val uri: Uri,
    val name: String,
    /** 所在目录（MediaStore RELATIVE_PATH），同一目录内的文件名不能重复。 */
    val folder: String,
    val volume: String,
    val albumId: Long,
    val albumName: String,
    /** 拍摄时间；缺少拍摄信息时退回文件修改时间。 */
    val takenAt: Long,
    /** 已根据媒体方向校正的显示尺寸，与图片解码后的方向一致。 */
    val width: Int,
    val height: Int,
)

data class RenameOperation(val photoId: Long, val uri: Uri, val newName: String)

sealed interface RenameOutcome {
    val photoId: Long

    /** [actualName] 为系统最终写入的文件名，个别机型会对文件名做额外整理。 */
    data class Renamed(override val photoId: Long, val actualName: String) : RenameOutcome
    data class Failed(override val photoId: Long, val reason: String) : RenameOutcome
}

class MediaRepository(context: Context) {
    private val resolver: ContentResolver = context.contentResolver
    private val collection: Uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

    suspend fun loadPhotos(): List<Photo> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.VOLUME_NAME,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.ORIENTATION,
        )
        val photos = mutableListOf<Photo>()
        resolver.query(collection, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val folderColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            val volumeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.VOLUME_NAME)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val albumNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val takenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val orientationColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.ORIENTATION)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameColumn) ?: continue
                val id = cursor.getLong(idColumn)
                val taken = cursor.getLong(takenColumn)
                val folder = cursor.getString(folderColumn).orEmpty()
                val orientation = cursor.getInt(orientationColumn)
                val swapsDimensions = orientation == 90 || orientation == 270
                photos += Photo(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    name = name,
                    folder = folder,
                    volume = cursor.getString(volumeColumn).orEmpty(),
                    albumId = cursor.getLong(albumIdColumn),
                    albumName = cursor.getString(albumNameColumn)
                        ?: folder.trimEnd('/').substringAfterLast('/').ifEmpty { "未命名相册" },
                    takenAt = if (taken > 0) taken else cursor.getLong(modifiedColumn) * 1000,
                    width = cursor.getInt(if (swapsDimensions) heightColumn else widthColumn),
                    height = cursor.getInt(if (swapsDimensions) widthColumn else heightColumn),
                )
            }
        }
        photos.sortedByDescending { it.takenAt }
    }

    /** 图库内容变化（新增、删除、重命名）时发出通知。 */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(collection, true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    /** 为一批照片请求修改权限；调用方按系统数量上限拆分批次。 */
    fun createWriteRequest(uris: List<Uri>): PendingIntent = MediaStore.createWriteRequest(resolver, uris)

    suspend fun rename(
        operations: List<RenameOperation>,
        onProgress: (done: Int) -> Unit,
    ): List<RenameOutcome> = withContext(Dispatchers.IO) {
        val outcomes = operations.mapIndexed { index, operation ->
            val outcome = runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, operation.newName)
                }
                if (resolver.update(operation.uri, values, null, null) > 0) {
                    RenameOutcome.Renamed(operation.photoId, operation.newName)
                } else {
                    RenameOutcome.Failed(operation.photoId, "照片已不存在")
                }
            }.getOrElse { error ->
                RenameOutcome.Failed(operation.photoId, describe(error))
            }
            onProgress(index + 1)
            outcome
        }
        confirmActualNames(outcomes)
    }

    private fun confirmActualNames(outcomes: List<RenameOutcome>): List<RenameOutcome> {
        val renamedIds = outcomes.filterIsInstance<RenameOutcome.Renamed>().map { it.photoId }
        if (renamedIds.isEmpty()) return outcomes
        val actualNames = mutableMapOf<Long, String>()
        renamedIds.chunked(500).forEach { chunk ->
            resolver.query(
                collection,
                arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME),
                "${MediaStore.Images.Media._ID} IN (${chunk.joinToString(",")})",
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) actualNames[cursor.getLong(0)] = cursor.getString(1)
            }
        }
        return outcomes.map { outcome ->
            if (outcome is RenameOutcome.Renamed) {
                actualNames[outcome.photoId]?.let { outcome.copy(actualName = it) } ?: outcome
            } else {
                outcome
            }
        }
    }

    private fun describe(error: Throwable): String = when (error) {
        is SecurityException -> "没有修改这张照片的权限"
        is IllegalStateException -> "目标文件名已被占用"
        else -> "系统未能完成重命名"
    }
}
