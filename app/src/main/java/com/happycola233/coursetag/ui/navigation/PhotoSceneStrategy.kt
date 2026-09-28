package com.happycola233.coursetag.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import com.happycola233.coursetag.ui.photos.LocalPhotoGridReturnState
import com.happycola233.coursetag.ui.photos.LocalPhotoPreviewMotion
import com.happycola233.coursetag.ui.photos.PhotoPreviewMotion

private object PhotoSceneKey : NavMetadataKey<Boolean>
internal val PhotoViewerSceneMetadata = metadata { put(PhotoSceneKey, true) }

internal class PhotoSceneStrategy<T : Any> : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val entry = entries.lastOrNull() ?: return null
        return if (entry.metadata[PhotoSceneKey] == true) PhotoScene(entry, entries.dropLast(1)) else null
    }
}

/** 同窗口叠层保留网格和预览的统一坐标系；照片与模糊采样也在同一绘制树中。 */
private data class PhotoScene<T : Any>(
    val entry: NavEntry<T>,
    override val previousEntries: List<NavEntry<T>>,
) : OverlayScene<T> {
    override val key: Any = entry.contentKey
    override val entries = listOf(entry)
    override val overlaidEntries = previousEntries
    private var motion: PhotoPreviewMotion? = null
    override val content: @Composable () -> Unit = {
        val scope = rememberCoroutineScope()
        val previewMotion = remember { PhotoPreviewMotion(scope) }
        val grid = LocalPhotoGridReturnState.current
        DisposableEffect(previewMotion) {
            motion = previewMotion
            onDispose { grid?.reveal(); motion = null }
        }
        CompositionLocalProvider(LocalPhotoPreviewMotion provides previewMotion) {
            Box(Modifier.fillMaxSize()) { entry.Content() }
        }
    }
    override suspend fun onRemove() { motion?.close() }
}
