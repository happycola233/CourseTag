package com.happycola233.coursetag.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.happycola233.coursetag.ui.theme.AppSurfaces
import com.happycola233.coursetag.ui.theme.Symbols

/** 全局提示条，由根布局创建，各页面的 Scaffold 共用。 */
val LocalSnackbarHostState = staticCompositionLocalOf { SnackbarHostState() }

@Composable
fun pageTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = AppSurfaces.page,
    scrolledContainerColor = AppSurfaces.page,
)

@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(Symbols.ArrowBack, contentDescription = "返回") }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** Expressive 分段列表项：同组各项之间留细缝，首尾圆角更大，按下时形变。 */
@Composable
fun GroupedItem(
    index: Int,
    count: Int,
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val shapes = ListItemDefaults.segmentedShapes(index = index, count = count)
    val colors = ListItemDefaults.segmentedColors(containerColor = AppSurfaces.card)
    val leadingContent: (@Composable () -> Unit)? = leading ?: icon?.let { { Icon(it, contentDescription = null) } }
    val supportingContent: (@Composable () -> Unit)? = supporting?.let { { Text(it) } }
    val content: @Composable () -> Unit = { Text(headline, fontWeight = FontWeight.Bold) }
    if (onClick != null) {
        SegmentedListItem(
            onClick = onClick,
            shapes = shapes,
            modifier = modifier,
            enabled = enabled,
            leadingContent = leadingContent,
            trailingContent = trailing,
            supportingContent = supportingContent,
            verticalAlignment = Alignment.CenterVertically,
            colors = colors,
            content = content,
        )
    } else {
        SegmentedListItem(
            shapes = shapes,
            modifier = modifier,
            enabled = enabled,
            leadingContent = leadingContent,
            trailingContent = trailing,
            supportingContent = supportingContent,
            verticalAlignment = Alignment.CenterVertically,
            colors = colors,
            content = content,
        )
    }
}

/** 分组之间的统一间距。 */
val GroupGap: Dp = ListItemDefaults.SegmentedGap

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(88.dp).clip(MaterialShapes.Cookie9Sided.toShape())
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Text(
            title,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) Box(Modifier.padding(top = 8.dp)) { action() }
    }
}

private val avatarShapes = listOf(
    MaterialShapes.Cookie6Sided,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Sunny,
    MaterialShapes.Pentagon,
    MaterialShapes.Cookie4Sided,
    MaterialShapes.Gem,
    MaterialShapes.Arch,
    MaterialShapes.Puffy,
)

/** 课程头像：取课程名首字，按名称固定一种 Expressive 形状，便于在列表中快速区分。 */
@Composable
fun CourseAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val shape = avatarShapes[Math.floorMod(name.hashCode(), avatarShapes.size)].toShape()
    Box(
        modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).ifEmpty { "课" },
            style = if (size >= 48.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
fun PhotoImage(uri: Uri, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(12.dp)) {
    AsyncImage(
        model = uri,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

/** 照片缩略图上的课程标签。 */
@Composable
fun CourseTagPill(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.88f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text,
            modifier = Modifier.padding(PaddingValues(horizontal = 6.dp, vertical = 2.dp)),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
