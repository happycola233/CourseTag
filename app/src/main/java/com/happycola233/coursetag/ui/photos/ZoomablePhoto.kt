package com.happycola233.coursetag.ui.photos

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import androidx.compose.ui.graphics.painter.Painter
import coil3.compose.rememberAsyncImagePainter
import com.happycola233.coursetag.domain.PhotoEntry
import com.happycola233.coursetag.ui.components.rememberPhotoPreviewRequest
import kotlinx.coroutines.flow.first

@Composable
internal fun ZoomablePhoto(
    entry: PhotoEntry,
    sourceKey: String?,
    active: Boolean,
    gesturesEnabled: Boolean,
    onTap: () -> Unit,
    onDismiss: () -> Unit,
    onFreezePaging: () -> Unit,
    onInteractionChanged: (pagingLocked: Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state = remember { PhotoGestureState(scope) }
    val motion = LocalPhotoPreviewMotion.current
    val grid = LocalPhotoGridReturnState.current
    val latestOnTap by rememberUpdatedState(onTap)
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    val latestOnFreezePaging by rememberUpdatedState(onFreezePaging)
    val latestOnInteractionChanged by rememberUpdatedState(onInteractionChanged)
    val enabled by rememberUpdatedState(gesturesEnabled && active && !state.dismissCommitted)
    var highResolutionRequested by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(active, gesturesEnabled) {
        if (active && gesturesEnabled) {
            if (!highResolutionRequested) {
                snapshotFlow { state.isZoomed && !state.captured && !state.blocksNewGestures }.first { it }
                highResolutionRequested = true
            }
        }
    }
    LaunchedEffect(state, active) {
        if (active) snapshotFlow { state.locksPaging }.collect { latestOnInteractionChanged(it) }
    }

    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().onGloballyPositioned { origin = it.positionInRoot() },
        contentAlignment = Alignment.Center) {
        val hasDimensions = entry.photo.width > 0 && entry.photo.height > 0
        val aspectRatio = if (hasDimensions) entry.photo.width.toFloat() / entry.photo.height else maxWidth / maxHeight
        val imageWidth = minOf(maxWidth, maxHeight * aspectRatio)
        val imageHeight = imageWidth / aspectRatio
        val density = LocalDensity.current
        val fittedSize = with(density) { IntSize(imageWidth.roundToPx(), imageHeight.roundToPx()) }
        val geometry = PhotoGeometry(
            viewport = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) },
            fittedImage = Size(fittedSize.width.toFloat(), fittedSize.height.toFloat()),
        )
        val latestGeometry by rememberUpdatedState(geometry)
        fun liveFrame(): PhotoFrame {
            val transform = state.transform
            val size = geometry.fittedImage * transform.scale
            return PhotoFrame(Rect(geometry.center + transform.offset - Offset(size.width / 2, size.height / 2), size),
                1f - state.dismissProgress * 0.7f)
        }
        SideEffect {
            if (active) {
                motion?.bind(
                    live = { liveFrame().let { it.copy(bounds = it.bounds.translate(origin)) } },
                    target = { grid?.bounds(sourceKey, entry.id) },
                    freeze = { state.interrupt(); latestOnFreezePaging() },
                )
                motion?.enter()
                grid?.showPhoto(sourceKey, entry.id)
            }
        }
        val decodeSize = if (highResolutionRequested && hasDimensions) IntSize(
            minOf(fittedSize.width * 3, entry.photo.width), minOf(fittedSize.height * 3, entry.photo.height),
        ) else fittedSize
        var displayedPainter by remember(entry.photo.uri) { mutableStateOf<Painter?>(null) }
        val painter = rememberAsyncImagePainter(
            // 首次组合就请求屏幕尺寸大图，缓存缩略图负责占位；解码不再排在入场动画之后。
            // Pager 预取的相邻页也能提前开始解码，不必等翻页停稳。
            model = rememberPhotoPreviewRequest(entry.photo.uri, decodeSize),
            transform = { next ->
                // 升级清晰度时保留已经显示的图；原图暂时不可读也不清空可用的缩略图。
                when (next) {
                    is AsyncImagePainter.State.Success -> next.also { displayedPainter = it.painter }
                    is AsyncImagePainter.State.Loading -> next.copy(painter = displayedPainter ?: next.painter)
                        .also { displayedPainter = it.painter }
                    is AsyncImagePainter.State.Error -> next.copy(painter = displayedPainter ?: next.painter)
                    else -> next
                }
            },
        )
        fun drawingFrame() = if (active && motion != null)
            motion.frame.let { it.copy(bounds = it.bounds.translate(-origin)) } else liveFrame()

        Box(
            Modifier.fillMaxSize()
                .photoInputGate(state) { enabled }
                .pointerInput(state) {
                    detectTapGestures(
                        onTap = { if (enabled && !state.blocksNewGestures) latestOnTap() },
                        onDoubleTap = { if (enabled) state.doubleTap(latestGeometry, it) },
                    )
                }
                .photoTransformGestures(state, { latestGeometry }, { enabled }) { velocity ->
                    motion?.releaseVelocity = velocity
                    latestOnDismiss()
                },
            contentAlignment = Alignment.Center,
        ) {
            // 固定测量尺寸；缩放/拖动/导航都只更新绘制与合成，模糊栏可以从第一帧采样到照片。
            Canvas(Modifier.requiredSize(imageWidth, imageHeight).graphicsLayer {
                val frame = drawingFrame()
                scaleX = frame.bounds.width / geometry.fittedImage.width
                scaleY = frame.bounds.height / geometry.fittedImage.height
                translationX = frame.bounds.center.x - geometry.center.x
                translationY = frame.bounds.center.y - geometry.center.y
                shape = RoundedCornerShape(6.dp * frame.corner / scaleX)
                clip = true
            }.semantics {
                contentDescription = entry.photo.name
                role = Role.Image
            }) {
                val bounds = drawingFrame().bounds
                val intrinsic = painter.intrinsicSize.takeIf { it != Size.Unspecified && it.width > 0f && it.height > 0f }
                    ?: geometry.fittedImage
                val cropScale = maxOf(bounds.width / intrinsic.width, bounds.height / intrinsic.height)
                val drawn = intrinsic * cropScale
                // 先按当前边界做居中裁切，再抵消外层非等比缩放；方形缩略图展开时图像不会被拉伸。
                scale(size.width / bounds.width, size.height / bounds.height, pivot = Offset.Zero) {
                    translate((bounds.width - drawn.width) / 2, (bounds.height - drawn.height) / 2) {
                        with(painter) { draw(drawn) }
                    }
                }
            }
        }
    }
}
