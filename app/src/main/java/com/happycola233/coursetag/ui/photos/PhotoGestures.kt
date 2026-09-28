package com.happycola233.coursetag.ui.photos

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs

private enum class PhotoGesture { Undecided, Pinch, Pan, Dismiss }

/** 动画期间从按下到全部抬起统一消费，不能在动画中途结束后把余下触摸交给点击/翻页识别器。 */
internal fun Modifier.photoInputGate(state: PhotoGestureState, enabled: () -> Boolean): Modifier = pointerInput(state) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (enabled() && !state.blocksNewGestures) return@awaitEachGesture
        down.consume()
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}

/**
 * 只在确定由照片处理时消费位移：原尺寸的横滑留给 Pager，纵向/斜向拖动收起预览。
 * 按住后可以向任意方向拖动关闭；双指一旦接管，直到全部松手都不会误触翻页或关闭。
 */
internal fun Modifier.photoTransformGestures(
    state: PhotoGestureState,
    geometry: () -> PhotoGeometry,
    enabled: () -> Boolean,
    onDismiss: (Velocity) -> Unit,
): Modifier = pointerInput(state) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (down.isConsumed || !enabled() || state.blocksNewGestures) return@awaitEachGesture
        val start = state.transform
        var gesture = PhotoGesture.Undecided
        var rawScale = start.scale
        var totalPan = Offset.Zero
        var lastFocus = down.position
        var released = false
        val velocityTracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
        try {
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.any { it.isConsumed && it.previousPressed && it.pressed }) break
                val pressed = event.changes.count { it.pressed }
                val primary = event.changes.first()
                velocityTracker.addPosition(primary.uptimeMillis, primary.position)
                if (pressed == 0) {
                    released = event.changes.all { it.changedToUpIgnoreConsumed() }
                    break
                }

                val pan = event.calculatePan()
                totalPan += pan
                if (pressed >= 2 && gesture != PhotoGesture.Pinch) {
                    gesture = PhotoGesture.Pinch
                    rawScale = state.transform.scale
                    state.capture()
                }
                if (gesture == PhotoGesture.Undecided && totalPan.getDistance() > viewConfiguration.touchSlop) {
                    val held = primary.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis
                    gesture = when {
                        held -> PhotoGesture.Dismiss
                        state.isZoomed -> PhotoGesture.Pan
                        abs(totalPan.y) > abs(totalPan.x) * 0.8f -> PhotoGesture.Dismiss
                        else -> return@awaitEachGesture
                    }
                    state.capture()
                }

                when (gesture) {
                    PhotoGesture.Pinch -> {
                        val previousFocus = event.calculateCentroid(useCurrent = false)
                        if (previousFocus != Offset.Unspecified) {
                            rawScale *= event.calculateZoom()
                            lastFocus = previousFocus + pan
                            // 捏合过程中不夹紧位移，包含图片留白处的焦点也应始终跟手。
                            state.transformTo(geometry().scaleAround(state.transform, elasticPhotoScale(rawScale), previousFocus, pan))
                        }
                    }
                    PhotoGesture.Pan -> state.transformTo(geometry().pan(start, totalPan))
                    PhotoGesture.Dismiss -> state.dragToDismiss(start, geometry(), down.position, totalPan)
                    PhotoGesture.Undecided -> Unit
                }
                if (gesture != PhotoGesture.Undecided) {
                    event.changes.forEach { it.consume() }
                } else {
                    // 父级已接受横向翻页时，本次触摸不再参与图片手势。
                    if (awaitPointerEvent(PointerEventPass.Final).changes.any { it.isConsumed }) break
                }
            }
        } finally {
            if (gesture == PhotoGesture.Dismiss && released) {
                val velocity = velocityTracker.calculateVelocity()
                state.finishDismiss(geometry(), start, totalPan, Offset(velocity.x, velocity.y), onDismiss)
            } else if (state.captured || geometry().constrain(state.transform) != state.transform) {
                state.settle(geometry(), lastFocus)
            }
        }
    }
}
