package com.happycola233.coursetag.ui.smart

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
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
import com.happycola233.coursetag.ui.components.PhotoImage
import com.happycola233.coursetag.ui.components.pageTopBarColors
import com.happycola233.coursetag.ui.courses.CoursePick
import com.happycola233.coursetag.ui.courses.CoursePickerSheet
import com.happycola233.coursetag.ui.formatCount
import com.happycola233.coursetag.ui.formatSessionDate
import com.happycola233.coursetag.ui.navigation.PhotoViewerRoute
import com.happycola233.coursetag.ui.navigation.RenamePreviewRoute
import com.happycola233.coursetag.ui.photos.SelectionMark
import com.happycola233.coursetag.ui.theme.AppSurfaces
import com.happycola233.coursetag.ui.theme.Symbols

private data class SessionGroup(val session: ClassSession, val entries: List<PhotoEntry>)

@Composable
fun SmartTagScreen(viewModel: AppViewModel, navigator: Navigator) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val draft by viewModel.smartDraft.collectAsStateWithLifecycle()
    var editingSession by rememberSaveable { mutableStateOf<String?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val groups = remember(library) {
        library?.untaggedClassPhotos.orEmpty()
            .groupBy { it.session!!.key }
            .values
            .map { SessionGroup(it.first().session!!, it) }
            .sortedByDescending { it.session.start }
    }
    val included = groups.sumOf { group -> group.entries.count { it.id !in draft.excluded } }
    val allIds = remember(groups) { groups.flatMap { group -> group.entries.map { it.id } } }

    fun courseOf(group: SessionGroup) = draft.courseOverrides[group.session.key] ?: group.session.course

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = AppSurfaces.page,
        snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("课上照片", fontWeight = FontWeight.Bold) },
                subtitle = { Text("按课程表识别 · ${groups.size} 节课") },
                navigationIcon = { BackButton(navigator::back) },
                actions = {
                    if (allIds.isNotEmpty()) {
                        val allIncluded = draft.excluded.none { it in allIds }
                        IconButton(onClick = { viewModel.setSmartExcluded(allIds, excluded = allIncluded) }) {
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
                        onClick = {
                            val assignments = groups.flatMap { group ->
                                val course = courseOf(group)
                                group.entries.filter { it.id !in draft.excluded }.map { it.id to course }
                            }.toMap()
                            viewModel.preview(RenameRequest.Assign("按课表标记课程", assignments))
                            navigator.open(RenamePreviewRoute)
                        },
                        enabled = included > 0,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 56.dp),
                    ) {
                        Text(
                            if (included > 0) "预览重命名 · ${formatCount(included)} 张" else "请至少选择一张照片",
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
                    title = "课上照片都已标记",
                    body = "之后上课拍摄的照片会自动出现在这里",
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
                    "点按课程名称可以修改课程，点按照片可取消选择，长按照片查看大图。",
                    Modifier.padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(groups, key = { it.session.key }) { group ->
                SessionCard(
                    group = group,
                    course = courseOf(group),
                    edited = group.session.key in draft.courseOverrides,
                    excluded = draft.excluded,
                    onEditCourse = { editingSession = group.session.key },
                    onToggleGroup = { include -> viewModel.setSmartExcluded(group.entries.map { it.id }, excluded = !include) },
                    onTogglePhoto = { id, include -> viewModel.setSmartExcluded(listOf(id), excluded = !include) },
                    onOpenPhoto = { entry ->
                        viewModel.viewerPhotoIds = group.entries.map { it.id }
                        navigator.open(PhotoViewerRoute(entry.id))
                    },
                )
            }
        }
    }

    val editing = groups.firstOrNull { it.session.key == editingSession }
    if (editing != null) {
        CoursePickerSheet(
            viewModel = viewModel,
            entries = editing.entries,
            title = "修改这节课的课程",
            currentCourse = courseOf(editing),
            allowBySchedule = false,
            onDismiss = { editingSession = null },
            onPick = { pick ->
                if (pick is CoursePick.Course) viewModel.overrideSessionCourse(editing.session.key, pick.name)
                editingSession = null
            },
        )
    }
}

@Composable
private fun SessionCard(
    group: SessionGroup,
    course: String,
    edited: Boolean,
    excluded: Set<Long>,
    onEditCourse: () -> Unit,
    onToggleGroup: (Boolean) -> Unit,
    onTogglePhoto: (Long, Boolean) -> Unit,
    onOpenPhoto: (PhotoEntry) -> Unit,
) {
    val includedCount = group.entries.count { it.id !in excluded }
    val state = when (includedCount) {
        0 -> ToggleableState.Off
        group.entries.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    val session = group.session
    Surface(shape = RoundedCornerShape(24.dp), color = AppSurfaces.card, modifier = Modifier.animateContentSize()) {
        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.padding(start = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TriStateCheckbox(state = state, onClick = { onToggleGroup(state != ToggleableState.On) })
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = "修改课程", onClick = onEditCourse)
                        .padding(vertical = 4.dp),
                ) {
                    Text(course, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val details = buildList {
                        add(formatSessionDate(session))
                        session.location?.let(::add)
                    }.joinToString(" · ")
                    Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (edited) {
                        Text(
                            "课表中为「${session.course}」",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                IconButton(onClick = onEditCourse) { Icon(Symbols.Edit, contentDescription = "修改课程") }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(group.entries, key = { it.id }) { entry ->
                    val selected = entry.id !in excluded
                    val alpha by animateFloatAsState(
                        if (selected) 1f else 0.45f,
                        MaterialTheme.motionScheme.fastEffectsSpec(),
                        label = "smart_photo_alpha",
                    )
                    Box(
                        Modifier.size(76.dp)
                            .combinedClickable(
                                onClick = { onTogglePhoto(entry.id, !selected) },
                                onLongClick = { onOpenPhoto(entry) },
                                onLongClickLabel = "查看大图",
                            )
                            .semantics {
                                contentDescription = entry.photo.name
                                role = Role.Checkbox
                                this.selected = selected
                            },
                    ) {
                        PhotoImage(entry.photo.uri, Modifier.size(76.dp).alpha(alpha), RoundedCornerShape(14.dp))
                        SelectionMark(selected, Modifier.align(Alignment.TopEnd).padding(4.dp))
                    }
                }
            }
            Text(
                "已选 $includedCount / ${group.entries.size} 张",
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
