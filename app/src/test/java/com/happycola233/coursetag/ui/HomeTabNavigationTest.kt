package com.happycola233.coursetag.ui

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.runtime.entryProvider
import com.happycola233.coursetag.data.ThemeMode
import com.happycola233.coursetag.ui.home.HomeTab
import com.happycola233.coursetag.ui.home.HomeTabHost
import com.happycola233.coursetag.ui.navigation.AppNavigationDisplay
import com.happycola233.coursetag.ui.theme.CourseTagTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeTabNavigationTest {
    @get:Rule val compose = createComposeRule()

    private var selectedTab by mutableStateOf(HomeTab.Courses)
    private var detailOpen by mutableStateOf(false)
    private var consumePhotoBack by mutableStateOf(false)
    private var photoBackCount = 0
    private val tabCreations = mutableMapOf<HomeTab, Int>()
    private lateinit var dispatcher: OnBackPressedDispatcher

    @Test
    fun cancelledGesturePreservesTabAndCompletedGestureReturnsToPhotos() {
        setContent()
        compose.onNodeWithText("课程 0").performClick()
        startBackGesture()
        compose.runOnIdle {
            assertEquals(HomeTab.Courses, selectedTab)
            dispatcher.dispatchOnBackCancelled()
        }
        compose.onNodeWithText("课程 1").assertExists()
        startBackGesture()
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.onNodeWithText("照片 0").assertExists()
        compose.runOnIdle {
            assertEquals(HomeTab.Photos, selectedTab)
            // 照片首页没有待处理的页面返回，应交给系统执行返回桌面。
            assertFalse(dispatcher.hasEnabledCallbacks())
            selectedTab = HomeTab.Courses
        }
        compose.onNodeWithText("课程 1").assertExists()
    }

    @Test
    fun switchingTabsPreservesStateWithoutBuildingTabHistory() {
        setContent()
        compose.onNodeWithText("课程 0").performClick()
        compose.runOnIdle { selectedTab = HomeTab.Settings }
        compose.onNodeWithText("设置 0").performClick()
        compose.runOnIdle { selectedTab = HomeTab.Courses }
        compose.onNodeWithText("课程 1").assertExists()
        compose.runOnIdle { selectedTab = HomeTab.Settings }
        compose.onNodeWithText("设置 1").assertExists()
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.onNodeWithText("照片 0").assertExists()
        compose.runOnIdle {
            assertEquals("课程页切回来时不应重建", 1, tabCreations[HomeTab.Courses])
            assertEquals("设置页切回来时不应重建", 1, tabCreations[HomeTab.Settings])
        }
    }

    @Test
    fun hiddenTabCannotInterceptTheCurrentTabsBackEvent() {
        selectedTab = HomeTab.Photos
        setContent()
        compose.runOnIdle {
            consumePhotoBack = true
            selectedTab = HomeTab.Settings
        }
        compose.onNodeWithText("设置 0").assertIsDisplayed()
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.onNodeWithText("照片 0").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, photoBackCount)
            dispatcher.onBackPressed()
            assertEquals("回到照片页后恢复其返回回调", 1, photoBackCount)
        }
    }

    @Test
    fun returningFromDetailDoesNotPopTheUnderlyingTab() {
        setContent()
        compose.runOnIdle { detailOpen = true }
        compose.onNodeWithText("详情").assertExists()
        startBackGesture()
        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        compose.onNodeWithText("详情").assertExists()
        compose.runOnIdle { assertEquals(HomeTab.Courses, selectedTab) }
        startBackGesture()
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.onNodeWithText("课程 0").assertExists()
        compose.runOnIdle {
            assertFalse(detailOpen)
            assertEquals(HomeTab.Courses, selectedTab)
        }
    }

    @Test
    fun predictiveBackSlidesWithoutShrinkingFromLeftEdge() = verifyPredictiveSlide(BackEventCompat.EDGE_LEFT)

    @Test
    fun predictiveBackSlidesWithoutShrinkingFromRightEdge() = verifyPredictiveSlide(BackEventCompat.EDGE_RIGHT)

    @Test
    fun predictiveBackMirrorsSlideInRtl() = verifyPredictiveSlide(BackEventCompat.EDGE_RIGHT, LayoutDirection.Rtl)

    private fun verifyPredictiveSlide(edge: Int, direction: LayoutDirection = LayoutDirection.Ltr) {
        setContent(direction)
        compose.runOnIdle { detailOpen = true }
        val before = compose.onNodeWithText("详情").getUnclippedBoundsInRoot()
        startBackGesture(edge)
        val during = compose.onNodeWithText("详情").getUnclippedBoundsInRoot()
        assertEquals("返回时保留原有宽度", (before.right - before.left).value, (during.right - during.left).value, 0.5f)
        assertEquals("返回时保留原有高度", (before.bottom - before.top).value, (during.bottom - during.top).value, 0.5f)
        assertEquals("返回时不发生纵向位移", before.top.value, during.top.value, 0.5f)
        val offset = during.left.value - before.left.value
        assertTrue("当前页面应沿布局方向横向移开", if (direction == LayoutDirection.Rtl) offset < 0f else offset > 0f)
        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        val restored = compose.onNodeWithText("详情").getUnclippedBoundsInRoot()
        assertEquals(before.left.value, restored.left.value, 0.5f)
    }

    private fun setContent(direction: LayoutDirection = LayoutDirection.Ltr) {
        compose.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                CourseTagTheme(ThemeMode.Light) {
                    AppNavigationDisplay(
                        backStack = if (detailOpen) listOf("home", "detail") else listOf("home"),
                        onBack = { detailOpen = false },
                        entryProvider = entryProvider {
                            entry("home") { TestHomeContent() }
                            entry("detail") {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("详情") }
                            }
                        },
                    )
                }
            }
        }
    }

    @Composable
    private fun TestHomeContent() {
        HomeTabHost(selectedTab, onBack = { selectedTab = HomeTab.Photos }) { tab ->
            DisposableEffect(tab) {
                tabCreations[tab] = (tabCreations[tab] ?: 0) + 1
                onDispose { }
            }
            BackHandler(enabled = tab == HomeTab.Photos && consumePhotoBack) { photoBackCount++ }
            var count by rememberSaveable { mutableIntStateOf(0) }
            Column {
                Button(onClick = { count++ }) { Text("${tab.label} $count") }
            }
        }
    }

    private fun startBackGesture(edge: Int = BackEventCompat.EDGE_LEFT) {
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, edge))
        }
        compose.runOnIdle {
            dispatcher.dispatchOnBackProgressed(BackEventCompat(150f, 100f, 0.5f, edge))
        }
    }
}
