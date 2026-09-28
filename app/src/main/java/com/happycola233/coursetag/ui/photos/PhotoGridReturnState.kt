package com.happycola233.coursetag.ui.photos

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

internal const val PhotosGridKey = "photos"
internal fun coursePhotosGridKey(courseName: String) = "course:$courseName"

internal val LocalPhotoGridReturnState = staticCompositionLocalOf<PhotoGridReturnState?> { null }

/**
 * 预览保留来源网格；返回目标始终来自当前网格的实测位置。
 * 已在屏幕内的照片保持原布局；滑到屏幕外的照片则在返回前放到网格中部。
 */
internal class PhotoGridReturnState {
    private var sourceKey: String? = null
    private var sourceState: LazyGridState? = null
    private var sourceOrigin: () -> Offset = { Offset.Zero }
    private var hiddenPhotoId by mutableStateOf<Long?>(null)
    // 先读取可观察的 ID，让首次打开预览时，已经绘制的网格图层也能收到隐藏通知。
    fun hides(key: String?, id: Long) = hiddenPhotoId == id && key == sourceKey
    fun reveal() { hiddenPhotoId = null }
    fun bounds(key: String?, photoId: Long): Rect? {
        if (key != sourceKey) return null
        val item = sourceState?.layoutInfo?.visibleItemsInfo?.firstOrNull { it.key == photoId } ?: return null
        return Rect(sourceOrigin() + Offset(item.offset.x.toFloat(), item.offset.y.toFloat()),
            Size(item.size.width.toFloat(), item.size.height.toFloat()))
    }
    private var indexByPhotoId: Map<Long, Int> = emptyMap()
    private var headerItemCount = 0
    private var visiblePhotoIds: Set<Long> = emptySet()
    private var firstVisibleItemIndex = 0
    private var firstVisibleItemOffset = 0
    private var centeredItemOffset = 0
    private var currentPhotoId: Long? = null

    fun gridState(key: String): LazyGridState? = sourceState.takeIf { sourceKey == key }

    fun open(key: String, photoId: Long, state: LazyGridState, photoIndices: Map<Long, Int>, sectionCount: Int, origin: () -> Offset) {
        sourceKey = key
        sourceOrigin = origin
        sourceState = state
        currentPhotoId = photoId
        val layout = state.layoutInfo
        // 网格头部可以包含相册筛选、提示卡片等；布局的总数包含这些条目。
        headerItemCount = layout.totalItemsCount - photoIndices.size - sectionCount
        indexByPhotoId = photoIndices
        visiblePhotoIds = layout.visibleItemsInfo.filter {
            it.offset.y >= 0 && it.offset.y + it.size.height <= layout.viewportEndOffset - layout.afterContentPadding
        }.mapNotNull { it.key as? Long }.toSet()
        firstVisibleItemIndex = state.firstVisibleItemIndex
        firstVisibleItemOffset = state.firstVisibleItemScrollOffset
        val photoHeight = layout.visibleItemsInfo.first { it.key == photoId }.size.height
        centeredItemOffset = -(layout.viewportEndOffset - layout.afterContentPadding - photoHeight) / 2
    }

    fun showPhoto(key: String?, photoId: Long) {
        if (sourceKey != key) return
        hiddenPhotoId = photoId
        if (currentPhotoId == photoId) return
        currentPhotoId = photoId
        val index = indexByPhotoId[photoId] ?: return
        // 翻回原可见范围时同时覆盖之前的滚动请求，不能沿用上一张的返回位置。
        if (photoId in visiblePhotoIds) {
            sourceState?.requestScrollToItem(firstVisibleItemIndex, firstVisibleItemOffset)
        } else {
            sourceState?.requestScrollToItem(index + headerItemCount, centeredItemOffset)
        }
    }
}
