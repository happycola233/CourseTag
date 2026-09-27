package com.happycola233.coursetag.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.happycola233.coursetag.data.naming.TagFormat
import com.happycola233.coursetag.data.naming.TagFormatPreset
import com.happycola233.coursetag.domain.RenameRequest
import com.happycola233.coursetag.ui.AppViewModel
import com.happycola233.coursetag.ui.Navigator
import com.happycola233.coursetag.ui.components.BackButton
import com.happycola233.coursetag.ui.components.GroupGap
import com.happycola233.coursetag.ui.components.GroupedItem
import com.happycola233.coursetag.ui.components.LocalSnackbarHostState
import com.happycola233.coursetag.ui.components.SectionLabel
import com.happycola233.coursetag.ui.components.pageTopBarColors
import com.happycola233.coursetag.ui.formatCount
import com.happycola233.coursetag.ui.navigation.RenamePreviewRoute
import com.happycola233.coursetag.ui.theme.AppSurfaces

private const val SampleStem = "IMG_20260927_143021"
private const val SampleCourse = "高等数学"

@Composable
fun NamingFormatScreen(viewModel: AppViewModel, navigator: Navigator) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val format = data.settings.tagFormat
    val preset = TagFormatPreset.of(format)
    var customMode by rememberSaveable { mutableStateOf(preset == null) }
    var opening by rememberSaveable { mutableStateOf(if (preset == null) format.opening else "《") }
    var closing by rememberSaveable { mutableStateOf(if (preset == null) format.closing else "》") }
    val previousCount = library?.previousFormatCount ?: 0
    val presets = TagFormatPreset.entries

    Scaffold(
        containerColor = AppSurfaces.page,
        snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
        topBar = {
            TopAppBar(
                title = { Text("命名格式") },
                navigationIcon = { BackButton(navigator::back) },
                colors = pageTopBarColors(),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(GroupGap),
        ) {
            item(key = "preview") { FormatPreview(format) }
            if (previousCount > 0) {
                item(key = "convert") {
                    ConvertCard(previousCount) {
                        val entries = library?.entries.orEmpty().filter { it.usesPreviousFormat }
                        viewModel.preview(RenameRequest.Assign("更新命名格式", entries.associate { it.id to it.course }))
                        navigator.open(RenamePreviewRoute)
                    }
                }
            }
            item(key = "label") { SectionLabel("常用格式") }
            itemsIndexed(presets, key = { _, it -> it.name }) { index, item ->
                val selected = !customMode && item == preset
                GroupedItem(
                    index = index,
                    count = presets.size + 1,
                    headline = item.label,
                    supporting = item.format.compose(SampleStem, SampleCourse),
                    leading = { RadioButton(selected = selected, onClick = null) },
                    onClick = {
                        customMode = false
                        viewModel.setTagFormat(item.format)
                    },
                )
            }
            item(key = "custom") {
                GroupedItem(
                    index = presets.size,
                    count = presets.size + 1,
                    headline = "自定义",
                    supporting = if (preset == null) format.compose(SampleStem, SampleCourse) else "自行设定课程名前后的符号",
                    leading = { RadioButton(selected = customMode, onClick = null) },
                    onClick = { customMode = true },
                )
            }
            item(key = "editor") {
                AnimatedVisibility(
                    visible = customMode,
                    enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                        fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                        fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                ) {
                    CustomEditor(
                        opening = opening,
                        closing = closing,
                        onOpeningChange = { opening = it },
                        onClosingChange = { closing = it },
                        current = format,
                        onApply = { viewModel.setTagFormat(it) },
                    )
                }
            }
            item(key = "note") {
                Text(
                    "更换格式后，之前标记的照片仍会被识别，并可一键更新为新格式。",
                    Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FormatPreview(format: TagFormat) {
    Surface(shape = RoundedCornerShape(28.dp), color = AppSurfaces.card, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("预览", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(sampleName(format), style = MaterialTheme.typography.titleLarge)
            Text(
                "原文件名保持不变，课程名称添加在末尾",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun sampleName(format: TagFormat): AnnotatedString {
    val primary = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        append(SampleStem)
        withStyle(SpanStyle(color = primary, fontWeight = FontWeight.SemiBold)) {
            append(format.opening + SampleCourse + format.closing)
        }
        append(".jpg")
    }
}

@Composable
private fun ConvertCard(count: Int, onConvert: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = AppSurfaces.card,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                Text("${formatCount(count)} 张照片使用之前的格式", style = MaterialTheme.typography.titleSmall)
                Text(
                    "统一更新后，按课程搜索更准确",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onConvert, shapes = ButtonDefaults.shapes()) { Text("更新") }
        }
    }
}

@Composable
private fun CustomEditor(
    opening: String,
    closing: String,
    onOpeningChange: (String) -> Unit,
    onClosingChange: (String) -> Unit,
    current: TagFormat,
    onApply: (TagFormat) -> Unit,
) {
    val candidate = TagFormat(opening, closing)
    val error = when {
        opening.isEmpty() -> "课程名前的符号不能为空"
        !candidate.isValid -> "不能包含 \\ / : * ? \" < > | 等符号"
        else -> null
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = AppSurfaces.card,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = opening,
                    onValueChange = { if (it.length <= 8) onOpeningChange(it) },
                    modifier = Modifier.weight(1f),
                    label = { Text("课程名前") },
                    singleLine = true,
                    isError = error != null && opening.isEmpty(),
                )
                OutlinedTextField(
                    value = closing,
                    onValueChange = { if (it.length <= 8) onClosingChange(it) },
                    modifier = Modifier.weight(1f),
                    label = { Text("课程名后") },
                    singleLine = true,
                )
            }
            Text(
                error ?: (candidate.compose(SampleStem, SampleCourse) + ".jpg"),
                style = MaterialTheme.typography.bodyMedium,
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { onApply(candidate) },
                enabled = error == null && candidate != current,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (candidate == current) "正在使用" else "使用此格式") }
        }
    }
}
