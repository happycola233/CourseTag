package com.happycola233.coursetag.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.happycola233.coursetag.ui.photos.LocalPhotoGridReturnState
import com.happycola233.coursetag.ui.photos.PhotoGridReturnState

/** 二级页面沿用 BiliTools 设置页的横向转场和默认弹簧参数。 */
@Composable
internal fun <T : Any> AppNavigationDisplay(
    backStack: List<T>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    entryDecorators: List<NavEntryDecorator<T>> = listOf(rememberSaveableStateHolderNavEntryDecorator()),
    entryProvider: (T) -> NavEntry<T>,
) {
    val navigationDirection = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
    val photoGridReturnState = remember { PhotoGridReturnState() }
    Box(modifier) {
        CompositionLocalProvider(
            LocalPhotoGridReturnState provides photoGridReturnState,
        ) {
            NavDisplay(
                backStack = backStack,
                onBack = onBack,
                sceneStrategies = remember { listOf(PhotoSceneStrategy<T>()) },
                entryDecorators = entryDecorators,
                transitionSpec = {
                    slideInHorizontally(initialOffsetX = { it * navigationDirection }).togetherWith(
                        slideOutHorizontally(targetOffsetX = { -it * navigationDirection / 4 }) + fadeOut(),
                    )
                },
                popTransitionSpec = { slideBackTransition(navigationDirection) },
                // 手势返回沿用二级页面的返回轨迹；进度、取消和完成仍由 NavDisplay 管理。
                predictivePopTransitionSpec = { slideBackTransition(navigationDirection) },
                entryProvider = entryProvider,
            )
        }
    }
}

private fun slideBackTransition(navigationDirection: Int): ContentTransform =
    (slideInHorizontally(initialOffsetX = { -it * navigationDirection / 4 }) + fadeIn()).togetherWith(
        slideOutHorizontally(targetOffsetX = { it * navigationDirection }),
    )
