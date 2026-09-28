package com.happycola233.coursetag.data.media

import android.content.ContentResolver
import android.net.Uri
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val PhotoThumbnailSize = 384
internal data class PhotoThumbnail(val uri: Uri)
internal fun thumbnailCacheKey(uri: Uri) = "thumbnail:$uri"
internal fun previewCacheKey(uri: Uri) = "preview:$uri"

/** 使用 MediaStore 跨进程缓存的缩略图，网格无需反复打开数千万像素的原图。 */
internal class PhotoThumbnailFetcher(private val resolver: ContentResolver, private val uri: Uri) : Fetcher {
    override suspend fun fetch(): ImageFetchResult = coroutineScope {
        val cancellation = CancellationSignal()
        val cancellationWatcher = launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { cancellation.cancel() }
        }
        try {
            val bitmap = withContext(thumbnailDispatcher) {
                resolver.loadThumbnail(uri, Size(PhotoThumbnailSize, PhotoThumbnailSize), cancellation)
            }
            ImageFetchResult(bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
        } catch (cancelled: OperationCanceledException) {
            // Android 的取消异常不是协程 CancellationException；恢复协程语义，避免滚出屏幕被当成加载失败。
            currentCoroutineContext().ensureActive()
            throw cancelled
        } finally {
            cancellationWatcher.cancel()
        }
    }

    class Factory : Fetcher.Factory<PhotoThumbnail> {
        override fun create(data: PhotoThumbnail, options: Options, imageLoader: ImageLoader): Fetcher =
            PhotoThumbnailFetcher(options.context.contentResolver, data.uri)
    }

    private companion object {
        // 限制冷启动和快速滚动时的并行解码，给主线程与预览渲染留出 CPU/内存带宽。
        val thumbnailDispatcher = Dispatchers.IO.limitedParallelism(2)
    }
}
