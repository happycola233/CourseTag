package com.happycola233.coursetag.ui.courses

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.happycola233.coursetag.data.Schedule
import com.happycola233.coursetag.data.schedule.courseNames
import com.happycola233.coursetag.data.schedule.dateRange
import com.happycola233.coursetag.domain.CourseStatus
import com.happycola233.coursetag.domain.CourseSummary
import com.happycola233.coursetag.domain.IgnoredTag
import com.happycola233.coursetag.domain.PhotoEntry
import com.happycola233.coursetag.ui.AppViewModel
import com.happycola233.coursetag.ui.Navigator
import com.happycola233.coursetag.ui.components.AttentionBadge
import com.happycola233.coursetag.ui.components.CollapsingTopBar
import com.happycola233.coursetag.ui.components.CourseAvatar
import com.happycola233.coursetag.ui.components.EmptyState
import com.happycola233.coursetag.ui.components.GroupGap
import com.happycola233.coursetag.ui.components.GroupedItem
import com.happycola233.coursetag.ui.components.LocalSnackbarHostState
import com.happycola233.coursetag.ui.components.SectionLabel
import com.happycola233.coursetag.ui.components.TonalIcon
import com.happycola233.coursetag.ui.components.rememberScheduleFilePicker
import com.happycola233.coursetag.ui.formatCount
import com.happycola233.coursetag.ui.formatDate
import com.happycola233.coursetag.ui.navigation.ConflictsRoute
import com.happycola233.coursetag.ui.navigation.CourseDetailRoute
import com.happycola233.coursetag.ui.photos.TooltipIcon
import com.happycola233.coursetag.ui.theme.AppSurfaces
import com.happycola233.coursetag.ui.theme.Symbols
import com.happycola233.coursetag.ui.weekdayName
import java.text.Collator
import java.util.Locale

/** 课程列表的筛选：按课程来源分类，另有需要处理的「时间不符」与「已忽略」。 */
private enum class CourseFilter(val label: String) {
    All("全部"),
    Scheduled("课表中"),
    Standalone("课表外"),
    Detected("待添加"),
    Conflicts("时间不符"),
    Ignored("已忽略"),
}

private enum class CourseSort(val label: String) {
    /** 沿用 [com.happycola233.coursetag.domain.courseSummaries] 的默认顺序。 */
    Recent("最近使用"),
    PhotoCount("照片数量"),
    Name("名称"),
}

/** 各来源分类在「全部」中的分组标题与顺序。 */
private val statusSections = listOf(
    CourseStatus.Scheduled to "课表中的课程",
    CourseStatus.Standalone to "课表外的课程",
    CourseStatus.Detected to "文件名中发现的课程",
)

@Composable
fun CoursesScreen(viewModel: AppViewModel, navigator: Navigator) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val courses by viewModel.courses.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val pickSchedule = rememberScheduleFilePicker(viewModel)
    var addDialog by rememberSaveable { mutableStateOf(false) }
    var openedSchedule by rememberSaveable { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf(CourseFilter.All) }
    var sort by rememberSaveable { mutableStateOf(CourseSort.Recent) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    val conflicts = library?.conflicts.orEmpty()
    val ignoredTags = library?.ignoredTags.orEmpty()
    val sortedCourses = remember(courses, sort) { courses.sortedFor(sort) }
    val byStatus = remember(sortedCourses) { sortedCourses.groupBy { it.status } }
    val conflictCourses = remember(sortedCourses) { sortedCourses.filter { it.conflictCount > 0 } }
    val counts = mapOf(
        CourseFilter.All to courses.size,
        CourseFilter.Scheduled to byStatus[CourseStatus.Scheduled].orEmpty().size,
        CourseFilter.Standalone to byStatus[CourseStatus.Standalone].orEmpty().size,
        CourseFilter.Detected to byStatus[CourseStatus.Detected].orEmpty().size,
        CourseFilter.Conflicts to conflictCourses.size,
        CourseFilter.Ignored to ignoredTags.size,
    )

    fun open(summary: CourseSummary) = navigator.open(CourseDetailRoute(summary.name))
    fun add(summary: CourseSummary) {
        viewModel.addCourses(listOf(summary.name))
        viewModel.message("已添加「${summary.name}」")
    }
    fun ignore(summary: CourseSummary) {
        viewModel.ignoreTag(summary.name)
        viewModel.message("已忽略「${summary.name}」")
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = AppSurfaces.page,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
        topBar = {
            CollapsingTopBar(
                title = "课程",
                subtitle = "${courses.size} 门课程 · ${formatCount(library?.taggedCount ?: 0)} 张照片已标记",
                state = scrollBehavior.state,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { addDialog = true },
                expanded = fabExpanded,
                icon = { Icon(Symbols.Add, contentDescription = "添加课程") },
                text = { Text("添加课程") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(GroupGap),
        ) {
            item(key = "filters", contentType = "filters") {
                FilterRow(
                    filter = filter,
                    counts = counts,
                    onFilterChange = { filter = it },
                    sort = sort,
                    onSortChange = { sort = it },
                )
            }
            val rowActions = CourseRowActions(onOpen = ::open, onAdd = ::add, onIgnore = ::ignore)
            when (filter) {
                CourseFilter.All -> {
                    attentionItems(
                        conflicts = conflicts,
                        detectedCount = counts.getValue(CourseFilter.Detected),
                        onReviewConflicts = { navigator.open(ConflictsRoute()) },
                        onShowDetected = { filter = CourseFilter.Detected },
                    )
                    if (courses.isEmpty()) {
                        item(key = "course_label") { SectionLabel("我的课程") }
                        item(key = "course_empty") {
                            GroupedItem(
                                index = 0,
                                count = 1,
                                headline = "还没有课程",
                                supporting = "添加常用课程，或导入课程表自动生成",
                                icon = Symbols.School,
                            )
                        }
                    }
                    for ((status, title) in statusSections) {
                        courseSection(title, byStatus[status].orEmpty(), rowActions)
                    }
                    scheduleSection(
                        schedules = data.schedules,
                        onOpen = { openedSchedule = it },
                        onImport = pickSchedule,
                    )
                }
                CourseFilter.Scheduled, CourseFilter.Standalone, CourseFilter.Detected -> {
                    val status = when (filter) {
                        CourseFilter.Scheduled -> CourseStatus.Scheduled
                        CourseFilter.Standalone -> CourseStatus.Standalone
                        else -> CourseStatus.Detected
                    }
                    val items = byStatus[status].orEmpty()
                    if (items.isEmpty()) {
                        item(key = "empty") { FilterEmptyState(filter, onImportSchedule = pickSchedule) }
                    } else {
                        courseSection(statusSections.first { it.first == status }.second, items, rowActions)
                    }
                }
                CourseFilter.Conflicts -> if (conflicts.isEmpty()) {
                    item(key = "empty") { FilterEmptyState(filter, onImportSchedule = pickSchedule) }
                } else {
                    item(key = "conflict_label") { SectionLabel("需要核对") }
                    item(key = "conflict_review") {
                        ConflictItem(conflicts, index = 0, count = 1) { navigator.open(ConflictsRoute()) }
                    }
                    courseSection("涉及的课程", conflictCourses, rowActions)
                }
                CourseFilter.Ignored -> if (ignoredTags.isEmpty()) {
                    item(key = "empty") { FilterEmptyState(filter, onImportSchedule = pickSchedule) }
                } else {
                    item(key = "ignored_label") { SectionLabel("已忽略的名称") }
                    itemsIndexed(ignoredTags, key = { _, it -> "ignored:${it.name}" }) { index, tag ->
                        IgnoredTagItem(tag, index, ignoredTags.size) {
                            viewModel.restoreTag(tag.name)
                            viewModel.message("已恢复「${tag.name}」")
                        }
                    }
                }
            }
        }
    }

    if (addDialog) {
        AddCoursesDialog(
            existing = courses.filter { it.course != null }.map { it.name }.toSet(),
            onDismiss = { addDialog = false },
            onAdd = { names ->
                viewModel.addCourses(names)
                addDialog = false
                viewModel.message(if (names.size == 1) "已添加「${names.first()}」" else "已添加 ${names.size} 门课程")
            },
        )
    }
    data.schedules.firstOrNull { it.id == openedSchedule }?.let { schedule ->
        ScheduleDetailSheet(
            schedule = schedule,
            viewModel = viewModel,
            onDismiss = { openedSchedule = null },
        )
    }
}

private class CourseRowActions(
    val onOpen: (CourseSummary) -> Unit,
    val onAdd: (CourseSummary) -> Unit,
    val onIgnore: (CourseSummary) -> Unit,
)

@Composable
private fun FilterRow(
    filter: CourseFilter,
    counts: Map<CourseFilter, Int>,
    onFilterChange: (CourseFilter) -> Unit,
    sort: CourseSort,
    onSortChange: (CourseSort) -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            FilterChip(
                selected = sort != CourseSort.Recent,
                onClick = { sortMenu = true },
                label = { Text(sort.label) },
                leadingIcon = { Icon(Symbols.Sort, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) },
                trailingIcon = { Icon(Symbols.ArrowDropDown, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) },
            )
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                for (option in CourseSort.entries) {
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            onSortChange(option)
                            sortMenu = false
                        },
                        trailingIcon = if (option == sort) ({ Icon(Symbols.Check, contentDescription = null) }) else null,
                    )
                }
            }
        }
        for (option in CourseFilter.entries) {
            val count = counts.getValue(option)
            val selected = option == filter
            // 没有内容的分类不占位；当前选中的分类始终保留，处理完最后一项后仍可看到空状态。
            if (option != CourseFilter.All && count == 0 && !selected) continue
            FilterChip(
                selected = selected,
                onClick = { onFilterChange(option) },
                label = { Text(if (option == CourseFilter.All) option.label else "${option.label} ${formatCount(count)}") },
                leadingIcon = if (selected) {
                    { Icon(Symbols.Check, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
            )
        }
    }
}

/** 「全部」顶部的待处理事项：照片与上课时间不符、文件名中出现了新的课程名。 */
private fun LazyListScope.attentionItems(
    conflicts: List<PhotoEntry>,
    detectedCount: Int,
    onReviewConflicts: () -> Unit,
    onShowDetected: () -> Unit,
) {
    val count = listOf(conflicts.isNotEmpty(), detectedCount > 0).count { it }
    if (count == 0) return
    item(key = "attention_label") { SectionLabel("需要处理") }
    if (conflicts.isNotEmpty()) {
        item(key = "attention_conflicts") { ConflictItem(conflicts, index = 0, count = count, onClick = onReviewConflicts) }
    }
    if (detectedCount > 0) {
        item(key = "attention_detected") {
            GroupedItem(
                index = count - 1,
                count = count,
                headline = "文件名中发现 $detectedCount 个新课程",
                supporting = "添加到课程列表，或忽略不是课程的名称",
                leading = {
                    TonalIcon(
                        Symbols.Sell,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                },
                trailing = { Icon(Symbols.ChevronRight, contentDescription = null) },
                onClick = onShowDetected,
            )
        }
    }
}

@Composable
private fun ConflictItem(conflicts: List<PhotoEntry>, index: Int, count: Int, onClick: () -> Unit) {
    val sample = conflicts.first()
    GroupedItem(
        index = index,
        count = count,
        headline = "${formatCount(conflicts.size)} 张照片与上课时间不符",
        supporting = "如「${sample.session!!.course}」课上拍摄的照片，文件名中为「${sample.course}」",
        leading = {
            TonalIcon(
                Symbols.Error,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        },
        trailing = { Icon(Symbols.ChevronRight, contentDescription = null) },
        onClick = onClick,
    )
}

private fun LazyListScope.courseSection(title: String, courses: List<CourseSummary>, actions: CourseRowActions) {
    if (courses.isEmpty()) return
    item(key = "label:$title") { SectionLabel(title) }
    itemsIndexed(courses, key = { _, it -> "course:${it.name}" }) { index, summary ->
        CourseItem(summary, index, courses.size, actions)
    }
}

@Composable
private fun CourseItem(summary: CourseSummary, index: Int, count: Int, actions: CourseRowActions) {
    val detected = summary.status == CourseStatus.Detected
    GroupedItem(
        index = index,
        count = count,
        headline = summary.name,
        supporting = summary.listDescription(),
        leading = { CourseAvatar(summary.name, muted = detected) },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (summary.conflictCount > 0) AttentionBadge("${formatCount(summary.conflictCount)} 张不符")
                if (detected) {
                    TooltipIcon("忽略") { label ->
                        IconButton(onClick = { actions.onIgnore(summary) }) { Icon(Symbols.VisibilityOff, contentDescription = label) }
                    }
                    TooltipIcon("添加到课程") { label ->
                        FilledTonalIconButton(onClick = { actions.onAdd(summary) }) { Icon(Symbols.Add, contentDescription = label) }
                    }
                } else {
                    Icon(Symbols.ChevronRight, contentDescription = null)
                }
            }
        },
        onClick = { actions.onOpen(summary) },
    )
}

@Composable
private fun IgnoredTagItem(tag: IgnoredTag, index: Int, count: Int, onRestore: () -> Unit) {
    GroupedItem(
        index = index,
        count = count,
        headline = tag.name,
        supporting = if (tag.photoCount > 0) "${formatCount(tag.photoCount)} 张照片 · 不作为课程识别" else "不作为课程识别",
        leading = {
            TonalIcon(
                Symbols.VisibilityOff,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailing = { TextButton(onClick = onRestore) { Text("恢复") } },
    )
}

private fun LazyListScope.scheduleSection(
    schedules: List<Schedule>,
    onOpen: (String) -> Unit,
    onImport: () -> Unit,
) {
    item(key = "schedule_label") { SectionLabel("课程表") }
    val rows = schedules.size + 1
    itemsIndexed(schedules, key = { _, it -> "schedule:${it.id}" }) { index, schedule ->
        val range = schedule.dateRange()
        GroupedItem(
            index = index,
            count = rows,
            headline = schedule.name,
            supporting = "${schedule.courseNames().size} 门课程 · ${formatDate(range.start)} – ${formatDate(range.endInclusive)}",
            icon = Symbols.CalendarMonth,
            trailing = { Icon(Symbols.ChevronRight, contentDescription = null) },
            onClick = { onOpen(schedule.id) },
        )
    }
    item(key = "schedule_import") {
        GroupedItem(
            index = schedules.size,
            count = rows,
            headline = if (schedules.isEmpty()) "导入课程表" else "导入其他学期的课程表",
            supporting = if (schedules.isEmpty()) {
                "支持 WakeUp 课程表等应用导出的 .ics 文件，自动识别课上照片"
            } else {
                "往期照片也能按当时的课表识别"
            },
            icon = Symbols.CalendarAddOn,
            onClick = onImport,
        )
    }
}

private data class FilterEmpty(val icon: ImageVector, val title: String, val body: String)

@Composable
private fun FilterEmptyState(filter: CourseFilter, onImportSchedule: () -> Unit) {
    val empty = when (filter) {
        CourseFilter.Scheduled -> FilterEmpty(Symbols.CalendarAddOn, "还没有课表中的课程", "导入课程表后，其中的课程会显示在这里")
        CourseFilter.Standalone -> FilterEmpty(Symbols.School, "所有课程都在课表中", "手动添加、或课表移除后保留的课程会显示在这里")
        CourseFilter.Detected -> FilterEmpty(Symbols.DoneAll, "文件名中的课程都已添加", "照片文件名中出现新的课程名称时会显示在这里")
        CourseFilter.Conflicts -> FilterEmpty(Symbols.DoneAll, "照片课程与上课时间一致", "课上拍摄的照片标记了其他课程时会显示在这里")
        CourseFilter.Ignored -> FilterEmpty(Symbols.VisibilityOff, "没有忽略的名称", "文件名括号中的备注不是课程时，可以在「待添加」中忽略")
        CourseFilter.All -> return
    }
    EmptyState(
        icon = empty.icon,
        title = empty.title,
        body = empty.body,
        action = if (filter == CourseFilter.Scheduled) ({ Button(onClick = onImportSchedule) { Text("导入课程表") } }) else null,
    )
}

private val courseNameCollator: Collator = Collator.getInstance(Locale.CHINA)

private fun List<CourseSummary>.sortedFor(sort: CourseSort): List<CourseSummary> = when (sort) {
    CourseSort.Recent -> this
    CourseSort.PhotoCount -> sortedWith(compareByDescending<CourseSummary> { it.photoCount }.thenBy(courseNameCollator) { it.name })
    CourseSort.Name -> sortedWith(compareBy(courseNameCollator) { it.name })
}

private fun CourseSummary.listDescription(): String {
    val photos = if (photoCount > 0) "${formatCount(photoCount)} 张照片" else "暂无照片"
    return when (status) {
        CourseStatus.Scheduled -> listOf(classDays.joinToString("、") { weekdayName(it) }, photos).joinToString(" · ")
        CourseStatus.Standalone -> photos
        CourseStatus.Detected -> "来自 ${formatCount(photoCount)} 张照片的文件名"
    }
}
