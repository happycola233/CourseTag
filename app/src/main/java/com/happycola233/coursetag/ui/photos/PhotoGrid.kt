package com.happycola233.coursetag.ui.photos

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toIntRect
import com.happycola233.coursetag.domain.PhotoEntry
import com.happycola233.coursetag.ui.components.CourseTagPill
import com.happycola233.coursetag.ui.components.PhotoImage
import com.happycola233.coursetag.ui.theme.Symbols
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

data class PhotoSection(
    val key: String,
    val title: String,
    val subtitle: String? = null,
    val entries: List<PhotoEntry>,
)

private val GridSpacing = 3.dp

/**
 * 分组照片网格。点按打开大图、多选时点按切换；长按后拖动可连续选择一段照片，
 * 拖到上下边缘时自动滚动。
 *
 * 点按与长按在同一个手势处理器中识别：长按后的移动与抬起全部被消费，
 * 既能让网格停止滚动，也避免同一次按压再被当作点按而取消刚选中的照片。
 */
@Composable
fun PhotoGrid(
    sections: List<PhotoSection>,
    selection: Set<Long>,
    onSelectionChange: (Set<Long>) -> Unit,
    onOpen: (PhotoEntry, List<Long>) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(),
    showCourseTags: Boolean = true,
    /** 为 true 时分组标题始终提供整组选择，否则只在多选状态下出现。 */
    alwaysShowSectionSelect: Boolean = false,
    header: LazyGridScope.() -> Unit = {},
) {
    val orderedIds = remember(sections) { sections.flatMap { section -> section.entries.map { it.id } } }
    val indexById = remember(orderedIds) { orderedIds.withIndex().associate { it.value to it.index } }
    val entryById = remember(sections) { sections.flatMap { it.entries }.associateBy { it.id } }
    val haptics = LocalHapticFeedback.current
    val selecting = selection.isNotEmpty()

    fun toggle(id: Long) {
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        onSelectionChange(if (id in selection) selection - id else selection + id)
    }

    fun activate(id: Long) {
        if (selecting) toggle(id) else entryById[id]?.let { onOpen(it, orderedIds) }
    }

    val latestSelection by rememberUpdatedState(selection)
    val latestIds by rememberUpdatedState(orderedIds)
    val latestIndex by rememberUpdatedState(indexById)
    val latestOnSelectionChange by rememberUpdatedState(onSelectionChange)
    val latestActivate by rememberUpdatedState(::activate)
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    val dragSelection = remember(state, haptics) {
        DragSelection(
            state = state,
            haptics = haptics,
            indexOf = { latestIndex[it] },
            idsBetween = { from, to -> latestIds.subList(from, to + 1) },
            selection = { latestSelection },
            onSelectionChange = { latestOnSelectionChange(it) },
        )
    }

    // 手指停在边缘时持续滚动，并随滚动继续扩展选择范围。
    LaunchedEffect(dragSelection.autoScrollSpeed) {
        val speed = dragSelection.autoScrollSpeed
        if (speed != 0f) {
            while (isActive) {
                state.scrollBy(speed)
                dragSelection.refresh()
                delay(10)
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(88.dp),
        state = state,
        modifier = modifier.photoGestures(
            state = state,
            dragSelection = dragSelection,
            autoScrollThreshold = threshold,
            onTap = { latestActivate(it) },
        ),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(GridSpacing),
        verticalArrangement = Arrangement.spacedBy(GridSpacing),
    ) {
        header()
        for (section in sections) {
            item(key = "section:${section.key}", span = { GridItemSpan(maxLineSpan) }, contentType = "section") {
                val ids = section.entries.map { it.id }
                SectionHeader(
                    section = section,
                    showSelect = selecting || alwaysShowSectionSelect,
                    allSelected = ids.all { it in selection },
                    onToggle = { selectAll ->
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelectionChange(if (selectAll) selection + ids else selection - ids.toSet())
                    },
                )
            }
            items(section.entries, key = { it.id }, contentType = { "photo" }) { entry ->
                PhotoGridItem(
                    entry = entry,
                    selected = entry.id in selection,
                    selecting = selecting,
                    showCourseTag = showCourseTags,
                    onActivate = { activate(entry.id) },
                    onSelect = { toggle(entry.id) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(section: PhotoSection, showSelect: Boolean, allSelected: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(section.title, style = MaterialTheme.typography.titleSmall)
            section.subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (showSelect) {
            IconButton(onClick = { onToggle(!allSelected) }) {
                Icon(
                    if (allSelected) Symbols.CheckCircleFilled else Symbols.Circle,
                    contentDescription = if (allSelected) "取消选择这一组" else "选择这一组",
                    tint = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 触摸由网格统一处理，这里只为无障碍服务提供点按与长按操作。 */
@Composable
private fun PhotoGridItem(
    entry: PhotoEntry,
    selected: Boolean,
    selecting: Boolean,
    showCourseTag: Boolean,
    onActivate: () -> Unit,
    onSelect: () -> Unit,
) {
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Dp>()
    val inset by animateDpAsState(if (selected) 10.dp else 0.dp, spatial, label = "photo_inset")
    val corner by animateDpAsState(if (selected) 16.dp else 6.dp, spatial, label = "photo_corner")
    val course = entry.course
    Box(
        Modifier.aspectRatio(1f)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                RoundedCornerShape(6.dp),
            )
            .semantics {
                contentDescription = listOfNotNull(entry.photo.name, course?.let { "课程：$it" }).joinToString("，")
                onClick(label = if (selecting) null else "查看大图") {
                    onActivate()
                    true
                }
                if (selecting) {
                    role = Role.Checkbox
                    this.selected = selected
                } else {
                    onLongClick(label = "选择") {
                        onSelect()
                        true
                    }
                }
            },
    ) {
        PhotoImage(entry.photo.uri, Modifier.fillMaxSize().padding(inset), RoundedCornerShape(corner))
        if (selecting) SelectionMark(selected, Modifier.align(Alignment.TopStart).padding(6.dp))
        if (showCourseTag && course != null) {
            CourseTagPill(
                course,
                Modifier.align(Alignment.BottomStart).padding(start = inset + 4.dp, end = inset + 4.dp, bottom = inset + 4.dp),
            )
        }
    }
}

/** 多选标记：选中为主题色实心勾，未选中为照片上清晰可辨的空心圆。 */
@Composable
fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.size(24.dp), contentAlignment = Alignment.Center) {
        if (selected) {
            Box(Modifier.size(18.dp).background(MaterialTheme.colorScheme.surface, CircleShape))
            Icon(Symbols.CheckCircleFilled, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        } else {
            Box(Modifier.size(20.dp).background(Color.Black.copy(alpha = 0.18f), CircleShape))
            Icon(Symbols.Circle, contentDescription = null, tint = Color.White)
        }
    }
}

private fun LazyGridState.photoIdAt(position: Offset): Long? =
    layoutInfo.visibleItemsInfo.firstOrNull { item ->
        item.size.toIntRect().contains(position.round() - item.offset)
    }?.key as? Long

/** 长按选中起点后拖动，按网格顺序选中起点到当前位置之间的照片；往回拖动会恢复原有选择。 */
private class DragSelection(
    private val state: LazyGridState,
    private val haptics: HapticFeedback,
    private val indexOf: (Long) -> Int?,
    private val idsBetween: (Int, Int) -> List<Long>,
    private val selection: () -> Set<Long>,
    private val onSelectionChange: (Set<Long>) -> Unit,
) {
    var autoScrollSpeed by mutableFloatStateOf(0f)
        private set

    private var anchorIndex: Int? = null
    private var currentIndex: Int? = null
    private var baseSelection: Set<Long> = emptySet()
    private var lastPosition = Offset.Zero

    /** 返回 false 表示长按位置不是照片，不进入拖动选择。 */
    fun start(position: Offset): Boolean {
        val id = state.photoIdAt(position) ?: return false
        val index = indexOf(id) ?: return false
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        anchorIndex = index
        currentIndex = index
        lastPosition = position
        baseSelection = selection()
        onSelectionChange(baseSelection + id)
        return true
    }

    fun move(position: Offset, threshold: Float) {
        lastPosition = position
        val fromBottom = state.layoutInfo.viewportSize.height - position.y
        autoScrollSpeed = when {
            fromBottom < threshold -> (threshold - fromBottom) / 4
            position.y < threshold -> -(threshold - position.y) / 4
            else -> 0f
        }
        refresh()
    }

    /** 按手指当前位置下的照片更新选择范围；自动滚动时手指不动也需要调用。 */
    fun refresh() {
        val anchor = anchorIndex ?: return
        val index = state.photoIdAt(lastPosition)?.let(indexOf) ?: return
        if (index == currentIndex) return
        currentIndex = index
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        onSelectionChange(baseSelection + idsBetween(minOf(anchor, index), maxOf(anchor, index)))
    }

    fun stop() {
        anchorIndex = null
        currentIndex = null
        autoScrollSpeed = 0f
    }
}

/**
 * 点按直接交给 [onTap]；长按后进入拖动选择，期间消费所有移动事件，
 * 网格的滚动识别在最终阶段看到事件已被消费后会放弃本次滚动。
 */
private fun Modifier.photoGestures(
    state: LazyGridState,
    dragSelection: DragSelection,
    autoScrollThreshold: Float,
    onTap: (Long) -> Unit,
): Modifier = pointerInput(state, dragSelection) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val longPress = awaitLongPressOrCancellation(down.id)
        if (longPress == null) {
            val up = currentEvent.changes.firstOrNull { it.id == down.id }
            if (up != null && up.changedToUp() && !up.isConsumed) state.photoIdAt(up.position)?.let(onTap)
            return@awaitEachGesture
        }
        if (!dragSelection.start(longPress.position)) return@awaitEachGesture
        longPress.consume()
        try {
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == longPress.id } ?: break
                change.consume()
                if (!change.pressed) break
                dragSelection.move(change.position, autoScrollThreshold)
            }
        } finally {
            dragSelection.stop()
        }
    }
}
