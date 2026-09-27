package com.happycola233.coursetag.ui.courses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.happycola233.coursetag.data.naming.FileNames

/** 支持一次添加多门课程：每行一门，也可用顿号或逗号分隔。 */
@Composable
fun AddCoursesDialog(existing: Set<String>, onDismiss: () -> Unit, onAdd: (List<String>) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val names = remember(text) {
        text.split('\n', '、', '，', ',', '；', ';')
            .map(FileNames::sanitizeCourseName)
            .filter { it.isNotEmpty() }
            .distinct()
    }
    val newNames = names.filter { it !in existing }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加课程") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    label = { Text("课程名称") },
                    placeholder = { Text("高等数学\n大学物理\n大学英语") },
                    minLines = 3,
                    maxLines = 6,
                )
                Text(
                    when {
                        names.isNotEmpty() && newNames.isEmpty() -> "这些课程已在列表中"
                        names.size != newNames.size -> "将添加 ${newNames.size} 门，已有的课程会自动跳过"
                        else -> "每行一门课程，可一次添加多门"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(newNames) }, enabled = newNames.isNotEmpty()) {
                Text(if (newNames.size > 1) "添加 ${newNames.size} 门" else "添加")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 修改课程名称；已有照片时说明会同步重命名，与已有课程同名时说明会合并。 */
@Composable
fun RenameCourseDialog(
    current: String,
    photoCount: Int,
    existing: Set<String>,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(current) }
    val name = FileNames.sanitizeCourseName(text)
    val merging = name != current && name in existing
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改课程名称") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    singleLine = true,
                    label = { Text("课程名称") },
                )
                val notes = buildList {
                    if (merging) add("将与已有的「$name」合并为同一门课程。")
                    if (photoCount > 0) add("$photoCount 张照片的文件名会同步修改，修改前可以预览。")
                    if (name != text.trim() && name.isNotEmpty()) add("文件名不支持的符号会替换为「$name」。")
                }
                if (notes.isNotEmpty()) {
                    Text(
                        notes.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onRename(name) }, enabled = name.isNotEmpty() && name != current) {
                Text(if (photoCount > 0) "预览" else "保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message)
                extra?.invoke()
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
