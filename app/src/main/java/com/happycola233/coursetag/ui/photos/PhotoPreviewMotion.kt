package com.happycola233.coursetag.ui.photos

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

internal val LocalPhotoPreviewMotion = staticCompositionLocalOf<PhotoPreviewMotion?> { null }
internal enum class PhotoPreviewPhase { Entering, Open, PredictiveBack, Closing }
internal data class PhotoFrame(val bounds: Rect, val backdrop: Float, val corner: Float = 0f)

/**
 * 进入、系统返回与拖动退出共用同一张照片和同一组边界。
 * 返回随时从屏幕上正在绘制的帧接续，不等待入场结束，也不依赖导航动画的剩余时长。
 */
@Stable
internal class PhotoPreviewMotion(private val scope: CoroutineScope) {
    var phase by mutableStateOf(PhotoPreviewPhase.Entering)
        private set
    private var animatedFrame by mutableStateOf<PhotoFrame?>(null)
    private var animation: Job? = null
    private var initialized = false
    private var liveFrame: () -> PhotoFrame = { PhotoFrame(Rect.Zero, 1f) }
    private var thumbnail: () -> Rect? = { null }
    private var freezeGesture: () -> Unit = {}
    private var returnStart: PhotoFrame? = null
    private var restoredFrame: PhotoFrame? = null
    var releaseVelocity = Velocity.Zero

    val frame: PhotoFrame get() = if (phase == PhotoPreviewPhase.Open) liveFrame() else animatedFrame ?: liveFrame()
    val gesturesEnabled get() = phase == PhotoPreviewPhase.Open

    fun bind(live: () -> PhotoFrame, target: () -> Rect?, freeze: () -> Unit) {
        liveFrame = live
        thumbnail = target
        freezeGesture = freeze
    }

    fun enter() {
        if (initialized) return
        initialized = true
        val target = liveFrame()
        val origin = thumbnail()
        animatedFrame = PhotoFrame(origin ?: target.bounds, 0f, if (origin == null) 0f else 1f)
        animation = scope.launch {
            animate(target, damping = 0.78f, stiffness = 700f)
            phase = PhotoPreviewPhase.Open
        }
    }

    fun beginBack() {
        if (phase == PhotoPreviewPhase.Closing) return
        val current = frame
        animation?.cancel()
        freezeGesture()
        restoredFrame = liveFrame()
        returnStart = current
        animatedFrame = current
        phase = PhotoPreviewPhase.PredictiveBack
    }

    fun seekBack(progress: Float) {
        if (phase != PhotoPreviewPhase.PredictiveBack) return
        val start = returnStart ?: return
        animatedFrame = interpolate(start, exitFrame(start), progress)
    }

    fun cancelBack() {
        if (phase != PhotoPreviewPhase.PredictiveBack) return
        animation = scope.launch {
            animate(restoredFrame!!, damping = 1f, stiffness = 600f)
            phase = PhotoPreviewPhase.Open
            returnStart = null
        }
    }

    fun requestClose(): Boolean {
        if (phase == PhotoPreviewPhase.Closing) return false
        val start = frame
        animation?.cancel()
        freezeGesture()
        animatedFrame = start
        phase = PhotoPreviewPhase.Closing
        return true
    }

    /** Nav3 保留叠层直到此动画完成，底下的网格无需在退出时重新创建。 */
    suspend fun close() {
        requestClose()
        // 退出保持无过冲，用更高的刚度补偿阻尼，使收回照片的节奏接近入场。
        animate(exitFrame(frame), damping = 1f, stiffness = 1100f, velocity = releaseVelocity)
    }

    private fun exitFrame(start: PhotoFrame) = PhotoFrame(thumbnail() ?: start.bounds, 0f, 1f)

    private suspend fun animate(target: PhotoFrame, damping: Float, stiffness: Float, velocity: Velocity = Velocity.Zero) = coroutineScope {
        val start = animatedFrame ?: liveFrame()
        launch {
            Animatable(0f).animateTo(1f, spring(dampingRatio = damping, stiffness = stiffness)) {
                val appearance = interpolate(start, target, value)
                animatedFrame = animatedFrame!!.copy(backdrop = appearance.backdrop, corner = appearance.corner)
            }
        }
        // 平移接续真实的二维松手速度，尺寸从静止开始收回；甩动不会被错误地换算成突然缩放。
        // 达到一个物理像素内即完成，不让肉眼不可见的弹簧尾部继续锁住翻页手势。
        Animatable(start.bounds, Rect.VectorConverter).animateTo(target.bounds,
            spring(dampingRatio = damping, stiffness = stiffness, visibilityThreshold = Rect(1f, 1f, 1f, 1f)),
            initialVelocity = Rect(velocity.x, velocity.y, velocity.x, velocity.y)) {
            animatedFrame = animatedFrame!!.copy(bounds = value)
        }
    }

    private fun interpolate(start: PhotoFrame, target: PhotoFrame, progress: Float) = PhotoFrame(
        lerp(start.bounds, target.bounds, progress),
        (start.backdrop + (target.backdrop - start.backdrop) * progress).coerceIn(0f, 1f),
        (start.corner + (target.corner - start.corner) * progress).coerceIn(0f, 1f),
    )
}
