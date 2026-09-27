package com.happycola233.coursetag.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.happycola233.coursetag.ui.theme.AppSurfaces
import kotlin.math.roundToInt

/**
 * 与 BiliTools 主顶栏一致：同一个粗体标题随滚动连续上移、缩小，避免大小标题交叉淡变。
 * 标题区域展开为 96dp、折叠为 56dp；统计信息追加在标题下方，不改变标题的上边距。
 */
@Composable
fun CollapsingTopBar(
    title: String,
    state: TopAppBarState,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val typography = MaterialTheme.typography
    val collapsedTitleScale = with(LocalDensity.current) {
        typography.titleLarge.fontSize.toPx() / typography.headlineMedium.fontSize.toPx()
    }
    Layout(
        modifier = modifier
            .fillMaxWidth()
            .background(AppSurfaces.page)
            // 使用布局阶段更新的 Insets，让首帧与恢复后的状态栏留白保持一致。
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .clipToBounds(),
        content = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                style = typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                    ),
                ),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) { measurables, constraints ->
        val horizontalPadding = 16.dp.roundToPx()
        val textConstraints = Constraints(maxWidth = (constraints.maxWidth - horizontalPadding * 2).coerceAtLeast(0))
        val titlePlaceable = measurables[0].measure(textConstraints)
        val subtitlePlaceable = measurables.getOrNull(1)?.measure(textConstraints)
        val subtitleSpacing = 4.dp.roundToPx()
        val collapsedHeight = maxOf(56.dp.roundToPx(), (titlePlaceable.height * collapsedTitleScale).roundToInt())
        val expandedTitleHeight = maxOf(96.dp.roundToPx(), titlePlaceable.height + 28.dp.roundToPx())
        val expandedHeight = expandedTitleHeight + (subtitlePlaceable?.let { it.height + subtitleSpacing } ?: 0)
        // 折叠范围随副标题的实测高度变化，也适配系统字体缩放。
        state.heightOffsetLimit = (collapsedHeight - expandedHeight).toFloat()
        val height = (expandedHeight + state.heightOffset).roundToInt().coerceIn(collapsedHeight, expandedHeight)
        val transformOrigin = TransformOrigin(if (layoutDirection == LayoutDirection.Rtl) 1f else 0f, 0f)

        layout(constraints.maxWidth, height) {
            // 在布局阶段读取滚动进度，拖动时不触发整个顶栏重组。
            val fraction = state.collapsedFraction
            val deceleratedFraction = 1f - (1f - fraction) * (1f - fraction)
            val titleScale = 1f + (collapsedTitleScale - 1f) * deceleratedFraction
            val expandedTitleY = expandedTitleHeight - 28.dp.toPx() - titlePlaceable[FirstBaseline]
            val collapsedTitleY = (collapsedHeight - titlePlaceable.height * collapsedTitleScale) / 2f
            val titleY = expandedTitleY + (collapsedTitleY - expandedTitleY) * fraction
            titlePlaceable.placeRelativeWithLayer(horizontalPadding, titleY.roundToInt()) {
                this.transformOrigin = transformOrigin
                scaleX = titleScale
                scaleY = titleScale
            }
            if (fraction < 1f) {
                subtitlePlaceable?.placeRelativeWithLayer(
                    horizontalPadding,
                    (titleY + titlePlaceable.height * titleScale + subtitleSpacing).roundToInt(),
                ) {
                    alpha = 1f - fraction
                }
            }
        }
    }
}
