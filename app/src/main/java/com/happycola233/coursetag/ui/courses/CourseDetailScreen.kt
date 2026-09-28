package com.happycola233.coursetag.ui.courses

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.happycola233.coursetag.data.schedule.WeeklySlot
import com.happycola233.coursetag.data.schedule.weeklySlots
import com.happycola233.coursetag.domain.CourseStatus
import com.happycola233.coursetag.domain.CourseSummary
import com.happycola233.coursetag.domain.RenameRequest
import com.happycola233.coursetag.ui.AppViewModel
import com.happycola233.coursetag.ui.Navigator
import com.happycola233.coursetag.ui.components.SelectionTopBar
import com.happycola233.coursetag.ui.components.BackButton
import com.happycola233.coursetag.ui.components.EmptyState
import com.happycola233.coursetag.ui.components.LocalSnackbarHostState
import com.happycola233.coursetag.ui.components.LongSetSaver
import com.happycola233.coursetag.ui.components.TonalIcon
import com.happycola233.coursetag.ui.components.pageTopBarColors
import com.happycola233.coursetag.ui.formatCount
import com.happycola233.coursetag.ui.formatDay
import com.happycola233.coursetag.ui.formatTime
import com.happycola233.coursetag.ui.formatWeeks
import com.happycola233.coursetag.ui.navigation.ConflictsRoute
import com.happycola233.coursetag.ui.navigation.PhotoViewerRoute
import com.happycola233.coursetag.ui.navigation.RenamePreviewRoute
import com.happycola233.coursetag.ui.photos.PhotoGrid
import com.happycola233.coursetag.ui.photos.PhotoSection
import com.happycola233.coursetag.ui.photos.PhotoTag
import com.happycola233.coursetag.ui.photos.coursePhotosGridKey
import com.happycola233.coursetag.ui.photos.TooltipIcon
import com.happycola233.coursetag.ui.photos.toRequest
import com.happycola233.coursetag.ui.theme.AppSurfaces
import com.happycola233.coursetag.ui.theme.Symbols
import com.happycola233.coursetag.ui.weekdayName
import java.time.Instant
import java.time.ZoneId

@Composable
fun CourseDetailScreen(courseName: String, viewModel: AppViewModel, navigator: Navigator) {
    val courses by viewModel.courses.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val data by viewModel.data.collectAsStateWithLifecycle()
    var rangeSelecting by remember { mutableStateOf(false) }
    // 修改名称后继续停留在本页，展示新名称下的课程。
    var name by rememberSaveable { mutableStateOf(courseName) }
    var pendingName by rememberSaveable { mutableStateOf<String?>(null) }
    var selection by rememberSaveable(saver = LongSetSaver) { mutableStateOf(emptySet<Long>()) }
    var menu by remember { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(canScroll = { !rangeSelecting })

    val summary = courses.firstOrNull { it.name == name }
    LaunchedEffect(summary, pendingName, courses) {
        if (summary == null && library != null) {
            val renamed = pendingName?.takeIf { target -> courses.any { it.name == target } }
            if (renamed != null) {
                name = renamed
                pendingName = null
            } else {
                navigator.back()
            }
        }
    }
    val entries = remember(library, name) { library?.entries?.filter { it.course == name }.orEmpty() }
    val sections = remember(entries) {
        val zone = ZoneId.systemDefault()
        entries.groupBy { Instant.ofEpochMilli(it.photo.takenAt).atZone(zone).toLocalDate() }
            .map { (day, items) -> PhotoSection(day.toString(), formatDay(day), entries = items) }
    }
    val slots = remember(data, summary) {
        summary?.course?.scheduleNames?.let { weeklySlots(data.schedules, it) }.orEmpty()
    }
    val selecting = selection.isNotEmpty() || rangeSelecting
    val selectedEntries = remember(entries, selection) { entries.filter { it.id in selection } }
    BackHandler(enabled = selecting) { selection = emptySet() }

    val detected = summary?.status == CourseStatus.Detected
    val conflictCount = summary?.conflictCount ?: 0

    fun openPreview(request: RenameRequest) {
        selection = emptySet()
        viewModel.preview(request)
        navigator.open(RenamePreviewRoute)
    }

    fun addCourse() {
        viewModel.addCourses(listOf(name))
        viewModel.message("已添加「$name」")
    }

    // 忽略后本课程不再出现在列表中，页面随之返回。
    fun ignoreName() {
        viewModel.ignoreTag(name)
        viewModel.message("已忽略「$name」")
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = AppSurfaces.page,
        snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
        topBar = {
            SelectionTopBar(selecting, selection.size, onCancel = { selection = emptySet() }) {
                MediumFlexibleTopAppBar(
                    title = { Text(name, fontWeight = FontWeight.Bold) },
                    subtitle = { Text(summary.detailDescription(entries.size)) },
                    navigationIcon = { BackButton(navigator::back) },
                    actions = {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Symbols.MoreVert, contentDescription = "更多") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("修改名称") },
                                    leadingIcon = { Icon(Symbols.EditNote, contentDescription = null) },
                                    onClick = {
                                        menu = false
                                        renaming = true
                                    },
                                )
                                if (detected) {
                                    DropdownMenuItem(
                                        text = { Text("添加到课程") },
                                        leadingIcon = { Icon(Symbols.Add, contentDescription = null) },
                                        onClick = {
                                            menu = false
                                            addCourse()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("忽略这个名称") },
                                        leadingIcon = { Icon(Symbols.VisibilityOff, contentDescription = null) },
                                        onClick = {
                                            menu = false
                                            ignoreName()
                                        },
                                    )
                                }
                                if (entries.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("移除全部照片的课程") },
                                        leadingIcon = { Icon(Symbols.LabelOff, contentDescription = null) },
                                        onClick = {
                                            menu = false
                                            openPreview(RenameRequest.Assign("移除课程「$name」", entries.associate { it.id to null }))
                                        },
                                    )
                                }
                                if (summary?.course != null) {
                                    DropdownMenuItem(
                                        text = { Text("删除课程") },
                                        leadingIcon = { Icon(Symbols.Delete, contentDescription = null) },
                                        onClick = {
                                            menu = false
                                            deleting = true
                                        },
                                    )
                                }
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = pageTopBarColors(),
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            PhotoGrid(
                sourceKey = coursePhotosGridKey(name),
                sections = sections,
                selection = selection,
                onRangeSelectionChange = { rangeSelecting = it },
                onSelectionChange = { selection = it },
                onOpen = { entry, ids ->
                    viewModel.viewerPhotoIds = ids
                    navigator.open(PhotoViewerRoute(entry.id, coursePhotosGridKey(name)))
                },
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    bottom = padding.calculateBottomPadding() + 104.dp,
                ),
                // 课程已由页面标题说明，缩略图只标出拍摄于其他课程上课时间的照片。
                tagOf = { entry -> entry.session?.takeIf { entry.conflictsWithSchedule }?.let { PhotoTag(it.course, warning = true) } },
                header = {
                    if (detected) {
                        item(key = "detected", span = { GridItemSpan(maxLineSpan) }) {
                            StatusCard(
                                icon = Symbols.Sell,
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                title = "尚未添加到课程",
                                body = "「$name」出现在 ${formatCount(entries.size)} 张照片的文件名中。" +
                                    "如果它不是课程名称，可以忽略，这些照片将视为未标记。",
                            ) {
                                TextButton(onClick = ::ignoreName) { Text("忽略") }
                                FilledTonalButton(onClick = ::addCourse, shapes = ButtonDefaults.shapes()) { Text("添加到课程") }
                            }
                        }
                    }
                    if (conflictCount > 0) {
                        item(key = "conflicts", span = { GridItemSpan(maxLineSpan) }) {
                            StatusCard(
                                icon = Symbols.Error,
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                title = "${formatCount(conflictCount)} 张照片与上课时间不符",
                                body = "它们拍摄于其他课程的上课时间，已在照片上标出当时的课程",
                            ) {
                                FilledTonalButton(
                                    onClick = { navigator.open(ConflictsRoute(name)) },
                                    shapes = ButtonDefaults.shapes(),
                                ) { Text("核对") }
                            }
                        }
                    }
                    if (slots.isNotEmpty()) {
                        item(key = "slots", span = { GridItemSpan(maxLineSpan) }) { ScheduleSlots(slots) }
                    }
                    if (entries.isEmpty()) {
                        item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                            EmptyState(
                                icon = Symbols.Sell,
                                title = "还没有这门课的照片",
                                body = "在照片页长按选择照片，再标记为「$name」",
                            )
                        }
                    }
                },
            )
            AnimatedVisibility(
                visible = selecting,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec(), initialScale = 0.8f),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    scaleOut(MaterialTheme.motionScheme.fastSpatialSpec(), targetScale = 0.8f),
            ) {
                HorizontalFloatingToolbar(
                    expanded = true,
                    floatingActionButton = {
                        FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = { picking = true }) {
                            Icon(Symbols.SwapHoriz, contentDescription = "更换课程")
                        }
                    },
                    colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                ) {
                    TooltipIcon("移除课程") { label ->
                        IconButton(onClick = {
                            openPreview(RenameRequest.Assign("移除课程「$name」", selectedEntries.associate { it.id to null }))
                        }) { Icon(Symbols.LabelOff, contentDescription = label) }
                    }
                }
            }
        }
    }

    if (picking && selectedEntries.isNotEmpty()) {
        CoursePickerSheet(
            viewModel = viewModel,
            entries = selectedEntries,
            title = "将 ${selectedEntries.size} 张照片改为",
            currentCourse = name,
            onDismiss = { picking = false },
            onPick = { pick ->
                picking = false
                openPreview(pick.toRequest(selectedEntries))
            },
        )
    }
    if (renaming) {
        RenameCourseDialog(
            current = name,
            photoCount = entries.size,
            existing = courses.map { it.name }.toSet(),
            onDismiss = { renaming = false },
            onRename = { newName ->
                renaming = false
                if (viewModel.renameCourse(name, newName)) {
                    pendingName = newName
                    navigator.open(RenamePreviewRoute)
                } else {
                    name = newName
                }
            },
        )
    }
    if (deleting) {
        ConfirmDialog(
            title = "删除「$name」？",
            message = if (entries.isEmpty()) {
                "课程将从列表中移除。"
            } else {
                "课程将从列表中移除，${entries.size} 张照片文件名中的课程保持不变，仍会显示在课程列表中。"
            },
            confirmLabel = "删除",
            onDismiss = { deleting = false },
            onConfirm = {
                deleting = false
                viewModel.deleteCourse(name)
            },
        )
    }
}

/** 课程状态提示卡片：图标、说明与操作按钮。 */
@Composable
private fun StatusCard(
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    title: String,
    body: String,
    actions: @Composable RowScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = AppSurfaces.card,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TonalIcon(icon, containerColor, contentColor)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

private fun CourseSummary?.detailDescription(photoCount: Int): String {
    val photos = if (photoCount == 0) "暂无照片" else "${formatCount(photoCount)} 张照片"
    val status = when (this?.status) {
        CourseStatus.Scheduled -> "课表中的课程"
        CourseStatus.Standalone -> "课表外的课程"
        CourseStatus.Detected -> "尚未添加"
        null -> return photos
    }
    return "$status · $photos"
}

@Composable
private fun ScheduleSlots(slots: List<WeeklySlot>) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = AppSurfaces.card,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Symbols.Schedule, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text("上课时间", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            for (slot in slots) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "${weekdayName(slot.dayOfWeek)} ${formatTime(slot.start)}–${formatTime(slot.end)} · ${formatWeeks(slot.weeks)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val details = listOfNotNull(slot.location, slot.teacher, slot.scheduleName).joinToString(" · ")
                    Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
