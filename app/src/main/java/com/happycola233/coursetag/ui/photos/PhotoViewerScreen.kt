package com.happycola233.coursetag.ui.photos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.happycola233.coursetag.domain.PhotoEntry
import com.happycola233.coursetag.domain.RenameRequest
import com.happycola233.coursetag.ui.AppViewModel
import com.happycola233.coursetag.ui.Navigator
import com.happycola233.coursetag.ui.components.BackButton
import com.happycola233.coursetag.ui.components.CourseAvatar
import com.happycola233.coursetag.ui.components.LocalSnackbarHostState
import com.happycola233.coursetag.ui.courses.CoursePickerSheet
import com.happycola233.coursetag.ui.formatDateTime
import com.happycola233.coursetag.ui.formatSessionTime
import com.happycola233.coursetag.ui.navigation.RenamePreviewRoute
import com.happycola233.coursetag.ui.theme.AppSurfaces
import com.happycola233.coursetag.ui.theme.Symbols

@Composable
fun PhotoViewerScreen(photoId: Long, viewModel: AppViewModel, navigator: Navigator) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val ids = remember { viewModel.viewerPhotoIds.takeIf { photoId in it } ?: listOf(photoId) }
    // 按照片 ID 取最新状态，重命名后停留在原位置并显示新的课程。
    val entries = remember(library, ids) { ids.mapNotNull { library?.byId?.get(it) } }
    val background = MaterialTheme.colorScheme.surfaceContainerLowest

    if (library == null) {
        Box(Modifier.fillMaxSize().background(background)) {
            LoadingIndicator(Modifier.align(Alignment.Center).size(64.dp))
        }
        return
    }
    if (entries.isEmpty()) {
        LaunchedEffect(Unit) { navigator.back() }
        return
    }

    val pagerState = rememberPagerState(initialPage = entries.indexOfFirst { it.id == photoId }.coerceAtLeast(0)) {
        entries.size
    }
    val current = entries[pagerState.currentPage.coerceIn(0, entries.lastIndex)]
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var picking by rememberSaveable { mutableStateOf(false) }

    fun openPreview(request: RenameRequest) {
        viewModel.preview(request)
        navigator.open(RenamePreviewRoute)
    }

    Box(Modifier.fillMaxSize().background(background)) {
        HorizontalPager(
            state = pagerState,
            key = { entries[it].id },
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            ZoomableImage(entries[page], onTap = { chromeVisible = !chromeVisible })
        }
        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { -it / 2 },
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                slideOutVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { -it / 2 },
        ) {
            TopAppBar(
                title = {
                    Column {
                        Text(current.photo.name, maxLines = 1, overflow = TextOverflow.MiddleEllipsis, fontWeight = FontWeight.Bold)
                        if (entries.size > 1) {
                            Text(
                                "${pagerState.currentPage + 1} / ${entries.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = { BackButton(navigator::back) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background.copy(alpha = 0.88f)),
            )
        }
        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 2 },
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                slideOutVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 2 },
        ) {
            InfoPanel(
                entry = current,
                onTag = { picking = true },
                onRemove = { openPreview(RenameRequest.Assign("移除课程", mapOf(current.id to null))) },
            )
        }
        SnackbarHost(
            LocalSnackbarHostState.current,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 160.dp),
        )
    }

    if (picking) {
        CoursePickerSheet(
            viewModel = viewModel,
            entries = listOf(current),
            title = if (current.course == null) "为这张照片选择课程" else "更换这张照片的课程",
            currentCourse = current.course,
            allowBySchedule = false,
            onDismiss = { picking = false },
            onPick = { pick ->
                picking = false
                openPreview(pick.toRequest(listOf(current)))
            },
        )
    }
}

@Composable
private fun InfoPanel(entry: PhotoEntry, onTag: () -> Unit, onRemove: () -> Unit) {
    val course = entry.course
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = AppSurfaces.card,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.navigationBarsPadding().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (course != null) {
                    CourseAvatar(course)
                } else {
                    Icon(Symbols.Sell, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.weight(1f)) {
                    Text(course ?: "未标记课程", style = MaterialTheme.typography.titleMedium)
                    val suggestion = entry.session?.takeIf { it.course != course }?.course
                    if (suggestion != null) {
                        Text(
                            "课表建议：$suggestion",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (course != null) {
                    IconButton(onClick = onRemove) { Icon(Symbols.LabelOff, contentDescription = "移除课程") }
                }
                FilledTonalButton(onClick = onTag, shapes = ButtonDefaults.shapes()) {
                    Text(if (course == null) "标记课程" else "更换")
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DetailLine(Symbols.Schedule, "${formatDateTime(entry.photo.takenAt)} · ${entry.photo.albumName}")
                entry.session?.let { session ->
                    val place = session.location?.let { " · $it" }.orEmpty()
                    DetailLine(Symbols.CalendarMonth, "${session.course} · ${formatSessionTime(session)}$place")
                }
            }
        }
    }
}

@Composable
private fun DetailLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 双指缩放、双击放大；未放大时横向拖动交给翻页处理。 */
@Composable
private fun ZoomableImage(entry: PhotoEntry, onTap: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    fun clamp(value: Offset, zoom: Float): Offset {
        val maxX = size.width * (zoom - 1) / 2
        val maxY = size.height * (zoom - 1) / 2
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    // 以双指中心为锚点缩放：手指下的画面位置保持不变，再叠加平移。
    val state = rememberTransformableState { centroid, zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
        val zoom = nextScale / scale
        val center = Offset(size.width / 2f, size.height / 2f)
        scale = nextScale
        offset = clamp(offset * zoom + (centroid - center) * (1 - zoom) + panChange, nextScale)
    }
    Box(
        Modifier.fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { point ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            val center = Offset(size.width / 2f, size.height / 2f)
                            offset = clamp((center - point) * 1.5f, scale)
                        }
                    },
                )
            }
            .transformable(state, canPan = { scale > 1f }),
    ) {
        AsyncImage(
            model = entry.photo.uri,
            contentDescription = entry.photo.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        )
    }
}
