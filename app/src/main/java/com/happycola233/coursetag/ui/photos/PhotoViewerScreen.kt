package com.happycola233.coursetag.ui.photos

import androidx.activity.compose.PredictiveBackHandler
import kotlinx.coroutines.CancellationException
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.happycola233.coursetag.domain.PhotoEntry
import com.happycola233.coursetag.domain.RenameRequest
import com.happycola233.coursetag.ui.AppViewModel
import com.happycola233.coursetag.ui.Navigator
import com.happycola233.coursetag.ui.components.CourseAvatar
import com.happycola233.coursetag.ui.components.LocalSnackbarHostState
import com.happycola233.coursetag.ui.courses.CoursePickerSheet
import com.happycola233.coursetag.ui.formatDateTime
import com.happycola233.coursetag.ui.formatSessionTime
import com.happycola233.coursetag.ui.navigation.RenamePreviewRoute
import com.happycola233.coursetag.ui.theme.Symbols
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun PhotoViewerScreen(photoId: Long, viewModel: AppViewModel, navigator: Navigator, sourceKey: String? = null) {
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

    var picking by rememberSaveable { mutableStateOf(false) }

    fun openPreview(request: RenameRequest) {
        viewModel.preview(request)
        navigator.open(RenamePreviewRoute)
    }

    PhotoViewerContent(
        entries = entries,
        pagerState = pagerState,
        sourceKey = sourceKey,
        onBack = navigator::back,
        onTag = { picking = true },
        onRemove = { openPreview(RenameRequest.Assign("移除课程", mapOf(current.id to null))) },
    )
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
internal fun PhotoViewerContent(
    entries: List<PhotoEntry>,
    pagerState: PagerState,
    sourceKey: String?,
    onBack: () -> Unit,
    onTag: () -> Unit,
    onRemove: () -> Unit,
) {
    val current = entries[pagerState.currentPage.coerceAtMost(entries.lastIndex)]
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    val motion = LocalPhotoPreviewMotion.current
    fun close() { if (motion?.requestClose() != false) onBack() }
    // 退出动画仍在屏幕上时继续接收返回，重复操作由 motion 合并，不能穿透到底下的页面。
    PredictiveBackHandler { events ->
        motion?.beginBack()
        try {
            events.collect { motion?.seekBack(it.progress) }
            close()
        } catch (cancelled: CancellationException) {
            motion?.cancelBack()
            throw cancelled
        }
    }
    fun backdrop() = motion?.frame?.backdrop ?: 1f
    fun chromeAlpha() = ((backdrop() - 0.3f) / 0.7f).coerceIn(0f, 1f)
    val background = MaterialTheme.colorScheme.surfaceContainerLowest
    val hazeState = rememberHazeState()
    val chromeStyle = HazeStyle(
        backgroundColor = background,
        tint = HazeTint(MaterialTheme.colorScheme.surface.copy(alpha = 0.58f)),
        blurRadius = 24.dp,
        noiseFactor = 0f,
        fallbackTint = HazeTint(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
    )
    Box(Modifier.fillMaxSize().drawBehind { drawRect(background.copy(alpha = backdrop())) }) {
        PhotoPager(
            entries, pagerState, sourceKey,
            onTap = { chromeVisible = !chromeVisible },
            onDismiss = { close() },
            modifier = Modifier.hazeSource(hazeState),
        )
        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.TopCenter)
                .graphicsLayer { alpha = chromeAlpha() },
            enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                slideInVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { -it / 3 },
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            TopAppBar(
                title = {
                    Text(current.photo.name, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                },
                navigationIcon = {
                    IconButton(onClick = { close() }) { Icon(Symbols.Close, contentDescription = "关闭预览") }
                },
                actions = {
                    if (entries.size > 1) Text(
                        "${pagerState.currentPage + 1} / ${entries.size}",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier.frostedPhotoBar(hazeState, chromeStyle),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.BottomCenter)
                .graphicsLayer { alpha = chromeAlpha() },
            enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                slideInVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it / 3 },
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            Column(Modifier.frostedPhotoBar(hazeState, chromeStyle)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                InfoPanel(current, onTag, onRemove)
            }
        }
        SnackbarHost(
            LocalSnackbarHostState.current,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 160.dp),
        )
    }
}

@OptIn(ExperimentalHazeApi::class)
private fun Modifier.frostedPhotoBar(state: HazeState, style: HazeStyle): Modifier =
    clipToBounds().hazeEffect(state, style) {
        // 背景只需要低频轮廓，降采样模糊避免拖动/缩放时做全分辨率滤镜。
        inputScale = HazeInputScale.Fixed(0.5f)
    }

@Composable
internal fun PhotoPager(
    entries: List<PhotoEntry>,
    pagerState: PagerState,
    sourceKey: String?,
    onTap: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalPhotoPreviewMotion.current
    val transitioning = motion != null && !motion.gesturesEnabled
    val currentPhotoId = entries[pagerState.currentPage.coerceAtMost(entries.lastIndex)].id
    var pagingLocked by remember(currentPhotoId) { mutableStateOf(false) }
    HorizontalPager(
        state = pagerState,
        key = { entries[it].id },
        // 沿用 Pager 按滑动方向预取，避免在进入/退出转场时集中创建、销毁相邻大图。
        userScrollEnabled = !transitioning && !pagingLocked,
        modifier = modifier.fillMaxSize(),
    ) { page ->
        ZoomablePhoto(
            entry = entries[page],
            sourceKey = sourceKey,
            active = entries[page].id == currentPhotoId,
            gesturesEnabled = !transitioning && !pagerState.isScrollInProgress,
            onFreezePaging = {
                if (pagerState.isScrollInProgress) pagerState.requestScrollToPage(pagerState.currentPage)
            },
            onTap = onTap,
            onDismiss = onDismiss,
            onInteractionChanged = { pagingLocked = it },
        )
    }
}

@Composable
private fun InfoPanel(entry: PhotoEntry, onTag: () -> Unit, onRemove: () -> Unit) {
    val course = entry.course
    Surface(
        shape = RectangleShape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
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
