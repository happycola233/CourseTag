package com.happycola233.coursetag.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.happycola233.coursetag.BuildConfig
import com.happycola233.coursetag.data.ThemeMode
import com.happycola233.coursetag.ui.AppViewModel
import com.happycola233.coursetag.ui.MediaAccess
import com.happycola233.coursetag.ui.Navigator
import com.happycola233.coursetag.ui.components.GroupGap
import com.happycola233.coursetag.ui.components.GroupedItem
import com.happycola233.coursetag.ui.components.LocalSnackbarHostState
import com.happycola233.coursetag.ui.components.SectionLabel
import com.happycola233.coursetag.ui.components.canRenameWithoutConfirmation
import com.happycola233.coursetag.ui.components.openAppSettings
import com.happycola233.coursetag.ui.components.rememberMediaManagementRequest
import com.happycola233.coursetag.ui.components.pageTopBarColors
import com.happycola233.coursetag.ui.components.supportsMediaManagement
import com.happycola233.coursetag.ui.navigation.HistoryRoute
import com.happycola233.coursetag.ui.navigation.LicensesRoute
import com.happycola233.coursetag.ui.navigation.NamingFormatRoute
import com.happycola233.coursetag.ui.theme.AppSurfaces
import com.happycola233.coursetag.ui.theme.Symbols
import kotlin.math.roundToInt

private val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.System -> "跟随系统"
        ThemeMode.Light -> "浅色"
        ThemeMode.Dark -> "深色"
    }

@Composable
fun SettingsScreen(viewModel: AppViewModel, navigator: Navigator) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val access by viewModel.access.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var canSkipConfirmation by rememberSaveable { mutableStateOf(false) }
    val requestMediaManagement = rememberMediaManagementRequest(viewModel) {
        canSkipConfirmation = context.canRenameWithoutConfirmation()
    }
    var windowDialog by rememberSaveable { mutableStateOf(false) }
    var themeDialog by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val settings = data.settings

    LifecycleResumeEffect(Unit) {
        canSkipConfirmation = context.canRenameWithoutConfirmation()
        onPauseOrDispose { }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = AppSurfaces.page,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("设置") },
                scrollBehavior = scrollBehavior,
                colors = pageTopBarColors(),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(GroupGap),
        ) {
            item { SectionLabel("命名") }
            item {
                GroupedItem(
                    index = 0,
                    count = 1,
                    headline = "命名格式",
                    supporting = settings.tagFormat.compose("IMG_20260927_143021", "高等数学") + ".jpg",
                    icon = Symbols.TextFields,
                    trailing = { Icon(Symbols.ChevronRight, contentDescription = null) },
                    onClick = { navigator.open(NamingFormatRoute) },
                )
            }
            item { SectionLabel("自动识别") }
            item {
                GroupedItem(
                    index = 0,
                    count = 1,
                    headline = "课上时间范围",
                    supporting = classWindowLabel(settings.minutesBeforeClass, settings.minutesAfterClass),
                    icon = Symbols.Timer,
                    onClick = { windowDialog = true },
                )
            }
            item { SectionLabel("权限") }
            val permissionRows = if (supportsMediaManagement) 2 else 1
            item {
                GroupedItem(
                    index = 0,
                    count = permissionRows,
                    headline = "照片访问",
                    supporting = when (access) {
                        MediaAccess.Full -> "已允许访问全部照片"
                        MediaAccess.Partial -> "仅可访问部分照片，点按调整"
                        else -> "未允许，点按前往系统设置开启"
                    },
                    icon = Symbols.Image,
                    trailing = { Icon(Symbols.OpenInNew, contentDescription = null) },
                    onClick = context::openAppSettings,
                )
            }
            if (supportsMediaManagement) {
                item {
                    GroupedItem(
                        index = 1,
                        count = permissionRows,
                        headline = "免确认修改照片",
                        supporting = if (canSkipConfirmation) "已开启，重命名时不再逐次确认" else "未开启，点按完成照片权限与媒体管理授权",
                        icon = Symbols.VerifiedUser,
                        trailing = { Icon(Symbols.OpenInNew, contentDescription = null) },
                        onClick = requestMediaManagement,
                    )
                }
            }
            item { SectionLabel("记录与外观") }
            item {
                GroupedItem(
                    index = 0,
                    count = 2,
                    headline = "重命名记录",
                    supporting = if (history.isEmpty()) "批量重命名后可在这里撤销" else "最近 ${history.size} 次批量重命名",
                    icon = Symbols.History,
                    trailing = { Icon(Symbols.ChevronRight, contentDescription = null) },
                    onClick = { navigator.open(HistoryRoute) },
                )
            }
            item {
                GroupedItem(
                    index = 1,
                    count = 2,
                    headline = "深浅模式",
                    supporting = settings.themeMode.label,
                    icon = Symbols.Contrast,
                    onClick = { themeDialog = true },
                )
            }
            item { SectionLabel("关于") }
            item {
                GroupedItem(
                    index = 0,
                    count = 1,
                    headline = "开源许可",
                    supporting = "本应用使用的开源组件与图标",
                    icon = Symbols.Article,
                    trailing = { Icon(Symbols.ChevronRight, contentDescription = null) },
                    onClick = { navigator.open(LicensesRoute) },
                )
            }
            item {
                Text(
                    "课签 ${BuildConfig.VERSION_NAME}",
                    Modifier.fillMaxWidth().padding(top = 24.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    if (windowDialog) {
        ClassWindowDialog(
            before = settings.minutesBeforeClass,
            after = settings.minutesAfterClass,
            onDismiss = { windowDialog = false },
            onSave = { before, after ->
                viewModel.setClassWindow(before, after)
                windowDialog = false
            },
        )
    }
    if (themeDialog) {
        ThemeDialog(settings.themeMode, onSelect = viewModel::setThemeMode, onDismiss = { themeDialog = false })
    }
}

private fun classWindowLabel(before: Int, after: Int): String {
    val start = if (before == 0) "上课时" else "上课前 $before 分钟"
    val end = if (after == 0) "下课时" else "下课后 $after 分钟"
    return "$start 至 $end 拍摄的照片视为课上照片"
}

@Composable
private fun ClassWindowDialog(before: Int, after: Int, onDismiss: () -> Unit, onSave: (Int, Int) -> Unit) {
    // 以 5 分钟为一档：课前最多 30 分钟，课后最多 60 分钟。
    val beforeState = rememberSliderState(before.toFloat(), steps = 5, trackRange = 0f..30f)
    val afterState = rememberSliderState(after.toFloat(), steps = 11, trackRange = 0f..60f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("课上时间范围") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "课前准备和课后拍板书也算作这节课。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MinuteSlider("上课前", "不提前", beforeState)
                MinuteSlider("下课后", "不延后", afterState)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(beforeState.value.roundToInt(), afterState.value.roundToInt()) }) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun MinuteSlider(label: String, zeroLabel: String, state: SliderState) {
    val minutes = state.value.roundToInt()
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(
                if (minutes == 0) zeroLabel else "$minutes 分钟",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(state = state, onValueChange = { state.value = it })
    }
}

@Composable
private fun ThemeDialog(current: ThemeMode, onSelect: (ThemeMode) -> Unit, onDismiss: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("深浅模式") },
        text = {
            Row(
                Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                val modes = ThemeMode.entries
                modes.forEachIndexed { index, mode ->
                    ToggleButton(
                        checked = current == mode,
                        onCheckedChange = {
                            if (current != mode) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onSelect(mode)
                            }
                        },
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                    ) { Text(mode.label) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}
