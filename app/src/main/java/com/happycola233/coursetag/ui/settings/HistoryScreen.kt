package com.happycola233.coursetag.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.happycola233.coursetag.data.RenameBatch
import com.happycola233.coursetag.ui.AppViewModel
import com.happycola233.coursetag.ui.Navigator
import com.happycola233.coursetag.ui.components.BackButton
import com.happycola233.coursetag.ui.components.EmptyState
import com.happycola233.coursetag.ui.components.LocalSnackbarHostState
import com.happycola233.coursetag.ui.components.pageTopBarColors
import com.happycola233.coursetag.ui.courses.ConfirmDialog
import com.happycola233.coursetag.ui.formatDateTime
import com.happycola233.coursetag.ui.theme.AppSurfaces
import com.happycola233.coursetag.ui.theme.Symbols

@Composable
fun HistoryScreen(viewModel: AppViewModel, navigator: Navigator) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var clearing by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = AppSurfaces.page,
        snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
        topBar = {
            TopAppBar(
                title = { Text("重命名记录") },
                navigationIcon = { BackButton(navigator::back) },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { clearing = true }) { Icon(Symbols.Delete, contentDescription = "清空记录") }
                    }
                },
                colors = pageTopBarColors(),
            )
        },
    ) { padding ->
        if (history.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center) {
                EmptyState(Symbols.History, "还没有重命名记录", "批量重命名后，可以在这里查看并撤销")
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "撤销会把照片恢复为当时的文件名，之后被再次改名或移动的照片会自动跳过。",
                    Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(history, key = { it.id }) { batch ->
                BatchCard(
                    batch = batch,
                    expanded = expanded == batch.id,
                    onToggle = { expanded = if (expanded == batch.id) null else batch.id },
                    onUndo = { viewModel.undo(batch.id) },
                )
            }
        }
    }

    if (clearing) {
        ConfirmDialog(
            title = "清空重命名记录？",
            message = "清空后将无法撤销之前的重命名，照片文件名不受影响。",
            confirmLabel = "清空",
            onDismiss = { clearing = false },
            onConfirm = {
                viewModel.clearHistory()
                clearing = false
            },
        )
    }
}

@Composable
private fun BatchCard(batch: RenameBatch, expanded: Boolean, onToggle: () -> Unit, onUndo: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = AppSurfaces.card,
        modifier = Modifier.fillMaxWidth().animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle)
                    .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(batch.title, style = MaterialTheme.typography.titleMedium)
                    val status = if (batch.undone) " · 已撤销" else ""
                    Text(
                        "${batch.records.size} 张照片 · ${formatDateTime(batch.createdAt)}$status",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!batch.undone) {
                    FilledTonalButton(onClick = onUndo, shapes = ButtonDefaults.shapes()) {
                        Icon(Symbols.Undo, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                        Text("撤销", Modifier.padding(start = ButtonDefaults.IconSpacing))
                    }
                }
                Icon(
                    if (expanded) Symbols.ExpandLess else Symbols.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                    fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                    fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                Column(
                    Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    for (record in batch.records.take(MAX_VISIBLE_RECORDS)) {
                        Column {
                            Text(
                                record.from,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.MiddleEllipsis,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Symbols.ArrowForward,
                                    contentDescription = null,
                                    Modifier.size(14.dp).padding(end = 2.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(record.to, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                            }
                        }
                    }
                    if (batch.records.size > MAX_VISIBLE_RECORDS) {
                        Text(
                            "以及另外 ${batch.records.size - MAX_VISIBLE_RECORDS} 张照片",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private const val MAX_VISIBLE_RECORDS = 50
