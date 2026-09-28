package com.happycola233.coursetag.ui.courses

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.happycola233.coursetag.data.schedule.ClassSession
import com.happycola233.coursetag.domain.PhotoEntry
import com.happycola233.coursetag.domain.RenameRequest
import com.happycola233.coursetag.ui.AppViewModel
import com.happycola233.coursetag.ui.Navigator
import com.happycola233.coursetag.ui.components.BackButton
import com.happycola233.coursetag.ui.components.EmptyState
import com.happycola233.coursetag.ui.components.LocalSnackbarHostState
import com.happycola233.coursetag.ui.components.LongSetSaver
import com.happycola233.coursetag.ui.components.pageTopBarColors
import com.happycola233.coursetag.ui.formatCount
import com.happycola233.coursetag.ui.formatSessionDate
import com.happycola233.coursetag.ui.navigation.PhotoViewerRoute
import com.happycola233.coursetag.ui.navigation.RenamePreviewRoute
import com.happycola233.coursetag.ui.photos.SelectablePhotoRow
import com.happycola233.coursetag.ui.theme.AppSurfaces
import com.happycola233.coursetag.ui.theme.Symbols

/** 同一节课上拍摄、文件名中课程相同的一组不符照片。 */
private data class ConflictGroup(val session: ClassSession, val fileCourse: String, val entries: List<PhotoEntry>) {
    val key: String get() = "${session.key}|$fileCourse"
}

/**
 * 核对拍摄时段与文件名课程不一致的照片：可按课表改为上课的课程，或保留文件名中的课程。
 * 照片默认全部选中，点按取消，操作只作用于选中的照片。
 */
@Composable
fun ConflictsScreen(courseName: String?, viewModel: AppViewModel, navigator: Navigator) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    var excluded by rememberSaveable(saver = LongSetSaver) { mutableStateOf(emptySet<Long>()) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val groups = remember(library, courseName) {
        library?.conflicts.orEmpty()
            .filter { courseName == null || it.course == courseName }
            .groupBy { it.session!!.key to it.course!! }
            .map { (key, entries) -> ConflictGroup(entries.first().session!!, key.second, entries) }
            .sortedByDescending { it.session.start }
    }
    val allEntries = remember(groups) { groups.flatMap { it.entries } }
    val selected = allEntries.filter { it.id !in excluded }

    fun fixBySchedule(entries: List<PhotoEntry>) {
        viewModel.preview(RenameRequest.Assign("按课表修正课程", entries.associate { it.id to it.session!!.course }))
        navigator.open(RenamePreviewRoute)
    }

    fun keep(entries: List<PhotoEntry>) {
        viewModel.keepConflictingCourses(entries)
        viewModel.message("已保留 ${entries.size} 张照片的课程")
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = AppSurfaces.page,
        snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(courseName?.let { "核对「$it」" } ?: "核对课程", fontWeight = FontWeight.Bold) },
                subtitle = { Text("${formatCount(allEntries.size)} 张照片与上课时间不符") },
                navigationIcon = { BackButton(navigator::back) },
                actions = {
                    if (allEntries.isNotEmpty()) {
                        val allIncluded = allEntries.none { it.id in excluded }
                        IconButton(onClick = { excluded = if (allIncluded) allEntries.map { it.id }.toSet() else emptySet() }) {
                            Icon(Symbols.SelectAll, contentDescription = if (allIncluded) "全部取消" else "全选")
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = pageTopBarColors(),
            )
        },
        bottomBar = {
            if (groups.isNotEmpty()) {
                Surface(color = AppSurfaces.page) {
                    Button(
                        onClick = { fixBySchedule(selected) },
                        enabled = selected.isNotEmpty(),
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 56.dp),
                    ) {
                        Text(
                            if (selected.isNotEmpty()) "全部按课表修正 · ${formatCount(selected.size)} 张" else "请至少选择一张照片",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (library != null && groups.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center) {
                EmptyState(
                    icon = Symbols.DoneAll,
                    title = "照片课程与上课时间一致",
                    body = "课上拍摄的照片标记了其他课程时，会显示在这里",
                    action = { Button(onClick = navigator::back) { Text("返回") } },
                )
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "hint") {
                Text(
                    "这些照片拍摄于某节课的上课时间，文件名中却是另一门课程。确实属于文件名中的课程时可以保留，点按照片可取消选择，长按查看大图。",
                    Modifier.padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(groups, key = { it.key }) { group ->
                val groupSelected = group.entries.filter { it.id !in excluded }
                ConflictCard(
                    group = group,
                    selectedCount = groupSelected.size,
                    excluded = excluded,
                    onToggleGroup = { include ->
                        val ids = group.entries.map { it.id }
                        excluded = if (include) excluded - ids.toSet() else excluded + ids
                    },
                    onTogglePhoto = { id, include -> excluded = if (include) excluded - id else excluded + id },
                    onOpenPhoto = { entry ->
                        viewModel.viewerPhotoIds = group.entries.map { it.id }
                        navigator.open(PhotoViewerRoute(entry.id))
                    },
                    onKeep = { keep(groupSelected) },
                    onFix = { fixBySchedule(groupSelected) },
                )
            }
        }
    }
}

@Composable
private fun ConflictCard(
    group: ConflictGroup,
    selectedCount: Int,
    excluded: Set<Long>,
    onToggleGroup: (Boolean) -> Unit,
    onTogglePhoto: (Long, Boolean) -> Unit,
    onOpenPhoto: (PhotoEntry) -> Unit,
    onKeep: () -> Unit,
    onFix: () -> Unit,
) {
    val state = when (selectedCount) {
        0 -> ToggleableState.Off
        group.entries.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    val session = group.session
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(24.dp), color = AppSurfaces.card, modifier = Modifier.animateContentSize()) {
        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                TriStateCheckbox(state = state, onClick = { onToggleGroup(state != ToggleableState.On) })
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // 左侧为文件名中的课程，右侧为课表中这节课的课程。
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        CoursePill("文件名", group.fileCourse, colors.errorContainer, colors.onErrorContainer)
                        Icon(Symbols.ArrowForward, contentDescription = "课表中为", Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                        CoursePill("课表", session.course, colors.primaryContainer, colors.onPrimaryContainer)
                    }
                    val details = listOfNotNull(formatSessionDate(session), session.location).joinToString(" · ")
                    Text(details, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            SelectablePhotoRow(
                entries = group.entries,
                excluded = excluded,
                onToggle = onTogglePhoto,
                onOpen = onOpenPhoto,
            )
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "已选 $selectedCount / ${group.entries.size} 张",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                TextButton(onClick = onKeep, enabled = selectedCount > 0) { Text("保留") }
                FilledTonalButton(onClick = onFix, enabled = selectedCount > 0, shapes = ButtonDefaults.shapes()) {
                    Text("改为「${session.course}」", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** 带说明文字的课程标签，例如「文件名 · 高等数学」。 */
@Composable
private fun CoursePill(label: String, course: String, containerColor: Color, contentColor: Color) {
    Surface(shape = CircleShape, color = containerColor, contentColor = contentColor) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(course, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
