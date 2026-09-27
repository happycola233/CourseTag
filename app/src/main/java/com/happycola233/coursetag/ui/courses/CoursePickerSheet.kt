package com.happycola233.coursetag.ui.courses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.happycola233.coursetag.data.naming.FileNames
import com.happycola233.coursetag.domain.CourseSummary
import com.happycola233.coursetag.domain.PhotoEntry
import com.happycola233.coursetag.ui.AppViewModel
import com.happycola233.coursetag.ui.components.CourseAvatar
import com.happycola233.coursetag.ui.components.SectionLabel
import com.happycola233.coursetag.ui.theme.Symbols

sealed interface CoursePick {
    data class Course(val name: String) : CoursePick

    /** 每张照片使用各自拍摄时段在课表中的课程。 */
    data object BySchedule : CoursePick
}

/**
 * 为照片选择课程：顶部可搜索或直接输入新课程；照片拍摄于上课时间时优先推荐课表中的课程。
 *
 * @param entries 待标记的照片，用于计算课表推荐；为空时不显示推荐。
 * @param allowBySchedule 是否提供「按课表自动匹配」。
 */
@Composable
fun CoursePickerSheet(
    viewModel: AppViewModel,
    entries: List<PhotoEntry>,
    onDismiss: () -> Unit,
    onPick: (CoursePick) -> Unit,
    title: String = "为 ${entries.size} 张照片选择课程",
    currentCourse: String? = null,
    allowBySchedule: Boolean = true,
) {
    val courses by viewModel.courses.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val sheetState = rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val recommendation = remember(entries) { recommend(entries) }
    val newName = FileNames.sanitizeCourseName(query)
    val filtered = remember(courses, query) {
        courses.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    }
    val canCreate = newName.isNotEmpty() && courses.none { it.name == newName }
    val itemColors = ListItemDefaults.colors(containerColor = Color.Transparent)
    val itemModifier = Modifier.padding(horizontal = 8.dp)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索或输入新课程") },
                leadingIcon = { Icon(Symbols.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    { IconButton(onClick = { query = "" }) { Icon(Symbols.Close, contentDescription = "清除") } }
                } else {
                    null
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    when {
                        canCreate -> onPick(CoursePick.Course(newName))
                        filtered.size == 1 -> onPick(CoursePick.Course(filtered.first().name))
                    }
                }),
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(
                start = 8.dp,
                end = 8.dp,
                top = 8.dp,
                bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
        ) {
            if (canCreate) {
                item(key = "create") {
                    ListItem(
                        onClick = { onPick(CoursePick.Course(newName)) },
                        supportingContent = { Text("同时保存到课程列表") },
                        leadingContent = { Icon(Symbols.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        colors = itemColors,
                        modifier = itemModifier,
                    ) { Text("新建课程「$newName」", fontWeight = FontWeight.Bold) }
                }
            }
            if (query.isBlank() && recommendation != null) {
                item(key = "recommend_label") { SectionLabel("课表推荐") }
                recommendation.single?.let { course ->
                    item(key = "recommend_single") {
                        ListItem(
                            onClick = { onPick(CoursePick.Course(course)) },
                            supportingContent = { Text(recommendation.singleDescription(entries.size)) },
                            leadingContent = { CourseAvatar(course) },
                            trailingContent = { Icon(Symbols.WandStars, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            colors = itemColors,
                            modifier = itemModifier,
                        ) { Text(course, fontWeight = FontWeight.Bold) }
                    }
                }
                if (allowBySchedule && recommendation.courses.size > 1) {
                    item(key = "recommend_schedule") {
                        ListItem(
                            onClick = { onPick(CoursePick.BySchedule) },
                            supportingContent = { Text(recommendation.scheduleDescription(entries.size)) },
                            leadingContent = { Icon(Symbols.WandStars, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            colors = itemColors,
                            modifier = itemModifier,
                        ) { Text("按课表自动匹配", fontWeight = FontWeight.Bold) }
                    }
                }
            }
            if (filtered.isNotEmpty()) {
                item(key = "all_label") { SectionLabel(if (query.isBlank()) "全部课程" else "搜索结果") }
            }
            items(filtered, key = { "course:${it.name}" }) { summary ->
                ListItem(
                    onClick = { onPick(CoursePick.Course(summary.name)) },
                    supportingContent = { Text(summary.pickerDescription()) },
                    leadingContent = { CourseAvatar(summary.name) },
                    trailingContent = if (summary.name == currentCourse) {
                        { Icon(Symbols.Check, contentDescription = "当前课程", tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                    colors = itemColors,
                    modifier = itemModifier,
                ) { Text(summary.name, fontWeight = FontWeight.Bold) }
            }
            if (filtered.isEmpty() && !canCreate) {
                item(key = "empty") {
                    Text(
                        "还没有课程，输入课程名称即可新建",
                        Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private data class Recommendation(
    /** 拍摄于上课时间的照片所属课程及张数，按张数从多到少排列。 */
    val courses: List<Pair<String, Int>>,
    val matchedCount: Int,
) {
    /** 只有一门课程时直接推荐该课程。 */
    val single: String? get() = courses.singleOrNull()?.first

    fun singleDescription(total: Int): String =
        if (matchedCount == total) "这些照片都拍摄于该课上课时间" else "$total 张中有 $matchedCount 张拍摄于该课上课时间"

    fun scheduleDescription(total: Int): String {
        val base = "$matchedCount 张照片分别归入 ${courses.size} 门课程"
        return if (matchedCount < total) "$base，其余 ${total - matchedCount} 张不变" else base
    }
}

private fun recommend(entries: List<PhotoEntry>): Recommendation? {
    val matched = entries.mapNotNull { it.session?.course }
    if (matched.isEmpty()) return null
    val courses = matched.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }
    return Recommendation(courses, matched.size)
}

private fun CourseSummary.pickerDescription(): String = buildList {
    if (linkedToSchedule) add("课表课程")
    add(if (photoCount > 0) "$photoCount 张照片" else "暂无照片")
}.joinToString(" · ")
