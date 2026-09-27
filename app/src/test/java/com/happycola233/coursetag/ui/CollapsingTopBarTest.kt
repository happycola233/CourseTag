package com.happycola233.coursetag.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.happycola233.coursetag.data.ThemeMode
import com.happycola233.coursetag.ui.components.CollapsingTopBar
import com.happycola233.coursetag.ui.theme.CourseTagTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h600dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CollapsingTopBarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightTitleKeepsStartPaddingDuringCollapse() = verifyCollapse(ThemeMode.Light)
    @Test fun darkTitleKeepsStartPaddingDuringCollapse() = verifyCollapse(ThemeMode.Dark)
    @Test fun rtlTitleKeepsStartPaddingDuringCollapse() = verifyCollapse(ThemeMode.Dark, LayoutDirection.Rtl)
    @Test fun firstFrameIncludesStatusBarInset() = verifyFirstLayout(initiallyCollapsed = false)
    @Test fun restoredCollapsedBarIncludesStatusBarInset() = verifyFirstLayout(initiallyCollapsed = true)

    private fun verifyCollapse(mode: ThemeMode, direction: LayoutDirection = LayoutDirection.Ltr) {
        lateinit var state: TopAppBarState
        var startPadding = 0f
        var collapsedHeight = 0f
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                CourseTagTheme(mode) {
                    startPadding = with(LocalDensity.current) { 16.dp.toPx() }
                    collapsedHeight = with(LocalDensity.current) { 56.dp.toPx() }
                    state = rememberTopAppBarState()
                    Box(Modifier.fillMaxWidth().testTag("bar")) {
                        CollapsingTopBar("课程", state, subtitle = "23 门课程 · 451 张照片已标记")
                    }
                }
            }
        }
        var expandedTitleHeight = 0f
        for (fraction in listOf(0f, 0.5f, 1f, 0f)) {
            compose.runOnIdle { state.heightOffset = state.heightOffsetLimit * fraction }
            val bar = compose.onNodeWithTag("bar").fetchSemanticsNode().boundsInRoot
            val title = compose.onNodeWithText("课程").fetchSemanticsNode().boundsInRoot
            val actualPadding = if (direction == LayoutDirection.Rtl) bar.right - title.right else title.left - bar.left
            assertEquals(startPadding, actualPadding, 1f)
            assertTrue("标题应始终完整位于顶栏中", title.top >= bar.top && title.bottom <= bar.bottom)
            if (fraction == 0f) expandedTitleHeight = title.height
            else assertTrue("折叠时标题应缩小", title.height < expandedTitleHeight)
            if (fraction == 1f) {
                assertEquals(collapsedHeight, bar.height, 1f)
                assertEquals(bar.center.y, title.center.y, 1f)
            }
        }
        compose.onNodeWithText("23 门课程 · 451 张照片已标记").assertExists()
    }

    private fun verifyFirstLayout(initiallyCollapsed: Boolean) {
        val heights = mutableListOf<Int>()
        val statusBarHeight = 72
        var toolbarHeight = 0
        lateinit var state: TopAppBarState
        compose.setContent {
            CourseTagTheme(ThemeMode.Light) {
                toolbarHeight = with(LocalDensity.current) { (if (initiallyCollapsed) 56.dp else 96.dp).roundToPx() }
                val collapseRange = with(LocalDensity.current) { 40.dp.toPx() }
                state = rememberTopAppBarState(
                    initialHeightOffsetLimit = -collapseRange,
                    initialHeightOffset = if (initiallyCollapsed) -collapseRange else 0f,
                )
                val view = LocalView.current
                Box(Modifier.layout { measurable, constraints ->
                    ViewCompat.dispatchApplyWindowInsets(
                        view,
                        WindowInsetsCompat.Builder()
                            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, statusBarHeight, 0, 0))
                            .setVisible(WindowInsetsCompat.Type.statusBars(), true)
                            .build(),
                    )
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }) {
                    CollapsingTopBar("设置", state, Modifier.onSizeChanged { heights += it.height })
                }
            }
        }
        compose.runOnIdle {
            assertTrue(heights.isNotEmpty())
            assertEquals(statusBarHeight + toolbarHeight, heights.first())
            assertEquals(if (initiallyCollapsed) 1f else 0f, state.collapsedFraction, 0f)
        }
    }
}
