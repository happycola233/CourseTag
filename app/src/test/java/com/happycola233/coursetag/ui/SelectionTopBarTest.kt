package com.happycola233.coursetag.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.happycola233.coursetag.data.ThemeMode
import com.happycola233.coursetag.ui.components.CollapsingTopBar
import com.happycola233.coursetag.ui.components.SelectionTopBar
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
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SelectionTopBarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun expandedPhotoBarKeepsContentStationary() = verifyHeight(ThemeMode.Light, collapsed = false)
    @Test fun collapsedPhotoBarKeepsContentStationary() = verifyHeight(ThemeMode.Dark, collapsed = true)
    @Test fun courseBarKeepsContentStationary() = verifyHeight(ThemeMode.Dark, collapsed = false, course = true)

    @Test fun selectedPhotoBarCollapsesWhenScrollingInLightMode() = verifySelectedScrolling(ThemeMode.Light)
    @Test fun selectedPhotoBarCollapsesWhenScrollingInDarkMode() = verifySelectedScrolling(ThemeMode.Dark)
    @Test fun selectedCourseBarCollapsesWhenScrolling() = verifySelectedScrolling(ThemeMode.Dark, course = true)

    private fun verifySelectedScrolling(theme: ThemeMode, course: Boolean = false) {
        var selecting by mutableStateOf(false)
        lateinit var state: TopAppBarState
        compose.setContent {
            CourseTagTheme(theme) {
                val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
                state = behavior.state
                Scaffold(modifier = Modifier.nestedScroll(behavior.nestedScrollConnection), topBar = {
                    SelectionTopBar(selecting, 1, onCancel = { selecting = false }) {
                        if (course) MediumFlexibleTopAppBar(title = { Text("高等数学") }, subtitle = { Text("132 张照片") },
                            scrollBehavior = behavior)
                        else CollapsingTopBar("课签", state, subtitle = "132 张照片")
                    }
                }) { padding ->
                    LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("grid")) {
                        items((1..100).toList()) { Text("照片 $it", Modifier.fillMaxWidth().height(95.dp)) }
                    }
                }
            }
        }
        val original = compose.onNodeWithTag("grid").fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { selecting = true }
        assertEquals(original, compose.onNodeWithTag("grid").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithTag("grid").performTouchInput { swipeUp() }
        compose.runOnIdle { assertEquals(1f, state.collapsedFraction, 0.01f) }
        val top = compose.onNodeWithTag("grid").fetchSemanticsNode().boundsInRoot.top
        assertTrue("选中后滚动应消除展开顶栏留下的空白", original - top > 30f)
        val cancel = compose.onNodeWithContentDescription("取消选择").fetchSemanticsNode().boundsInRoot
        assertTrue("照片应紧接折叠后的工具栏", top - cancel.bottom in 0f..24f)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("已选择 1 张").fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        assertEquals(FontWeight.Bold, layouts.single().layoutInput.style.fontWeight)
    }

    private fun verifyHeight(theme: ThemeMode, collapsed: Boolean, course: Boolean = false) {
        var selecting by mutableStateOf(false)
        compose.setContent {
            CourseTagTheme(theme) {
                val state = rememberTopAppBarState(initialHeightOffset = if (collapsed) -200f else 0f)
                Column(Modifier.fillMaxSize()) {
                    SelectionTopBar(selecting, 3, onCancel = { selecting = false }) {
                        if (course) MediumFlexibleTopAppBar(title = { Text("高等数学") }, subtitle = { Text("132 张照片") })
                        else CollapsingTopBar("课签", state, subtitle = "132 张照片")
                    }
                    Box(Modifier.fillMaxWidth().height(95.dp).testTag("photo"))
                }
            }
        }
        val before = compose.onNodeWithTag("photo").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        for (selected in listOf(true, false, true)) {
            compose.runOnIdle { selecting = selected }
            repeat(16) {
                compose.mainClock.advanceTimeByFrame()
                assertEquals("过渡的每一帧都应保持照片位置", before,
                    compose.onNodeWithTag("photo").fetchSemanticsNode().boundsInRoot)
            }
        }
        val cancel = compose.onNodeWithContentDescription("取消选择").fetchSemanticsNode().boundsInRoot
        assertTrue("取消按钮必须完整位于顶栏中", cancel.top >= 0f && cancel.bottom <= before.top)
        compose.mainClock.autoAdvance = true
    }
}
