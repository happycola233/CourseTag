package com.happycola233.coursetag.ui.photos

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.happycola233.coursetag.domain.Library
import com.happycola233.coursetag.domain.PhotoEntry
import com.happycola233.coursetag.domain.RenameRequest
import com.happycola233.coursetag.ui.AppViewModel
import com.happycola233.coursetag.ui.MediaAccess
import com.happycola233.coursetag.ui.Navigator
import com.happycola233.coursetag.ui.components.EmptyState
import com.happycola233.coursetag.ui.components.CollapsingTopBar
import com.happycola233.coursetag.ui.components.GroupedItem
import com.happycola233.coursetag.ui.components.LocalSnackbarHostState
import com.happycola233.coursetag.ui.components.openAppSettings
import com.happycola233.coursetag.ui.components.pageTopBarColors
import com.happycola233.coursetag.ui.components.rememberMediaPermissionRequest
import com.happycola233.coursetag.ui.components.rememberScheduleFilePicker
import com.happycola233.coursetag.ui.courses.CoursePick
import com.happycola233.coursetag.ui.courses.CoursePickerSheet
import com.happycola233.coursetag.ui.formatCount
import com.happycola233.coursetag.ui.formatDay
import com.happycola233.coursetag.ui.formatSessionDate
import com.happycola233.coursetag.ui.navigation.PhotoViewerRoute
import com.happycola233.coursetag.ui.navigation.RenamePreviewRoute
import com.happycola233.coursetag.ui.navigation.SmartTagRoute
import com.happycola233.coursetag.ui.theme.AppSurfaces
import com.happycola233.coursetag.ui.theme.Symbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class PhotoFilter(val label: String) {
    All("全部"),
    InClass("课上拍摄"),
    Untagged("未标记"),
    Tagged("已标记"),
}

@Composable
fun PhotosScreen(viewModel: AppViewModel, navigator: Navigator) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val access by viewModel.access.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(PhotoFilter.All) }
    var albumId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val selecting = selection.isNotEmpty()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val currentLibrary = library

    BackHandler(enabled = selecting) { viewModel.clearSelection() }

    val sections = remember(currentLibrary, filter, albumId) {
        currentLibrary?.let { buildSections(it, filter, albumId) }.orEmpty()
    }
    val visibleIds = remember(sections) { sections.flatMap { section -> section.entries.map { it.id } } }
    val selectedEntries = remember(selection, currentLibrary) { selection.mapNotNull { currentLibrary?.byId?.get(it) } }

    fun openPreview(request: RenameRequest) {
        viewModel.preview(request)
        navigator.open(RenamePreviewRoute)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = AppSurfaces.page,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text("已选择 ${selection.size} 张", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) { Icon(Symbols.Close, contentDescription = "取消选择") }
                    },
                    actions = {
                        val allSelected = visibleIds.isNotEmpty() && selection.containsAll(visibleIds)
                        IconButton(onClick = { viewModel.setSelection(if (allSelected) emptySet() else selection + visibleIds) }) {
                            Icon(Symbols.SelectAll, contentDescription = if (allSelected) "取消全选" else "全选")
                        }
                    },
                    colors = pageTopBarColors(),
                )
            } else {
                CollapsingTopBar(
                    title = "照片",
                    subtitle = currentLibrary?.takeIf { access != MediaAccess.Denied }?.let(::librarySummary),
                    state = scrollBehavior.state,
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            when {
                access == MediaAccess.Denied -> PermissionRequest(viewModel)
                currentLibrary == null -> LoadingIndicator(Modifier.align(Alignment.Center).size(64.dp))
                else -> PhotoGrid(
                    sections = sections,
                    selection = selection,
                    onSelectionChange = viewModel::setSelection,
                    onOpen = { entry, ids ->
                        viewModel.viewerPhotoIds = ids
                        navigator.open(PhotoViewerRoute(entry.id))
                    },
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = if (selecting) 104.dp else 16.dp,
                    ),
                    alwaysShowSectionSelect = filter == PhotoFilter.InClass,
                    header = {
                        photoHeader(
                            library = currentLibrary,
                            access = access,
                            filter = filter,
                            onFilterChange = { filter = it },
                            albumId = albumId,
                            onAlbumChange = { albumId = it },
                            showBanners = !selecting,
                            viewModel = viewModel,
                            onOpenSmartTag = {
                                viewModel.resetSmartDraft()
                                navigator.open(SmartTagRoute)
                            },
                            onConvertFormat = {
                                openPreview(
                                    RenameRequest.Assign(
                                        title = "更新命名格式",
                                        assignments = currentLibrary.entries.filter { it.usesPreviousFormat }
                                            .associate { it.id to it.course },
                                    ),
                                )
                            },
                        )
                        if (sections.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) { FilterEmptyState(filter, currentLibrary, viewModel) }
                        }
                    },
                )
            }
            AnimatedVisibility(
                visible = selecting,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec(), initialScale = 0.8f),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    scaleOut(MaterialTheme.motionScheme.fastSpatialSpec(), targetScale = 0.8f),
            ) {
                SelectionToolbar(
                    canMatchSchedule = selectedEntries.any { it.session != null },
                    canRemove = selectedEntries.any { it.course != null },
                    onTag = { pickerOpen = true },
                    onMatchSchedule = {
                        openPreview(
                            RenameRequest.Assign(
                                title = "按课表标记课程",
                                assignments = selectedEntries.mapNotNull { entry -> entry.session?.let { entry.id to it.course } }.toMap(),
                            ),
                        )
                    },
                    onRemove = {
                        openPreview(
                            RenameRequest.Assign(
                                title = "移除课程",
                                assignments = selectedEntries.filter { it.course != null }.associate { it.id to null },
                            ),
                        )
                    },
                )
            }
        }
    }

    if (pickerOpen && selectedEntries.isNotEmpty()) {
        CoursePickerSheet(
            viewModel = viewModel,
            entries = selectedEntries,
            onDismiss = { pickerOpen = false },
            onPick = { pick ->
                pickerOpen = false
                openPreview(pick.toRequest(selectedEntries))
            },
        )
    }
}

/** 选课结果转换为重命名请求；按课表匹配时只处理拍摄于上课时间的照片。 */
fun CoursePick.toRequest(entries: List<PhotoEntry>): RenameRequest.Assign = when (this) {
    is CoursePick.Course -> RenameRequest.Assign("标记为「$name」", entries.associate { it.id to name })
    CoursePick.BySchedule -> RenameRequest.Assign(
        "按课表标记课程",
        entries.mapNotNull { entry -> entry.session?.let { entry.id to it.course } }.toMap(),
    )
}

private fun librarySummary(library: Library): String {
    val total = "${formatCount(library.entries.size)} 张照片"
    val pending = library.untaggedClassPhotos.size
    return when {
        library.hasSchedule && pending > 0 -> "$total · ${formatCount(pending)} 张课上照片待标记"
        else -> "$total · ${formatCount(library.taggedCount)} 张已标记"
    }
}

private fun buildSections(library: Library, filter: PhotoFilter, albumId: Long?): List<PhotoSection> {
    val zone = ZoneId.systemDefault()
    val inAlbum = library.entries.filter { albumId == null || it.photo.albumId == albumId }
    val filtered = when (filter) {
        PhotoFilter.All -> inAlbum
        PhotoFilter.InClass -> inAlbum.filter { it.session != null }
        PhotoFilter.Untagged -> inAlbum.filter { it.course == null }
        PhotoFilter.Tagged -> inAlbum.filter { it.course != null }
    }
    if (filter == PhotoFilter.InClass) {
        return filtered.groupBy { it.session!!.key }.values.map { entries ->
            val session = entries.first().session!!
            PhotoSection(
                key = session.key,
                title = session.course,
                subtitle = listOfNotNull(formatSessionDate(session), session.location).joinToString(" · "),
                entries = entries,
            )
        }
    }
    // 照片已按拍摄时间倒序排列，相邻同一天的照片归为一组。
    val sections = mutableListOf<PhotoSection>()
    var currentDay: LocalDate? = null
    var bucket = mutableListOf<PhotoEntry>()
    fun flush() {
        val day = currentDay ?: return
        sections += PhotoSection(key = day.toString(), title = formatDay(day), entries = bucket)
    }
    for (entry in filtered) {
        val day = Instant.ofEpochMilli(entry.photo.takenAt).atZone(zone).toLocalDate()
        if (day != currentDay) {
            flush()
            currentDay = day
            bucket = mutableListOf()
        }
        bucket += entry
    }
    flush()
    return sections
}

private fun LazyGridScope.photoHeader(
    library: Library,
    access: MediaAccess,
    filter: PhotoFilter,
    onFilterChange: (PhotoFilter) -> Unit,
    albumId: Long?,
    onAlbumChange: (Long?) -> Unit,
    showBanners: Boolean,
    viewModel: AppViewModel,
    onOpenSmartTag: () -> Unit,
    onConvertFormat: () -> Unit,
) {
    item(key = "filters", span = { GridItemSpan(maxLineSpan) }, contentType = "filters") {
        FilterRow(library, filter, onFilterChange, albumId, onAlbumChange)
    }
    if (!showBanners) return
    val pending = library.untaggedClassPhotos
    if (library.hasSchedule && pending.isNotEmpty() && (filter == PhotoFilter.All || filter == PhotoFilter.InClass)) {
        item(key = "smart", span = { GridItemSpan(maxLineSpan) }, contentType = "banner") {
            val sessions = pending.mapNotNull { it.session?.key }.distinct().size
            Banner(
                icon = Symbols.WandStars,
                title = "识别到 ${formatCount(pending.size)} 张课上照片未标记",
                body = "来自 $sessions 节课，确认后一键添加课程",
                onClick = onOpenSmartTag,
            )
        }
    }
    if (library.previousFormatCount > 0 && filter == PhotoFilter.All) {
        item(key = "format", span = { GridItemSpan(maxLineSpan) }, contentType = "banner") {
            Banner(
                icon = Symbols.TextFields,
                title = "${formatCount(library.previousFormatCount)} 张照片使用之前的命名格式",
                body = "统一更新为当前格式，便于按课程搜索",
                onClick = onConvertFormat,
            )
        }
    }
    if (access == MediaAccess.Partial) {
        item(key = "partial", span = { GridItemSpan(maxLineSpan) }, contentType = "banner") {
            val request = rememberMediaPermissionRequest(viewModel)
            Banner(
                icon = Symbols.Image,
                title = "仅可访问部分照片",
                body = "点按选择更多照片，或在系统设置中允许访问全部照片",
                onClick = request,
            )
        }
    }
}

@Composable
private fun Banner(icon: ImageVector, title: String, body: String, onClick: () -> Unit) {
    GroupedItem(
        index = 0,
        count = 1,
        headline = title,
        supporting = body,
        modifier = Modifier.padding(vertical = 4.dp),
        leading = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailing = { Icon(Symbols.ChevronRight, contentDescription = null) },
        onClick = onClick,
    )
}

@Composable
private fun FilterRow(
    library: Library,
    filter: PhotoFilter,
    onFilterChange: (PhotoFilter) -> Unit,
    albumId: Long?,
    onAlbumChange: (Long?) -> Unit,
) {
    var albumMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            val album = library.albums.firstOrNull { it.id == albumId }
            FilterChip(
                selected = album != null,
                onClick = { albumMenu = true },
                label = { Text(album?.name ?: "全部相册") },
                leadingIcon = { Icon(Symbols.PhotoAlbum, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) },
                trailingIcon = { Icon(Symbols.ArrowDropDown, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) },
            )
            DropdownMenu(expanded = albumMenu, onDismissRequest = { albumMenu = false }) {
                DropdownMenuItem(
                    text = { Text("全部相册 · ${formatCount(library.entries.size)}") },
                    onClick = {
                        onAlbumChange(null)
                        albumMenu = false
                    },
                    trailingIcon = if (albumId == null) ({ Icon(Symbols.Check, contentDescription = null) }) else null,
                )
                for (item in library.albums) {
                    DropdownMenuItem(
                        text = { Text("${item.name} · ${formatCount(item.count)}") },
                        onClick = {
                            onAlbumChange(item.id)
                            albumMenu = false
                        },
                        trailingIcon = if (albumId == item.id) ({ Icon(Symbols.Check, contentDescription = null) }) else null,
                    )
                }
            }
        }
        for (option in PhotoFilter.entries) {
            val selected = option == filter
            FilterChip(
                selected = selected,
                onClick = { onFilterChange(option) },
                label = { Text(option.label) },
                leadingIcon = if (selected) {
                    { Icon(Symbols.Check, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun FilterEmptyState(filter: PhotoFilter, library: Library, viewModel: AppViewModel) {
    val pickSchedule = rememberScheduleFilePicker(viewModel)
    when {
        library.entries.isEmpty() -> EmptyState(Symbols.Image, "没有照片", "拍摄的照片会显示在这里")
        filter == PhotoFilter.InClass && !library.hasSchedule -> EmptyState(
            icon = Symbols.CalendarAddOn,
            title = "导入课程表，自动识别课上照片",
            body = "支持 WakeUp 课程表等应用导出的 .ics 文件。上课时间拍摄的照片会按课程归类，一键添加课程名称。",
            action = { Button(onClick = pickSchedule) { Text("导入课程表") } },
        )
        filter == PhotoFilter.InClass -> EmptyState(Symbols.EventBusy, "没有课上拍摄的照片", "上课时间内拍摄的照片会显示在这里")
        filter == PhotoFilter.Untagged -> EmptyState(Symbols.DoneAll, "照片都已标记课程", "新拍摄的照片会显示在这里")
        filter == PhotoFilter.Tagged -> EmptyState(Symbols.Sell, "还没有标记课程的照片", "长按照片进入多选，再为它们添加课程")
        else -> EmptyState(Symbols.ImageSearch, "这个相册中没有照片", "换一个相册看看")
    }
}

@Composable
private fun PermissionRequest(viewModel: AppViewModel) {
    val context = LocalContext.current
    val request = rememberMediaPermissionRequest(viewModel)
    var requested by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        EmptyState(
            icon = Symbols.PhotoLibrary,
            title = "允许课签访问照片",
            body = "课签需要读取相册，才能为照片添加课程名称。所有照片只在本机处理。",
            action = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(onClick = {
                        requested = true
                        request()
                    }) { Text("允许访问照片") }
                    if (requested) {
                        TextButton(onClick = context::openAppSettings) { Text("在系统设置中开启") }
                    }
                }
            },
        )
    }
}

@Composable
private fun SelectionToolbar(
    canMatchSchedule: Boolean,
    canRemove: Boolean,
    onTag: () -> Unit,
    onMatchSchedule: () -> Unit,
    onRemove: () -> Unit,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = onTag) {
                Icon(Symbols.Sell, contentDescription = "标记课程")
            }
        },
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
    ) {
        TooltipIcon("按课表匹配") { label ->
            IconButton(onClick = onMatchSchedule, enabled = canMatchSchedule) {
                Icon(Symbols.WandStars, contentDescription = label)
            }
        }
        TooltipIcon("移除课程") { label ->
            IconButton(onClick = onRemove, enabled = canRemove) {
                Icon(Symbols.LabelOff, contentDescription = label)
            }
        }
    }
}

/** 长按显示文字说明的图标按钮。 */
@Composable
fun TooltipIcon(label: String, content: @Composable (String) -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        content(label)
    }
}
