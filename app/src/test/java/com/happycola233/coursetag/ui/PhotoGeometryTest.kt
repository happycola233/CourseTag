package com.happycola233.coursetag.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.happycola233.coursetag.ui.photos.PhotoGeometry
import com.happycola233.coursetag.ui.photos.PhotoTransform
import com.happycola233.coursetag.ui.photos.elasticPhotoScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoGeometryTest {
    private val geometry = PhotoGeometry(Size(400f, 800f), Size(400f, 225f))

    @Test
    fun pinchingKeepsTheImagePointUnderTheMovingFingerIncludingLetterboxing() {
        val start = PhotoTransform(1.7f, Offset(65f, -30f))
        // 焦点特意放在横图上方的留白，不能在捏合中途强行夹到图片边缘。
        val focus = Offset(75f, 100f)
        val pan = Offset(28f, 42f)
        val imagePoint = (focus - geometry.center - start.offset) / start.scale
        val transformed = geometry.scaleAround(start, 3.2f, focus, pan)
        val positionAfter = geometry.center + transformed.offset + imagePoint * transformed.scale
        assertOffsetNear(focus + pan, positionAfter)
    }

    @Test
    fun reversingPinchReturnsToTheSameImagePosition() {
        val focus = Offset(90f, 500f)
        val start = PhotoTransform(2f, Offset(30f, -15f))
        val enlarged = geometry.scaleAround(start, elasticPhotoScale(6f), focus)
        val restored = geometry.scaleAround(enlarged, start.scale, focus)
        assertEquals(start.scale, restored.scale, 0.001f)
        assertOffsetNear(start.offset, restored.offset)
    }

    @Test
    fun scaleLimitsHaveResistanceAndSettleInsideValidBounds() {
        assertTrue(elasticPhotoScale(0.7f) in 0.88f..0.99f)
        assertTrue(elasticPhotoScale(5f) in 4.01f..4.48f)
        assertEquals(4.48f, elasticPhotoScale(100f), 0.001f)
        assertEquals(2f, elasticPhotoScale(2f), 0f)
        assertEquals(PhotoTransform(), geometry.settle(PhotoTransform(0.9f, Offset(80f, 20f)), geometry.center))
        val settled = geometry.settle(PhotoTransform(4.4f, Offset(1000f, -500f)), geometry.center)
        assertEquals(4f, settled.scale, 0f)
        assertOffsetNear(Offset(600f, -50f), settled.offset)
    }

    @Test
    fun singleFingerPanStopsAtTheImageEdge() {
        val dragged = geometry.pan(PhotoTransform(2f), Offset(400f, 120f))
        assertOffsetNear(Offset(200f, 0f), dragged.offset)
        assertOffsetNear(Offset(200f, 0f), geometry.settle(dragged, geometry.center).offset)
    }

    private fun assertOffsetNear(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, 0.001f)
        assertEquals(expected.y, actual.y, 0.001f)
    }
}
