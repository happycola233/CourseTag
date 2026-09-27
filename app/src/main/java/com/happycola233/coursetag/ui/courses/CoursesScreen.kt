package com.happycola233.coursetag.ui.courses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.happycola233.coursetag.data.schedule.courseNames
import com.happycola233.coursetag.data.schedule.dateRange
import com.happycola233.coursetag.domain.CourseSummary
import com.happycola233.coursetag.ui.AppViewModel
import com.happycola233.coursetag.ui.Navigator
import com.happycola233.coursetag.ui.components.CourseAvatar
import com.happycola233.coursetag.ui.components.GroupGap
import com.happycola233.coursetag.ui.components.GroupedItem
import com.happycola233.coursetag.ui.components.LocalSnackbarHostState
import com.happycola233.coursetag.ui.components.SectionLabel
import com.happycola233.coursetag.ui.components.pageTopBarColors
import com.happycola233.coursetag.ui.components.rememberScheduleFilePicker
import com.happycola233.coursetag.ui.formatCount
import com.happycola233.coursetag.ui.formatDate
import com.happycola233.coursetag.ui.navigation.CourseDetailRoute
import com.happycola233.coursetag.ui.theme.AppSurfaces
import com.happycola233.coursetag.ui.theme.Symbols

@Composable
fun CoursesScreen(viewModel: AppViewModel, navigator: Navigator) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val courses by viewModel.courses.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val pickSchedule = rememberScheduleFilePicker(viewModel)
    var addDialog by rememberSaveable { mutableStateOf(false) }
    var openedSchedule by rememberSaveable { mutableStateOf<String?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = AppSurfaces.page,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("课程") },
                subtitle = {
                    Text("${courses.size} 门课程 · ${formatCount(library?.taggedCount ?: 0)} 张照片已标记")
                },
                scrollBehavior = scrollBehavior,
                colors = pageTopBarColors(),
            )
        },
        floatingActionButton = {
            MediumExtendedFloatingActionButton(
                onClick = { addDialog = true },
                expanded = fabExpanded,
                icon = { Icon(Symbols.Add, contentDescription = null) },
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
                bottom = 112.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(GroupGap),
        ) {
            item(key = "schedule_label") { SectionLabel("课程表", Modifier) }
            val schedules = data.schedules
            val scheduleRows = schedules.size + 1
            itemsIndexed(schedules, key = { _, it -> "schedule:${it.id}" }) { index, schedule ->
                val range = schedule.dateRange()
                GroupedItem(
                    index = index,
                    count = scheduleRows,
                    headline = schedule.name,
                    supporting = "${schedule.courseNames().size} 门课程 · ${formatDate(range.start)} – ${formatDate(range.endInclusive)}",
                    icon = Symbols.CalendarMonth,
                    trailing = { Icon(Symbols.ChevronRight, contentDescription = null) },
                    onClick = { openedSchedule = schedule.id },
                )
            }
            item(key = "schedule_import") {
                GroupedItem(
                    index = schedules.size,
                    count = scheduleRows,
                    headline = if (schedules.isEmpty()) "导入课程表" else "导入其他学期的课程表",
                    supporting = if (schedules.isEmpty()) {
                        "支持 WakeUp 课程表等应用导出的 .ics 文件，自动识别课上照片"
                    } else {
                        "往期照片也能按当时的课表识别"
                    },
                    icon = Symbols.CalendarAddOn,
                    onClick = pickSchedule,
                )
            }
            item(key = "course_label") { SectionLabel("我的课程") }
            if (courses.isEmpty()) {
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
            itemsIndexed(courses, key = { _, it -> "course:${it.name}" }) { index, summary ->
                GroupedItem(
                    index = index,
                    count = courses.size,
                    headline = summary.name,
                    supporting = summary.listDescription(),
                    leading = { CourseAvatar(summary.name) },
                    trailing = { Icon(Symbols.ChevronRight, contentDescription = null) },
                    onClick = { navigator.open(CourseDetailRoute(summary.name)) },
                )
            }
        }
    }

    if (addDialog) {
        AddCoursesDialog(
            existing = courses.map { it.name }.toSet(),
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

private fun CourseSummary.listDescription(): String = buildList {
    if (course == null) add("未保存")
    add(if (photoCount > 0) "${formatCount(photoCount)} 张照片" else "暂无照片")
    if (linkedToSchedule) add("课表课程")
}.joinToString(" · ")
