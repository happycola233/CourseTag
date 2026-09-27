package com.happycola233.coursetag.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.happycola233.coursetag.ui.AppViewModel
import com.happycola233.coursetag.ui.Navigator
import com.happycola233.coursetag.ui.components.mediaAccess
import com.happycola233.coursetag.ui.courses.CoursesScreen
import com.happycola233.coursetag.ui.photos.PhotosScreen
import com.happycola233.coursetag.ui.settings.SettingsScreen
import com.happycola233.coursetag.ui.theme.AppSurfaces
import com.happycola233.coursetag.ui.theme.Symbols

enum class HomeTab(val label: String) { Photos("照片"), Courses("课程"), Settings("设置") }

@Composable
fun HomeScreen(viewModel: AppViewModel, navigator: Navigator) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.Photos) }
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val selecting = tab == HomeTab.Photos && selection.isNotEmpty()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    // 每次回到前台都重新检查照片权限，用户可能刚在系统设置中修改过。
    LifecycleResumeEffect(Unit) {
        viewModel.onAccessChecked(context.mediaAccess())
        onPauseOrDispose { }
    }
    BackHandler(enabled = tab != HomeTab.Photos) { tab = HomeTab.Photos }

    Scaffold(
        containerColor = AppSurfaces.page,
        contentWindowInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom),
        bottomBar = {
            AnimatedVisibility(
                visible = !selecting,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                    fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                    fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                ShortNavigationBar(containerColor = AppSurfaces.page) {
                    for (item in HomeTab.entries) {
                        val selected = tab == item
                        ShortNavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    tab = item
                                }
                            },
                            icon = { Icon(item.icon(selected), contentDescription = null) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        val tabState = rememberSaveableStateHolder()
        val enter = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val exit = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        AnimatedContent(
            targetState = tab,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { (fadeIn(enter) + scaleIn(enter, initialScale = 0.96f)) togetherWith fadeOut(exit) },
            label = "home_tab",
        ) { current ->
            tabState.SaveableStateProvider(current.name) {
                Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
                    when (current) {
                        HomeTab.Photos -> PhotosScreen(viewModel, navigator)
                        HomeTab.Courses -> CoursesScreen(viewModel, navigator)
                        HomeTab.Settings -> SettingsScreen(viewModel, navigator)
                    }
                }
            }
        }
    }
}

/** 选中的标签使用实心图标。 */
@Composable
private fun HomeTab.icon(selected: Boolean): ImageVector = when (this) {
    HomeTab.Photos -> if (selected) Symbols.PhotoLibraryFilled else Symbols.PhotoLibrary
    HomeTab.Courses -> if (selected) Symbols.SchoolFilled else Symbols.School
    HomeTab.Settings -> if (selected) Symbols.SettingsFilled else Symbols.Settings
}
