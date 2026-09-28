package com.happycola233.coursetag.ui.photos

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class PhotoTransform(val scale: Float = 1f, val offset: Offset = Offset.Zero)

/** 缩放的几何计算独立于手势识别；尺寸均为屏幕像素。 */
internal data class PhotoGeometry(val viewport: Size, val fittedImage: Size) {
    val center: Offset get() = Offset(viewport.width / 2, viewport.height / 2)

    fun scaleAround(current: PhotoTransform, scale: Float, focus: Offset, pan: Offset = Offset.Zero): PhotoTransform {
        val ratio = scale / current.scale
        return PhotoTransform(scale, current.offset * ratio + (focus - center) * (1 - ratio) + pan)
    }

    fun constrain(transform: PhotoTransform): PhotoTransform {
        val scale = transform.scale.coerceIn(1f, MaxPhotoScale)
        if (scale <= 1.02f) return PhotoTransform()
        val bounds = panBounds(scale)
        return PhotoTransform(scale, Offset(
            transform.offset.x.coerceIn(-bounds.x, bounds.x),
            transform.offset.y.coerceIn(-bounds.y, bounds.y),
        ))
    }

    fun settle(transform: PhotoTransform, focus: Offset): PhotoTransform {
        if (transform.scale <= 1.02f) return PhotoTransform()
        return constrain(scaleAround(transform, transform.scale.coerceAtMost(MaxPhotoScale), focus))
    }

    fun pan(start: PhotoTransform, distance: Offset): PhotoTransform = constrain(start.copy(offset = start.offset + distance))

    private fun panBounds(scale: Float) = Offset(
        ((fittedImage.width * scale - viewport.width) / 2).coerceAtLeast(0f),
        ((fittedImage.height * scale - viewport.height) / 2).coerceAtLeast(0f),
    )
}

internal const val MaxPhotoScale = 4f

/** 与 snapseam 的预览一致：原始倍率只计算一次阻尼，不把已阻尼的倍率再次输入阻尼。 */
internal fun elasticPhotoScale(rawScale: Float): Float = when {
    rawScale < 1f -> (1f - resistedOvershoot(1f - rawScale)).coerceAtLeast(0.88f)
    rawScale > MaxPhotoScale -> (MaxPhotoScale + resistedOvershoot(rawScale - MaxPhotoScale)).coerceAtMost(4.48f)
    else -> rawScale
}

private fun resistedOvershoot(excess: Float) = excess * 0.42f / (1f + excess * 0.55f)

@Stable
internal class PhotoGestureState(private val scope: CoroutineScope) {
    var transform by mutableStateOf(PhotoTransform())
        private set
    var captured by mutableStateOf(false)
        private set
    var dismissProgress by mutableFloatStateOf(0f)
        private set
    var dismissCommitted by mutableStateOf(false)
        private set
    private var animator by mutableStateOf<Animatable<Float, *>?>(null)
    private var animationJob: Job? = null
    val isZoomed: Boolean get() = transform.scale > 1.02f
    val blocksNewGestures: Boolean get() = animator != null || dismissCommitted
    val locksPaging: Boolean get() = isZoomed || captured || animator != null || dismissCommitted
    fun interrupt() {
        animationJob?.cancel()
        animator = null
    }

    fun capture() {
        interrupt()
        captured = true
    }

    fun transformTo(value: PhotoTransform) {
        transform = value
        dismissProgress = 0f
    }

    fun doubleTap(geometry: PhotoGeometry, focus: Offset) {
        if (blocksNewGestures || captured) return
        val target = if (isZoomed) PhotoTransform() else geometry.constrain(geometry.scaleAround(transform, 2.5f, focus))
        // 与参考相册一致：双击采用单调的三次缓动，动画期间忽略新触摸，不排队反向缩放。
        animateTo(target, spec = if (isZoomed) tween(220, easing = CubicBezierEasing(0.645f, 0.045f, 0.355f, 1f))
            else tween(260, easing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)))
    }

    fun dragToDismiss(start: PhotoTransform, geometry: PhotoGeometry, focus: Offset, pan: Offset) {
        dismissProgress = (pan.getDistance() / (geometry.viewport.minDimension * 0.7f)).coerceIn(0f, 1f)
        transform = geometry.scaleAround(start, start.scale * (1f - 0.22f * dismissProgress), focus, pan)
    }

    fun finishDismiss(geometry: PhotoGeometry, start: PhotoTransform, pan: Offset, velocity: Offset, onDismiss: (Velocity) -> Unit) {
        captured = false
        val distance = pan.getDistance()
        val extent = geometry.viewport.minDimension
        val flingingAway = velocity.getDistance() > extent * 2.5f && velocity.x * pan.x + velocity.y * pan.y > 0f
        if (distance > extent * 0.28f || (distance > extent * 0.08f && flingingAway)) {
            dismissCommitted = true
            onDismiss(Velocity(velocity.x, velocity.y))
        } else {
            animateTo(start, velocity)
        }
    }

    fun settle(geometry: PhotoGeometry, focus: Offset) {
        captured = false
        val overscaled = transform.scale < 1f || transform.scale > MaxPhotoScale
        animateTo(geometry.settle(transform, focus), spec = if (overscaled)
            spring(dampingRatio = 0.82f, stiffness = 500f) else tween(250, easing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)))
    }

    private fun animateTo(
        target: PhotoTransform,
        releaseVelocity: Offset = Offset.Zero,
        spec: AnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = 500f),
    ) {
        interrupt()
        val start = transform
        val initialDismissProgress = dismissProgress
        if (start == target) {
            dismissProgress = 0f
            return
        }
        val nextAnimator = Animatable(0f)
        val displacement = target.offset - start.offset
        val distanceSquared = displacement.getDistanceSquared()
        val initialVelocity = if (distanceSquared > 0f) {
            (releaseVelocity.x * displacement.x + releaseVelocity.y * displacement.y) / distanceSquared
        } else 0f
        animator = nextAnimator
        animationJob = scope.launch {
            try {
                // 位移与倍率共用进度，避免缩放中心在动画途中漂移。
                nextAnimator.animateTo(1f, spec, initialVelocity) {
                    transform = PhotoTransform(
                        scale = start.scale + (target.scale - start.scale) * value,
                        offset = lerp(start.offset, target.offset, value),
                    )
                    dismissProgress = initialDismissProgress * (1f - value).coerceIn(0f, 1f)
                }
                transform = target
                dismissProgress = 0f
            } finally {
                if (animator === nextAnimator) animator = null
            }
        }
    }
}
