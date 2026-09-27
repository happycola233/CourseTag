package com.happycola233.coursetag.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import com.happycola233.coursetag.ui.components.LocalSnackbarHostState
import com.happycola233.coursetag.ui.components.mediaAccess
import com.happycola233.coursetag.ui.courses.CourseDetailScreen
import com.happycola233.coursetag.ui.courses.ScheduleImportSheet
import com.happycola233.coursetag.ui.home.HomeScreen
import com.happycola233.coursetag.ui.navigation.CourseDetailRoute
import com.happycola233.coursetag.ui.navigation.HistoryRoute
import com.happycola233.coursetag.ui.navigation.HomeRoute
import com.happycola233.coursetag.ui.navigation.LicensesRoute
import com.happycola233.coursetag.ui.navigation.NamingFormatRoute
import com.happycola233.coursetag.ui.navigation.PhotoViewerRoute
import com.happycola233.coursetag.ui.navigation.RenamePreviewRoute
import com.happycola233.coursetag.ui.navigation.SmartTagRoute
import com.happycola233.coursetag.ui.photos.PhotoViewerScreen
import com.happycola233.coursetag.ui.rename.ApplyProgressDialog
import com.happycola233.coursetag.ui.rename.RenameFailuresDialog
import com.happycola233.coursetag.ui.rename.RenamePreviewScreen
import com.happycola233.coursetag.ui.settings.HistoryScreen
import com.happycola233.coursetag.ui.settings.LicensesScreen
import com.happycola233.coursetag.ui.settings.NamingFormatScreen
import com.happycola233.coursetag.ui.smart.SmartTagScreen
import com.happycola233.coursetag.ui.theme.AppSurfaces
import com.happycola233.coursetag.ui.theme.CourseTagTheme
import kotlinx.coroutines.launch

/** 页面间导航的统一入口，由根布局提供给各页面。 */
class Navigator(private val backStack: MutableList<NavKey>) {
    fun open(route: NavKey) {
        backStack += route
    }

    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    /** 关闭指定页面（不在栈顶时同样移除），用于重命名完成后收起预览页。 */
    fun close(route: NavKey) {
        val index = backStack.lastIndexOf(route)
        if (index > 0) backStack.removeAt(index)
    }

    /** 进程回收后只清理缺少执行方案的预览，保留仍可从本地数据恢复的页面。 */
    fun discardExpiredPreview(hasPreview: Boolean): Boolean =
        !hasPreview && backStack.removeAll { it == RenamePreviewRoute }
}

@Composable
fun CourseTagApp(viewModel: AppViewModel) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    CourseTagTheme(data.settings.themeMode) {
        val backStack = rememberNavBackStack(HomeRoute)
        val navigator = remember(backStack) { Navigator(backStack) }
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        // 权限和图库属于整个应用，恢复到大图、课上照片等页面时也必须初始化。
        LifecycleResumeEffect(viewModel) {
            viewModel.onAccessChecked(context.mediaAccess())
            onPauseOrDispose { }
        }
        val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            viewModel.onWriteResult(it.resultCode == Activity.RESULT_OK)
        }

        LaunchedEffect(viewModel) {
            // 返回栈可以跨进程恢复，但包含大量照片的待执行方案仅在当前进程内有效。
            if (navigator.discardExpiredPreview(viewModel.hasPreview)) {
                viewModel.message("上次的重命名预览已关闭，请重新选择照片")
            }
            viewModel.uiEvents.collect { event ->
                when (event) {
                    is UiEvent.RequestWrite ->
                        writeLauncher.launch(IntentSenderRequest.Builder(event.intentSender).build())
                    UiEvent.Applied -> navigator.close(RenamePreviewRoute)
                    // 提示条在独立协程中展示，等待用户操作时不阻塞后续事件。
                    is UiEvent.Message -> scope.launch { showMessage(snackbarHostState, event, viewModel) }
                }
            }
        }

        CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
            AppNavDisplay(backStack, navigator, viewModel)
            ApplyProgressDialog(viewModel)
            RenameFailuresDialog(viewModel)
            ScheduleImportSheet(viewModel)
        }
    }
}

private suspend fun showMessage(host: SnackbarHostState, event: UiEvent.Message, viewModel: AppViewModel) {
    host.currentSnackbarData?.dismiss()
    val result = host.showSnackbar(
        message = event.text,
        actionLabel = if (event.undoBatchId != null) "撤销" else null,
        withDismissAction = event.undoBatchId == null,
        duration = if (event.undoBatchId != null) SnackbarDuration.Long else SnackbarDuration.Short,
    )
    if (result == SnackbarResult.ActionPerformed) event.undoBatchId?.let(viewModel::undo)
}

@Composable
private fun AppNavDisplay(backStack: MutableList<NavKey>, navigator: Navigator, viewModel: AppViewModel) {
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val fastEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    Box(Modifier.fillMaxSize().background(AppSurfaces.page)) {
        NavDisplay(
            backStack = backStack,
            onBack = navigator::back,
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
            transitionSpec = {
                ContentTransform(
                    slideInHorizontally(spatial) { it / 4 } + fadeIn(effects),
                    slideOutHorizontally(spatial) { -it / 12 } + fadeOut(fastEffects),
                )
            },
            popTransitionSpec = {
                ContentTransform(
                    slideInHorizontally(spatial) { -it / 12 } + fadeIn(effects),
                    slideOutHorizontally(spatial) { it / 4 } + fadeOut(fastEffects),
                    targetContentZIndex = -1f,
                )
            },
            // 预测性返回：当前页随手势缩小并向返回方向移出，下层页面同步浮现。
            predictivePopTransitionSpec = { swipeEdge ->
                val direction = if (swipeEdge == NavigationEvent.EDGE_RIGHT) -1 else 1
                ContentTransform(
                    scaleIn(initialScale = 0.96f) + fadeIn(effects),
                    scaleOut(targetScale = 0.9f) +
                        slideOutHorizontally { direction * it / 8 } +
                        fadeOut(),
                    targetContentZIndex = -1f,
                )
            },
            entryProvider = entryProvider {
                entry<HomeRoute> { HomeScreen(viewModel, navigator) }
                entry<SmartTagRoute> { SmartTagScreen(viewModel, navigator) }
                entry<RenamePreviewRoute> { RenamePreviewScreen(viewModel, navigator) }
                entry<CourseDetailRoute> { CourseDetailScreen(it.courseName, viewModel, navigator) }
                entry<PhotoViewerRoute> { PhotoViewerScreen(it.photoId, viewModel, navigator) }
                entry<NamingFormatRoute> { NamingFormatScreen(viewModel, navigator) }
                entry<HistoryRoute> { HistoryScreen(viewModel, navigator) }
                entry<LicensesRoute> { LicensesScreen(navigator) }
            },
        )
    }
}
