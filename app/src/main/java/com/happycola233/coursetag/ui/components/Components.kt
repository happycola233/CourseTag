package com.happycola233.coursetag.ui.components

import android.net.Uri
import com.happycola233.coursetag.data.media.PhotoThumbnail
import com.happycola233.coursetag.data.media.PhotoThumbnailSize
import com.happycola233.coursetag.data.media.thumbnailCacheKey
import com.happycola233.coursetag.data.media.previewCacheKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.happycola233.coursetag.ui.theme.AppSurfaces
import com.happycola233.coursetag.ui.theme.Symbols

/** 以 LongArray 保存照片 ID 集合，配置变更与进程重建后保持选择。 */
val LongSetSaver = Saver<MutableState<Set<Long>>, LongArray>(
    save = { it.value.toLongArray() },
    restore = { mutableStateOf(it.toSet()) },
)

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

/** 课程头像：以统一圆形底色展示课程名首字；[muted] 用于尚未添加到课程列表的名称。 */
@Composable
fun CourseAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp, muted: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier.size(size).clip(CircleShape)
            .background(if (muted) colors.surfaceContainerHighest else colors.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).ifEmpty { "课" },
            style = if (size >= 48.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            color = if (muted) colors.onSurfaceVariant else colors.onSecondaryContainer,
        )
    }
}

/** 圆形色块中的图标，用于提醒卡片等需要强调状态的列表项。 */
@Composable
fun TonalIcon(icon: ImageVector, containerColor: Color, contentColor: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(40.dp).clip(CircleShape).background(containerColor), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, Modifier.size(22.dp), tint = contentColor)
    }
}

/** 列表项右侧的醒目计数，例如「3 张不符」。 */
@Composable
fun AttentionBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

@Composable
fun PhotoImage(uri: Uri, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(12.dp)) {
    AsyncImage(
        model = rememberPhotoThumbnailRequest(uri),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

/** 缩略图和预览分别缓存，避免一张大图挤占网格缓存或触发重复解码。 */
@Composable
internal fun rememberPhotoThumbnailRequest(uri: Uri): ImageRequest {
    val context = LocalContext.current
    return remember(context, uri) {
        ImageRequest.Builder(context)
            .data(if (uri.scheme == "content" && uri.authority == "media") PhotoThumbnail(uri) else uri)
            .size(PhotoThumbnailSize)
            .memoryCacheKey(thumbnailCacheKey(uri))
            .build()
    }
}

@Composable
internal fun rememberPhotoPreviewRequest(uri: Uri, size: IntSize): ImageRequest {
    val context = LocalContext.current
    return remember(context, uri, size) {
        ImageRequest.Builder(context)
            .data(uri)
            .memoryCacheKey(previewCacheKey(uri))
            .placeholderMemoryCacheKey(thumbnailCacheKey(uri))
            .crossfade(160)
            .size(size.width, size.height)
            .build()
    }
}

/** 照片缩略图上的课程标签；[warning] 用于标出与上课时间不符的课程。 */
@Composable
fun CourseTagPill(text: String, modifier: Modifier = Modifier, warning: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = (if (warning) colors.errorContainer else colors.surfaceContainerHighest).copy(alpha = 0.92f),
        contentColor = if (warning) colors.onErrorContainer else colors.onSurface,
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
