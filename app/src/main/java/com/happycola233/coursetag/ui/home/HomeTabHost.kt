package com.happycola233.coursetag.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner

/** 与 BiliTools 首页一致：已打开的标签常驻组合，点击后直接切换放置，无导航转场调度。 */
@Composable
internal fun HomeTabHost(
    selectedTab: HomeTab,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (HomeTab) -> Unit,
) {
    val tabState = rememberSaveableStateHolder()
    // selectedTab 已驱动重组，访问标记不需要额外的可观察状态。
    val visitedTabs = remember { mutableSetOf<HomeTab>() }
    visitedTabs += selectedTab
    BackHandler(enabled = selectedTab != HomeTab.Photos, onBack = onBack)

    Box(modifier) {
        for (tab in HomeTab.entries) {
            if (tab in visitedTabs) key(tab) {
                val active = tab == selectedTab
                // 隐藏页暂停数据收集和返回回调，避免常驻的照片选择模式拦截当前页返回。
                val lifecycleOwner = rememberLifecycleOwner(
                    maxLifecycle = if (active) Lifecycle.State.RESUMED else Lifecycle.State.CREATED,
                )
                CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                    tabState.SaveableStateProvider(tab.name) {
                        Box(Modifier.fillMaxSize().layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height) {
                                if (active) placeable.place(0, 0)
                            }
                        }) {
                            content(tab)
                        }
                    }
                }
            }
        }
    }
}
