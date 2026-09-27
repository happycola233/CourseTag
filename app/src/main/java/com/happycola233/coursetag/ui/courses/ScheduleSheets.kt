package com.happycola233.coursetag.ui.courses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.happycola233.coursetag.data.Schedule
import com.happycola233.coursetag.data.naming.FileNames
import com.happycola233.coursetag.data.schedule.ClassIndex
import com.happycola233.coursetag.data.schedule.courseNames
import com.happycola233.coursetag.data.schedule.dateRange
import com.happycola233.coursetag.ui.AppViewModel
import com.happycola233.coursetag.ui.components.CourseAvatar
import com.happycola233.coursetag.ui.formatDate
import com.happycola233.coursetag.ui.formatWeeks
import com.happycola233.coursetag.ui.theme.Symbols
import java.time.Instant

/** 导入确认：可修改课表名称、取消勾选不需要的课程（例如混在日历中的非课程日程）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleImportSheet(viewModel: AppViewModel) {
    val draft by viewModel.importDraft.collectAsStateWithLifecycle()
    val data by viewModel.data.collectAsStateWithLifecycle()
    val current = draft ?: return
    var name by rememberSaveable(current) { mutableStateOf(current.suggestedName) }
    var excluded by remember(current) { mutableStateOf(emptySet<String>()) }
    val sheetState = rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val zone = current.calendar.zone
    val meetings = current.calendar.meetings
    val first = Instant.ofEpochMilli(meetings.first().start).atZone(zone).toLocalDate()
    val last = Instant.ofEpochMilli(meetings.maxOf { it.end }).atZone(zone).toLocalDate()
    val trimmedName = name.trim()
    val replacing = data.schedules.any { it.name == trimmedName }
    val included = current.courses.map { it.scheduleName }.filter { it !in excluded }.toSet()

    ModalBottomSheet(onDismissRequest = viewModel::dismissImport, sheetState = sheetState) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("导入课程表", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${current.courses.size} 门课程 · ${meetings.size} 节课 · ${formatDate(first)} – ${formatDate(last)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("课表名称") },
                singleLine = true,
                supportingText = if (replacing) ({ Text("将替换已导入的「$trimmedName」") }) else null,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("导入的课程", style = MaterialTheme.typography.titleSmall)
                Text(
                    "点按可取消不需要的课程，课程名称导入后仍可修改",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (course in current.courses) {
                    val selected = course.scheduleName !in excluded
                    FilterChip(
                        selected = selected,
                        onClick = {
                            excluded = if (selected) excluded + course.scheduleName else excluded - course.scheduleName
                        },
                        label = { Text(course.courseName) },
                        leadingIcon = if (selected) {
                            { Icon(Symbols.Check, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
                        } else {
                            null
                        },
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = viewModel::dismissImport) { Text("取消") }
                Button(
                    onClick = { viewModel.confirmImport(trimmedName, included) },
                    enabled = trimmedName.isNotEmpty() && included.isNotEmpty(),
                    shapes = ButtonDefaults.shapes(),
                ) { Text(if (replacing) "替换导入" else "导入") }
            }
        }
    }
}

/** 已导入课表的详情：课程、教学周与管理操作。 */
@Composable
fun ScheduleDetailSheet(schedule: Schedule, viewModel: AppViewModel, onDismiss: () -> Unit) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    var renaming by rememberSaveable { mutableStateOf(false) }
    var removing by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val range = schedule.dateRange()
    val weeks = ClassIndex.weekOf(schedule, schedule.meetings.maxOf { it.start })
    val rows = remember(schedule, data.courses) {
        schedule.courseNames().map { scheduleName ->
            val meetings = schedule.meetings.filter { it.course == scheduleName }
            val name = data.courses.firstOrNull { scheduleName in it.scheduleNames }?.name
                ?: FileNames.sanitizeCourseName(scheduleName)
            val courseWeeks = meetings.map { ClassIndex.weekOf(schedule, it.start) }.distinct().sorted()
            Triple(name, meetings.size, formatWeeks(courseWeeks))
        }.sortedByDescending { it.second }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(schedule.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    "${rows.size} 门课程 · ${schedule.meetings.size} 节课 · 共 $weeks 周\n" +
                        "${formatDate(range.start)} – ${formatDate(range.endInclusive)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { renaming = true }, shapes = ButtonDefaults.shapes()) {
                    Icon(Symbols.Edit, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                    Text("重命名", Modifier.padding(start = ButtonDefaults.IconSpacing))
                }
                OutlinedButton(
                    onClick = { removing = true },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Symbols.Delete, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                    Text("移除课表", Modifier.padding(start = ButtonDefaults.IconSpacing))
                }
            }
            for ((name, count, weekText) in rows) {
                ListItem(
                    supportingContent = { Text("$count 节 · $weekText") },
                    leadingContent = { CourseAvatar(name) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) { Text(name) }
            }
        }
    }

    if (renaming) {
        var text by rememberSaveable { mutableStateOf(schedule.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("重命名课表") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text("课表名称") })
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameSchedule(schedule.id, text.trim())
                        renaming = false
                    },
                    enabled = text.isNotBlank(),
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("取消") } },
        )
    }
    if (removing) {
        var removeCourses by rememberSaveable { mutableStateOf(true) }
        ConfirmDialog(
            title = "移除「${schedule.name}」？",
            message = "移除后，这学期的照片不再按课表自动识别。已添加到文件名中的课程不受影响。",
            confirmLabel = "移除",
            onDismiss = { removing = false },
            onConfirm = {
                viewModel.removeSchedule(schedule.id, removeCourses)
                removing = false
                onDismiss()
            },
            extra = {
                Row(
                    Modifier.fillMaxWidth().toggleable(removeCourses, role = Role.Checkbox) { removeCourses = it },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = removeCourses, onCheckedChange = null)
                    Text("同时移除还没有照片的课表课程", Modifier.padding(start = 12.dp))
                }
            },
        )
    }
}
