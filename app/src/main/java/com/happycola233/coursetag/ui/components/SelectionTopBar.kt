package com.happycola233.coursetag.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.font.FontWeight
import com.happycola233.coursetag.ui.theme.Symbols

/** 选择切换只交叉淡化内容，不挤动照片；后续滚动仍跟随原顶栏的实时折叠高度。 */
@Composable
internal fun SelectionTopBar(
    selecting: Boolean,
    count: Int,
    onCancel: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    normalBar: @Composable () -> Unit,
) {
    val progress by animateFloatAsState(if (selecting) 1f else 0f, tween(180), label = "selection_bar")
    Layout(content = {
        Box(Modifier.graphicsLayer { alpha = 1f - progress }
            .then(if (selecting) Modifier.clearAndSetSemantics {} else Modifier)
            .pointerInput(selecting) {
                if (selecting) awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).consume()
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }) { normalBar() }
        Box(Modifier.graphicsLayer { alpha = progress }) {
            if (selecting || progress > 0f) {
                TopAppBar(
                    title = { Text("已选择 $count 张", fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = onCancel) { Icon(Symbols.Close, "取消选择") } },
                    actions = actions,
                    modifier = Modifier.fillMaxSize(),
                    colors = pageTopBarColors(),
                )
            }
        }
    }) { measurables, constraints ->
        val normal = measurables[0].measure(constraints)
        val selection = measurables[1].measure(Constraints.fixed(normal.width, normal.height))
        layout(normal.width, normal.height) {
            normal.place(0, 0)
            selection.place(0, 0)
        }
    }
}
