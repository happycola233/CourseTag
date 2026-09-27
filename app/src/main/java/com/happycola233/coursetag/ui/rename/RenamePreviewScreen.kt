package com.happycola233.coursetag.ui.rename

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.happycola233.coursetag.data.naming.PhotoNameParser
import com.happycola233.coursetag.domain.RenameChange
import com.happycola233.coursetag.domain.RenameItem
import com.happycola233.coursetag.domain.RenamePlan
import com.happycola233.coursetag.ui.AppViewModel
import com.happycola233.coursetag.ui.Navigator
import com.happycola233.coursetag.ui.components.BackButton
import com.happycola233.coursetag.ui.components.EmptyState
import com.happycola233.coursetag.ui.components.GroupGap
import com.happycola233.coursetag.ui.components.LocalSnackbarHostState
import com.happycola233.coursetag.ui.components.PhotoImage
import com.happycola233.coursetag.ui.components.SectionLabel
import com.happycola233.coursetag.ui.components.pageTopBarColors
import com.happycola233.coursetag.ui.formatCount
import com.happycola233.coursetag.ui.navigation.NamingFormatRoute
import com.happycola233.coursetag.ui.theme.AppSurfaces
import com.happycola233.coursetag.ui.theme.Symbols

@Composable
fun RenamePreviewScreen(viewModel: AppViewModel, navigator: Navigator) {
    val livePlan by viewModel.plan.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val excluded by viewModel.excluded.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    // 开始重命名后固定显示当前方案：照片改名后方案会被重新计算为「无需重命名」，不应在页面关闭时闪现。
    var frozenPlan by remember { mutableStateOf<RenamePlan?>(null) }
    LaunchedEffect(progress != null) {
        if (progress != null && frozenPlan == null) frozenPlan = livePlan
    }
    val currentPlan = frozenPlan ?: livePlan
    val parser = library?.parser
    val runnable = currentPlan?.items?.count { it.issue == null && it.photoId !in excluded } ?: 0

    Scaffold(
        containerColor = AppSurfaces.page,
        snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
        topBar = {
            TopAppBar(
                title = { Text("确认重命名") },
                navigationIcon = { BackButton(navigator::back) },
                colors = pageTopBarColors(),
            )
        },
        bottomBar = {
            if (currentPlan != null && currentPlan.items.isNotEmpty()) {
                ConfirmBar(count = runnable, enabled = runnable > 0 && progress == null, onConfirm = viewModel::confirmPlan)
            }
        },
    ) { padding ->
        when {
            currentPlan == null || parser == null -> Box(Modifier.fillMaxSize().padding(padding)) {
                LoadingIndicator(Modifier.align(Alignment.Center).size(64.dp))
            }
            currentPlan.items.isEmpty() -> Column(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    icon = Symbols.DoneAll,
                    title = "无需重命名",
                    body = if (currentPlan.missingCount > 0) "这些照片已被移动、删除或再次改名" else "所选照片的文件名已经符合要求",
                    action = { Button(onClick = navigator::back) { Text("返回") } },
                )
            }
            else -> PlanList(
                plan = currentPlan,
                parser = parser,
                excluded = excluded,
                onToggle = { id, include -> viewModel.setExcluded(id, !include) },
                onChangeFormat = { navigator.open(NamingFormatRoute) },
                contentPadding = padding,
            )
        }
    }
}

@Composable
private fun PlanList(
    plan: RenamePlan,
    parser: PhotoNameParser,
    excluded: Set<Long>,
    onToggle: (Long, Boolean) -> Unit,
    onChangeFormat: () -> Unit,
    contentPadding: PaddingValues,
) {
    val groups = remember(plan) {
        val (valid, blocked) = plan.items.partition { it.issue == null }
        valid.groupBy { it.groupTitle() }.toList() +
            if (blocked.isEmpty()) emptyList() else listOf("无法重命名" to blocked)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(GroupGap),
    ) {
        item(key = "summary") { Summary(plan, onChangeFormat, parser) }
        for ((title, items) in groups) {
            item(key = "group:$title") { SectionLabel("$title · ${items.size} 张") }
            itemsIndexed(items, key = { _, item -> item.photoId }) { index, item ->
                PlanRow(
                    item = item,
                    parser = parser,
                    included = item.issue == null && item.photoId !in excluded,
                    index = index,
                    count = items.size,
                    onToggle = { onToggle(item.photoId, it) },
                )
            }
        }
    }
}

private fun RenameItem.groupTitle(): String = when (change) {
    RenameChange.Remove -> "移除课程"
    RenameChange.Revert -> "恢复原文件名"
    else -> newCourse ?: "移除课程"
}

@Composable
private fun Summary(plan: RenamePlan, onChangeFormat: () -> Unit, parser: PhotoNameParser) {
    val blocked = plan.items.count { it.issue != null }
    val facts = buildList {
        add("${formatCount(plan.items.size - blocked)} 张照片将重命名")
        if (plan.unchangedCount > 0) add("${formatCount(plan.unchangedCount)} 张已是目标名称")
        if (plan.missingCount > 0) add("${formatCount(plan.missingCount)} 张已被移动或改名")
        if (blocked > 0) add("${formatCount(blocked)} 张无法重命名")
    }
    val example = plan.items.firstOrNull { it.newCourse != null && it.change != RenameChange.Revert }
    Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(plan.request.title, style = MaterialTheme.typography.headlineSmall)
        Text(
            facts.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (example != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    highlightedName(example.newName, parser, removed = false),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
                TextButton(onClick = onChangeFormat) { Text("更改格式") }
            }
        }
    }
}

@Composable
private fun PlanRow(
    item: RenameItem,
    parser: PhotoNameParser,
    included: Boolean,
    index: Int,
    count: Int,
    onToggle: (Boolean) -> Unit,
) {
    val oldName = item.entry.photo.name
    val strikeOld = item.change == RenameChange.Replace || item.change == RenameChange.Remove ||
        item.change == RenameChange.Reformat
    val blocked = item.issue != null
    SegmentedListItem(
        onClick = { onToggle(!included) },
        enabled = !blocked,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(containerColor = AppSurfaces.card),
        leadingContent = {
            PhotoImage(item.entry.photo.uri, Modifier.size(52.dp), RoundedCornerShape(12.dp))
        },
        trailingContent = if (blocked) null else ({ Checkbox(checked = included, onCheckedChange = null) }),
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    highlightedName(oldName, parser, removed = strikeOld),
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
                item.issue?.let {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Symbols.Error, contentDescription = null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        Text(it.message, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
    ) {
        Text(
            highlightedName(item.newName, parser, removed = false),
            style = MaterialTheme.typography.bodyLarge,
            color = if (included || blocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 突出显示文件名中的课程后缀：新名称中加粗着色，旧名称中被替换的部分加删除线。 */
@Composable
fun highlightedName(name: String, parser: PhotoNameParser, removed: Boolean): AnnotatedString {
    val primary = MaterialTheme.colorScheme.primary
    val parsed = remember(name, parser) { parser.parse(name) }
    if (parsed.course == null) return AnnotatedString(name)
    val start = parsed.stem.length
    val end = name.length - parsed.extension.length
    return buildAnnotatedString {
        append(name.substring(0, start))
        withStyle(
            if (removed) {
                SpanStyle(textDecoration = TextDecoration.LineThrough)
            } else {
                SpanStyle(color = primary, fontWeight = FontWeight.SemiBold)
            },
        ) { append(name.substring(start, end)) }
        append(name.substring(end))
    }
}

@Composable
private fun ConfirmBar(count: Int, enabled: Boolean, onConfirm: () -> Unit) {
    Surface(color = AppSurfaces.page) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onConfirm,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(if (count > 0) "重命名 ${formatCount(count)} 张照片" else "请至少保留一张照片", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "只修改文件名，照片内容与拍摄信息保持不变",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
