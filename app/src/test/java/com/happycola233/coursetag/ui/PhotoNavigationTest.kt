package com.happycola233.coursetag.ui

import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.happycola233.coursetag.data.ThemeMode
import com.happycola233.coursetag.data.media.Photo
import com.happycola233.coursetag.data.naming.ParsedName
import com.happycola233.coursetag.domain.PhotoEntry
import com.happycola233.coursetag.ui.navigation.AppNavigationDisplay
import com.happycola233.coursetag.ui.navigation.HomeRoute
import com.happycola233.coursetag.ui.navigation.PhotoViewerSceneMetadata
import com.happycola233.coursetag.ui.navigation.PhotoViewerRoute
import com.happycola233.coursetag.ui.photos.PhotoGrid
import com.happycola233.coursetag.ui.photos.PhotoViewerContent
import com.happycola233.coursetag.ui.photos.PhotoSection
import com.happycola233.coursetag.ui.photos.PhotosGridKey
import com.happycola233.coursetag.ui.theme.CourseTagTheme
import coil3.asImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.memory.MemoryCache
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentLinkedQueue
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.request.transitionFactory
import coil3.request.SuccessResult
import coil3.transition.CrossfadeTransition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoNavigationTest {
    @get:Rule val compose = createComposeRule()

    private val backStack = mutableStateListOf<NavKey>(HomeRoute)
    private var selection by mutableStateOf(emptySet<Long>())
    private lateinit var dispatcher: OnBackPressedDispatcher
    private lateinit var pager: PagerState
    private lateinit var scope: CoroutineScope
    private val imageErrors = mutableListOf<Throwable>()
    private val pendingDecodes = ConcurrentLinkedQueue<String>()
    private val decodedPhotos = ConcurrentLinkedQueue<String>()
    private val previewResults = ConcurrentLinkedQueue<SuccessResult>()
    private val decodeGate = CompletableDeferred<Unit>()
    private lateinit var imageLoader: ImageLoader
    private val photoDirectory = File(System.getProperty("user.dir")!!).let { workingDirectory ->
        File(if (workingDirectory.name == "app") workingDirectory.parentFile else workingDirectory, ".tmp/photo-test-images")
    }
    private val entries = (1L..100L).map { id ->
        PhotoEntry(
            photo = Photo(id, Uri.fromFile(File(photoDirectory, "${id % 2}.png")), "照片 $id", "", "external", 1, "相机", 0,
                width = if (id % 2L == 0L) 900 else 1600,
                height = if (id % 2L == 0L) 1600 else 900),
            parsed = ParsedName("照片 $id", ".jpg", null, null),
            session = null,
            usesPreviousFormat = false,
        )
    }

    @Before
    @OptIn(DelicateCoilApi::class)
    fun cachePhotos() {
        imageLoader = ImageLoader.Builder(RuntimeEnvironment.getApplication()).eventListener(object : coil3.EventListener() {
            override fun onSuccess(request: coil3.request.ImageRequest, result: SuccessResult) {
                if (request.memoryCacheKey?.startsWith("preview:") == true) previewResults += result
            }
            override fun onError(request: coil3.request.ImageRequest, result: coil3.request.ErrorResult) {
                imageErrors += result.throwable
            }
        }).build()
        SingletonImageLoader.setUnsafe(imageLoader)
        val cache = imageLoader.memoryCache!!
        photoDirectory.mkdirs()
        for (id in 0..1) {
            val width = if (id == 0) 180 else 320
            val height = if (id == 0) 320 else 180
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint().apply { color = Color.rgb(36, 143, 157) }
            canvas.drawColor(Color.rgb(241, 180, 73))
            canvas.drawRect(0f, 0f, width / 2f, height.toFloat(), paint)
            paint.color = Color.WHITE
            canvas.drawCircle(width / 2f, height / 2f, minOf(width, height) / 4f, paint)
            val file = File(photoDirectory, "$id.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            // Robolectric 的 native ImageDecoder 不支持 Windows 文件描述符；固定图像缓存用于渲染与手势测试。
            cache[MemoryCache.Key("thumbnail:${Uri.fromFile(file)}")] = MemoryCache.Value(bitmap.asImage())
            cache[MemoryCache.Key("preview:${Uri.fromFile(file)}")] = MemoryCache.Value(bitmap.asImage())
        }
    }

    @After
    fun releasePhotos() {
        imageLoader.shutdown()
        assertTrue("测试图片必须成功加载：$imageErrors", imageErrors.isEmpty())
    }

    /** 让真正的 Coil 请求停在解码边界，验证加载时序和手势，不用已缓存的大图掩盖等待问题。 */
    @OptIn(DelicateCoilApi::class)
    private fun delayPreviewDecoding() {
        val cache = imageLoader.memoryCache!!
        entries.map { it.photo.uri }.distinct().forEach { cache.remove(MemoryCache.Key("preview:$it")) }
        imageLoader = imageLoader.newBuilder().components {
            add(object : Fetcher.Factory<coil3.Uri> {
                override fun create(data: coil3.Uri, options: Options, imageLoader: ImageLoader): Fetcher =
                    Fetcher {
                        pendingDecodes += data.toString()
                        decodeGate.await()
                        val portrait = data.toString().endsWith("0.png")
                        val bitmap = Bitmap.createBitmap(if (portrait) 180 else 320, if (portrait) 320 else 180,
                            Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
                        decodedPhotos += data.toString()
                        ImageFetchResult(bitmap.asImage(), isSampled = false, dataSource = DataSource.DISK)
                    }
            })
        }.build()
        SingletonImageLoader.setUnsafe(imageLoader)
    }

    @Test
    fun previewDecodingStartsWhileThePhotoIsStillEntering() {
        delayPreviewDecoding()
        setContent()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("照片 1").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.waitUntil { pendingDecodes.isNotEmpty() }
        assertTrue("大图应在入场期间就开始加载", imageBounds(1).width < 411f)
        assertTrue(decodedPhotos.isEmpty())
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun slowPreviewDecodingDoesNotPreventPaging() {
        delayPreviewDecoding()
        setContent()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("照片 1").performClick()
        compose.mainClock.advanceTimeBy(400)
        compose.waitUntil { pendingDecodes.isNotEmpty() }
        assertTrue(decodedPhotos.isEmpty())
        // 高清图尚未返回，仍应能从当前缩略图直接翻到下一张。
        compose.onRoot().performTouchInput { swipeLeft(durationMillis = 220) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("加载图片不能锁住翻页", 1, pager.currentPage)
        assertTrue(decodedPhotos.isEmpty())
        compose.waitUntil { pendingDecodes.any { it.endsWith("0.png") } }
        compose.runOnIdle { decodeGate.complete(Unit) }
        compose.waitUntil { decodedPhotos.isNotEmpty() }
        compose.waitForIdle()
        assertEquals(1, pager.currentPage)
    }

    @Test fun lightPreviewKeepsItsThumbnailUntilCrossfadingToTheDecodedImage() = verifyDecodedPhoto(ThemeMode.Light)
    @Test fun darkPreviewKeepsItsThumbnailUntilCrossfadingToTheDecodedImage() = verifyDecodedPhoto(ThemeMode.Dark)

    private fun verifyDecodedPhoto(mode: ThemeMode) {
        delayPreviewDecoding()
        setContent(mode)
        open(1)
        compose.waitUntil { pendingDecodes.isNotEmpty() }
        val bounds = imageBounds(1)
        val x = 40
        val y = bounds.center.y.toInt()
        val before = compose.onRoot().captureToImage().asAndroidBitmap().getPixel(x, y)
        assertEquals("加载过程中必须保留已显示的缩略图", Color.rgb(36, 143, 157), before)
        compose.runOnIdle { decodeGate.complete(Unit) }
        compose.waitUntil { previewResults.isNotEmpty() }
        compose.waitForIdle()
        val result = previewResults.last()
        assertEquals(DataSource.DISK, result.dataSource)
        assertTrue("清晰图片必须从缓存缩略图交叉淡入", result.isPlaceholderCached &&
            result.request.transitionFactory is CrossfadeTransition.Factory)
        assertRectNear(bounds, imageBounds(1))
        val samples = mutableListOf<Int>()
        // Coil 的交叉淡化使用真实时间；逐帧取样，检查升级清晰度时没有闪白、闪黑或改变图片位置。
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.mainClock.advanceTimeByFrame()
            val pixel = compose.onRoot().captureToImage().asAndroidBitmap().getPixel(x, y)
            samples += pixel
            pixel == Color.BLUE
        }
        assertTrue(samples.all { pixel ->
            Color.red(pixel) in 0..Color.red(before) && Color.green(pixel) in 0..Color.green(before) &&
                Color.blue(pixel) in Color.blue(before)..255
        })
        assertRectNear(bounds, imageBounds(1))
        captureFrame("${mode.name}-decoded-photo")
    }

    @Test fun lightPreviewExpandsFromThumbnail() = verifyOpening(ThemeMode.Light)
    @Test fun darkPreviewExpandsFromThumbnail() = verifyOpening(ThemeMode.Dark)

    @Test
    fun draggingFromSelectedPhotoSubtractsRangeAndBacktrackingRestoresIt() {
        setContent()
        compose.runOnIdle { selection = setOf(1L, 2L, 3L, 4L) }
        val second = gridBounds(2)
        val third = gridBounds(3)
        compose.onRoot().performTouchInput {
            down(second.center)
            advanceEventTime(650)
            moveTo(third.center)
        }
        assertEquals(setOf(1L, 4L), selection)
        compose.onRoot().performTouchInput { moveTo(second.center) }
        assertEquals(setOf(1L, 3L, 4L), selection)
        compose.onRoot().performTouchInput { up() }
        assertRectNear(second, gridBounds(2))
        assertEquals(listOf(HomeRoute), backStack.toList())
    }

    @Test
    fun longPressingAndClearingLastSelectionDoesNotMovePhoto() {
        setContent()
        val before = gridBounds(1)
        compose.onNodeWithContentDescription("照片 1").performTouchInput { longClick() }
        assertRectNear(before, gridBounds(1))
        compose.onRoot().performTouchInput { down(before.center); advanceEventTime(650); moveBy(Offset(1f, 0f)) }
        assertTrue(selection.isEmpty())
        assertRectNear(before, gridBounds(1))
        compose.onRoot().performTouchInput { up() }
        assertRectNear(before, gridBounds(1))
    }

    @Test
    fun tappingTheFarEdgeOfATileStillSelectsThatTile() {
        setContent()
        compose.onNodeWithContentDescription("照片 1").performTouchInput { longClick() }
        compose.onNodeWithContentDescription("照片 2").performTouchInput { click(Offset(width - 1f, height - 1f)) }
        assertEquals(setOf(1L, 2L), selection)
    }

    @Test
    fun earlyPredictiveBackContinuesFromEnteringFrameAndCanBeCancelled() {
        setContent()
        originalThumbnail = gridBounds(1)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("照片 1").performClick()
        compose.mainClock.advanceTimeBy(80)
        val entering = imageBounds(1)
        compose.runOnIdle { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT)) }
        compose.mainClock.advanceTimeByFrame()
        assertRectNear(entering, imageBounds(1), 2f)
        compose.runOnIdle { dispatcher.dispatchOnBackProgressed(BackEventCompat(150f, 100f, 0.5f, BackEventCompat.EDGE_LEFT)) }
        compose.mainClock.advanceTimeByFrame()
        assertTrue(imageBounds(1).width < entering.width)
        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(411f, imageBounds(1).width, 1f)
        compose.onRoot().performTouchInput { doubleClick(center) }
        assertTrue(imageBounds(1).height > 500f)
        compose.runOnIdle { dispatcher.onBackPressed() }
        assertRectNear(originalThumbnail, gridBounds(1))
    }

    @Test
    fun backDuringEntryKeepsAnExitAnimationVisible() {
        setContent()
        originalThumbnail = gridBounds(2)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("照片 2").performClick()
        compose.mainClock.advanceTimeBy(96)
        val entering = imageBounds(2)
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(48)
        val returning = imageBounds(2)
        assertTrue(returning.height > originalThumbnail.height)
        assertTrue(returning.height < entering.height)
        captureFrame("early-return")
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertRectNear(originalThumbnail, gridBounds(2))
    }

    @Test
    fun doubleTapZoomNeverOvershootsInEitherDirection() {
        setContent()
        open(1)
        val fitted = imageBounds(1).height
        for (zoomingIn in listOf(true, false)) {
            compose.mainClock.autoAdvance = false
            var previous = imageBounds(1).height
            compose.onRoot().performTouchInput { doubleClick(center, delayMillis = 60) }
            repeat(30) {
                compose.mainClock.advanceTimeByFrame()
                val height = imageBounds(1).height
                assertTrue("双击不能越过目标再回弹", height in (fitted - 1f)..(fitted * 2.5f + 1f))
                assertTrue("双击倍率应单调变化", if (zoomingIn) height >= previous - 0.1f else height <= previous + 0.1f)
                previous = height
            }
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
        }
    }

    @Test
    fun longPressThenTapPhotosAndSelectionMarksKeepsSelecting() {
        setContent()
        compose.onNodeWithContentDescription("照片 1").performTouchInput { longClick() }
        assertEquals(setOf(1L), selection)
        compose.onNodeWithContentDescription("照片 2").performTouchInput { click() }
        assertEquals("进入多选后点照片不能打开预览", setOf(1L, 2L), selection)
        compose.onNodeWithContentDescription("照片 3").performTouchInput { click(Offset(18f, 18f)) }
        assertEquals("点左上角勾选标记也应选择", setOf(1L, 2L, 3L), selection)
        assertEquals(listOf(HomeRoute), backStack.toList())
        for (id in 1..3) compose.onNodeWithContentDescription("照片 $id").performTouchInput { click() }
        assertTrue(selection.isEmpty())
    }

    @Test
    fun deselectingASelectedPhotoCompletesItsSpringWithoutCrashing() {
        setContent()
        compose.onNodeWithContentDescription("照片 1").performTouchInput { longClick() }
        compose.runOnIdle { selection = emptySet() }
        compose.waitForIdle()
        assertEquals(listOf(HomeRoute), backStack.toList())
    }

    @Test
    fun returningFromPreviewThenSelectingSeveralPhotosStaysInTheGrid() {
        setContent()
        open(1)
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.onNodeWithContentDescription("照片 2").performTouchInput { longClick() }
        for (id in 3..5) compose.onNodeWithContentDescription("照片 $id").performTouchInput { click(Offset(18f, 18f)) }
        assertEquals(setOf(2L, 3L, 4L, 5L), selection)
        for (id in 2..5) compose.onNodeWithContentDescription("照片 $id").performTouchInput { click() }
        compose.waitForIdle()
        assertTrue(selection.isEmpty())
        assertEquals(listOf(HomeRoute), backStack.toList())
    }

    @Test
    fun returnAfterPagingFindsTheCurrentPhotoAcrossSectionsAndHeaders() {
        setContent()
        open(1)
        pageTo(71)
        startBackGesture()
        // 手势开始时来源网格已经准备好；最后浏览的照片必须参与同一条收回轨迹。
        val target = gridBounds(71)
        val moving = imageBounds(71)
        assertTrue("返回图片应缩向当前缩略图", moving.width < fullImageWidth)
        assertTrue("屏幕外的返回目标应滚入屏幕", target.top >= 0f && target.bottom <= rootHeight)
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.onNodeWithContentDescription("照片 71").assertExists()
        assertEquals(target, gridBounds(71))
        assertEquals(listOf(HomeRoute), backStack.toList())
    }

    @Test
    fun cancelledBackRestoresPreviewAndCanReturnToAnotherPhoto() {
        setContent(ThemeMode.Dark)
        open(2)
        pageTo(70)
        val before = imageBounds(70)
        startBackGesture()
        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        assertRectNear(before, imageBounds(70))
        pageTo(2)
        compose.runOnIdle { dispatcher.onBackPressed() }
        assertRectNear(originalThumbnail, gridBounds(2))
    }

    @Test
    fun pagingBackBeforeClosingRestoresTheOriginalGridPosition() {
        setContent()
        open(1)
        pageTo(90)
        pageTo(1)
        compose.runOnIdle { dispatcher.onBackPressed() }
        assertRectNear(originalThumbnail, gridBounds(1))
    }

    @Test
    fun zoomedPhotoPreservesZoomWhenBackIsCancelledAndReturnsToItsThumbnail() {
        setContent()
        open(1)
        compose.onNode(hasContentDescription("照片 1") and !hasClickAction()).performTouchInput { doubleClick() }
        val zoomed = imageBounds(1)
        captureFrame("zoom-start")
        startBackGesture()
        captureFrame("zoom-return")
        val moving = imageBounds(1)
        assertTrue("放大的照片应朝缩略图收回，不应向屏幕边缘偏移",
            moving.center.x >= originalThumbnail.center.x - 1f && moving.center.x <= zoomed.center.x + 1f)
        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        assertRectNear(zoomed, imageBounds(1))
        compose.runOnIdle { dispatcher.onBackPressed() }
        assertRectNear(originalThumbnail, gridBounds(1))
    }

    @Test
    fun doubleTapAnimatesBothDirectionsAndZoomLocksPaging() {
        setContent()
        open(1)
        val fitted = imageBounds(1)
        compose.mainClock.autoAdvance = false
        compose.onRoot().performTouchInput { doubleClick(center) }
        compose.mainClock.advanceTimeBy(80)
        val middle = imageBounds(1)
        assertTrue("双击应有连续动画", middle.height > fitted.height && middle.height < fitted.height * 2.5f)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val zoomed = imageBounds(1)
        assertEquals(fitted.height * 2.5f, zoomed.height, 2f)
        compose.onRoot().performTouchInput { swipeLeft() }
        assertEquals("放大后拖动应平移照片", 0, pager.currentPage)
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onRoot().performTouchInput { doubleClick(center) }
        compose.mainClock.advanceTimeBy(80)
        assertTrue("缩小也应连续过渡", imageBounds(1).height in (fitted.height + 1)..(zoomed.height - 1))
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertRectNear(fitted, imageBounds(1))
        compose.onRoot().performTouchInput { swipeLeft() }
        assertEquals("还原后恢复横滑翻页", 1, pager.currentPage)
    }

    @Test
    fun rapidDoubleTapsCannotInterruptOrQueueAnotherZoom() {
        setContent()
        open(1)
        val fittedHeight = imageBounds(1).height
        for (targetScale in listOf(2.5f, 1f)) {
            compose.mainClock.autoAdvance = false
            compose.onRoot().performTouchInput { doubleClick(center, delayMillis = 60) }
            compose.mainClock.advanceTimeBy(64)
            compose.onRoot().performTouchInput { doubleClick(center, delayMillis = 60) }
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            assertEquals("动画期间的连点不能打断或排队反向缩放", fittedHeight * targetScale, imageBounds(1).height, 2f)
            compose.onNodeWithContentDescription("关闭预览").assertExists()
        }
    }

    @Test
    fun gestureThatStartsDuringZoomStaysBlockedAfterTheAnimationEnds() {
        setContent()
        open(1)
        val fittedHeight = imageBounds(1).height
        compose.mainClock.autoAdvance = false
        compose.onRoot().performTouchInput { doubleClick(center, delayMillis = 60) }
        compose.mainClock.advanceTimeBy(64)
        compose.onRoot().performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().performTouchInput {
            advanceEventTime(600)
            moveBy(Offset(160f, 50f), delayMillis = 160)
            up()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("被屏蔽的按压不能在动画结束后突然变为拖动关闭", 2, backStack.size)
        assertEquals(fittedHeight * 2.5f, imageBounds(1).height, 2f)
        compose.onNodeWithContentDescription("关闭预览").assertExists()
        assertEquals(0, pager.currentPage)
    }

    @Test
    fun pinchBelowFitSpringsBackAfterAllFingersLift() {
        setContent()
        open(1)
        val fitted = imageBounds(1)
        compose.onRoot().performTouchInput {
            down(0, center - Offset(100f, 0f))
            down(1, center + Offset(100f, 0f))
            updatePointerTo(0, center - Offset(15f, 0f))
            updatePointerTo(1, center + Offset(15f, 0f))
            move()
        }
        assertTrue("捏合可以带阻尼缩到适配尺寸以下", imageBounds(1).height < fitted.height)
        compose.onRoot().performTouchInput {
            up(1)
            moveBy(0, Offset(40f, 25f))
        }
        assertTrue("剩下一根手指时仍保持本次捏合", imageBounds(1).height < fitted.height)
        compose.mainClock.autoAdvance = false
        compose.onRoot().performTouchInput { up(0) }
        compose.mainClock.advanceTimeBy(80)
        assertTrue("松手后应逐步回弹", imageBounds(1).height < fitted.height)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertRectNear(fitted, imageBounds(1))
        assertEquals(0, pager.currentPage)
        assertEquals(2, backStack.size)
    }

    @Test
    fun pinchStaysAnchoredUntilReleaseThenCentersTheLetterboxedImage() {
        setContent()
        open(1)
        val fitted = imageBounds(1)
        val focus = fitted.center + Offset(0f, 50f)
        val imageFraction = (focus.y - fitted.top) / fitted.height
        compose.onRoot().performTouchInput {
            down(0, focus - Offset(40f, 0f))
            down(1, focus + Offset(40f, 0f))
            updatePointerTo(0, focus - Offset(80f, 0f))
            updatePointerTo(1, focus + Offset(80f, 0f))
            move()
        }
        val pinching = imageBounds(1)
        assertEquals(fitted.height * 2f, pinching.height, 2f)
        assertEquals("手指下的图像内容应保持在原处", focus.y, pinching.top + pinching.height * imageFraction, 2f)
        compose.onRoot().performTouchInput { up(0); up(1) }
        assertEquals("松手后才消除横图上下的多余偏移", fitted.center.y, imageBounds(1).center.y, 1f)
        assertEquals(0, pager.currentPage)
    }

    @Test
    fun dragDismissStartsReturningFromTheReleasedPosition() {
        setContent()
        open(1)
        val measuredHeight = compose.onNode(hasContentDescription("照片 1") and !hasClickAction())
            .fetchSemanticsNode().layoutInfo.height
        compose.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(20f, 155f), delayMillis = 240)
        }
        val dragged = imageBounds(1)
        assertEquals("拖动缩放只改变图层，不逐帧重新测量图片", measuredHeight,
            compose.onNode(hasContentDescription("照片 1") and !hasClickAction()).fetchSemanticsNode().layoutInfo.height)
        captureFrame("drag-before-release")
        compose.mainClock.autoAdvance = false
        compose.onRoot().performTouchInput { up() }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        assertRectNear(dragged, imageBounds(1), tolerance = 5f)
        captureFrame("drag-return-start")
        compose.mainClock.advanceTimeBy(80)
        assertTrue("松手后从当前位置连续缩回", imageBounds(1).height < dragged.height)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertRectNear(originalThumbnail, gridBounds(1))
    }

    @Test
    fun shortDragSpringsBackAndVerticalDragReturnsCurrentPhoto() {
        setContent()
        open(1)
        pageTo(71)
        val before = imageBounds(71)
        compose.onRoot().performTouchInput { swipe(center, center + Offset(0f, 55f), durationMillis = 500) }
        assertEquals("短距离拖动不关闭", 2, backStack.size)
        assertRectNear(before, imageBounds(71))
        compose.onRoot().performTouchInput { swipe(center, center + Offset(45f, 200f), durationMillis = 400) }
        compose.waitForIdle()
        assertEquals(listOf(HomeRoute), backStack.toList())
        val target = gridBounds(71)
        assertTrue("拖动关闭也应返回最后浏览的照片", target.top >= 0f && target.bottom <= rootHeight)
    }

    @Test
    fun holdingThenDraggingSidewaysClosesInsteadOfPaging() {
        setContent()
        open(1)
        compose.onRoot().performTouchInput {
            down(center)
            advanceEventTime(600)
            moveBy(Offset(150f, 5f), delayMillis = 160)
            up()
        }
        compose.waitForIdle()
        assertEquals(listOf(HomeRoute), backStack.toList())
        assertRectNear(originalThumbnail, gridBounds(1))
    }

    @Test fun lightBarsAlreadySampleThePhotoDuringEntry() = verifyEntryBlur(ThemeMode.Light)
    @Test fun darkBarsAlreadySampleThePhotoDuringEntry() = verifyEntryBlur(ThemeMode.Dark)

    private fun verifyEntryBlur(mode: ThemeMode) {
        setContent(mode)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("照片 2").performClick()
        compose.mainClock.advanceTimeBy(208)
        val frame = compose.onRoot().captureToImage().asAndroidBitmap()
        val left = frame.getPixel(120, 54)
        val right = frame.getPixel(290, 54)
        assertTrue("入场尚未结束时，标题栏已经能采样到照片的两种颜色",
            kotlin.math.abs(Color.red(left) - Color.red(right)) > 20)
        captureFrame("${mode.name}-entry-bars")
        compose.mainClock.autoAdvance = true
    }

    @Test fun lightFrostedBars() = verifyFrostedBars(ThemeMode.Light)
    @Test fun darkFrostedBars() = verifyFrostedBars(ThemeMode.Dark)

    private fun verifyFrostedBars(mode: ThemeMode) {
        setContent(mode)
        open(2)
        compose.onRoot().performTouchInput { doubleClick(center) }
        compose.onNodeWithContentDescription("关闭预览").assertExists()
        captureFrame("${mode.name}-frosted-bars")
    }

    private var fullImageWidth = 0f
    private var rootHeight = 0f
    private lateinit var originalThumbnail: Rect

    private fun verifyOpening(mode: ThemeMode) {
        setContent(mode)
        originalThumbnail = gridBounds(1)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("照片 1").performTouchInput { click() }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        val start = imageBounds(1)
        captureFrame("${mode.name}-start")
        assertRectNear(originalThumbnail, start, tolerance = 1f)
        compose.mainClock.advanceTimeBy(48)
        assertTrue("打开后第一段动画就应明显响应", imageBounds(1).width > start.width * 1.2f)
        compose.mainClock.advanceTimeBy(80)
        val middle = imageBounds(1)
        captureFrame("${mode.name}-middle")
        compose.mainClock.advanceTimeBy(80)
        val overshoot = imageBounds(1)
        captureFrame("${mode.name}-spring-overshoot")
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val end = imageBounds(1)
        captureFrame("${mode.name}-end")
        assertTrue("图片应连续放大", middle.width > start.width && middle.width < end.width)
        assertTrue("不应从屏幕右侧滑入", middle.left < originalThumbnail.right)
        assertTrue("入场应轻微超调后收敛", overshoot.height > end.height)
        assertEquals("横图保持真实比例", 1600f / 900f, end.width / end.height, 0.02f)
    }

    private fun open(photoId: Long) {
        originalThumbnail = gridBounds(photoId)
        compose.onNodeWithContentDescription("照片 $photoId").performClick()
        compose.waitForIdle()
    }

    private fun pageTo(photoId: Long) {
        compose.runOnIdle { scope.launch { pager.scrollToPage((photoId - 1).toInt()) } }
        compose.waitForIdle()
        fullImageWidth = imageBounds(photoId).width
    }

    private fun startBackGesture() {
        compose.runOnIdle { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT)) }
        compose.runOnIdle { dispatcher.dispatchOnBackProgressed(BackEventCompat(150f, 100f, 0.5f, BackEventCompat.EDGE_LEFT)) }
    }

    private fun gridBounds(id: Long) = compose.onNode(hasContentDescription("照片 $id") and hasClickAction())
        .fetchSemanticsNode().boundsInRoot

    private fun imageBounds(id: Long) = compose.onNode(hasContentDescription("照片 $id") and !hasClickAction())
        .fetchSemanticsNode().boundsInRoot

    private fun assertRectNear(expected: Rect, actual: Rect, tolerance: Float = 1f) {
        assertEquals("left", expected.left, actual.left, tolerance)
        assertEquals("top", expected.top, actual.top, tolerance)
        assertEquals("width", expected.width, actual.width, tolerance)
        assertEquals("height", expected.height, actual.height, tolerance)
    }

    private fun captureFrame(name: String) {
        val output = System.getProperty("coursetag.photoFrames") ?: return
        val directory = File(output).apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun setContent(mode: ThemeMode = ThemeMode.Light) {
        compose.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            scope = rememberCoroutineScope()
            CourseTagTheme(mode) {
                AppNavigationDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeAt(backStack.lastIndex) },
                    entryProvider = entryProvider {
                        entry<HomeRoute> {
                            PhotoGrid(
                                sections = entries.chunked(20).mapIndexed { index, photos ->
                                    PhotoSection("$index", "分组 $index", entries = photos)
                                },
                                selection = selection,
                                onSelectionChange = { selection = it },
                                onOpen = { entry, _ -> backStack += PhotoViewerRoute(entry.id, PhotosGridKey) },
                                sourceKey = PhotosGridKey,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(12.dp),
                                header = {
                                    item(span = { GridItemSpan(maxLineSpan) }) { Text("课签") }
                                    item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.height(60.dp)) }
                                },
                            )
                        }
                        entry<PhotoViewerRoute>(metadata = PhotoViewerSceneMetadata) { route ->
                            pager = rememberPagerState(initialPage = (route.photoId - 1).toInt()) { entries.size }
                            PhotoViewerContent(entries, pager, route.sourceKey,
                                onBack = { backStack.removeAt(backStack.lastIndex) }, onTag = {}, onRemove = {})
                        }
                    },
                )
            }
        }
        rootHeight = compose.onRoot().fetchSemanticsNode().boundsInRoot.height
    }
}
