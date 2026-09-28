package com.happycola233.coursetag.ui.photos

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.happycola233.coursetag.domain.PhotoEntry
import com.happycola233.coursetag.ui.components.PhotoImage

/** 卡片内横向排列的可选照片：点按切换是否选中，长按查看大图；默认全部选中，[excluded] 为取消选择的照片。 */
@Composable
fun SelectablePhotoRow(
    entries: List<PhotoEntry>,
    excluded: Set<Long>,
    onToggle: (id: Long, include: Boolean) -> Unit,
    onOpen: (PhotoEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(entries, key = { it.id }) { entry ->
            val selected = entry.id !in excluded
            val alpha by animateFloatAsState(
                if (selected) 1f else 0.45f,
                MaterialTheme.motionScheme.fastEffectsSpec(),
                label = "selectable_photo_alpha",
            )
            Box(
                Modifier.size(76.dp)
                    .combinedClickable(
                        onClick = { onToggle(entry.id, !selected) },
                        onLongClick = { onOpen(entry) },
                        onLongClickLabel = "查看大图",
                    )
                    .semantics {
                        contentDescription = entry.photo.name
                        role = Role.Checkbox
                        this.selected = selected
                    },
            ) {
                PhotoImage(entry.photo.uri, Modifier.size(76.dp).alpha(alpha), RoundedCornerShape(14.dp))
                SelectionMark(selected, Modifier.align(Alignment.TopEnd).padding(4.dp))
            }
        }
    }
}
