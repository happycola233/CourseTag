package com.happycola233.coursetag.data.media

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.os.CancellationSignal
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowContentResolver
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [ThumbnailResolver::class])
class PhotoThumbnailFetcherTest {
    @Before fun reset() { ThumbnailResolver.reset() }

    @Test fun aBurstOfRequestsUsesBoundedSystemThumbnails() = runBlocking {
        ThumbnailResolver.started = CountDownLatch(2)
        val requests = (1..12).map { id -> async(Dispatchers.Default) { fetcher(id).fetch() } }
        assertTrue(ThumbnailResolver.started.await(5, TimeUnit.SECONDS))
        assertEquals(2, ThumbnailResolver.active.get())
        ThumbnailResolver.release.countDown()
        requests.awaitAll()
        assertEquals(2, ThumbnailResolver.peak.get())
        assertEquals(12, ThumbnailResolver.requestedSizes.size)
        assertTrue(ThumbnailResolver.requestedSizes.all { it == Size(384, 384) })
    }

    @Test fun cancellingAnInvisibleThumbnailCancelsItsProviderRequest() = runBlocking {
        val request = async(Dispatchers.Default) { fetcher(1).fetch() }
        assertTrue(ThumbnailResolver.started.await(5, TimeUnit.SECONDS))
        request.cancel()
        assertTrue("页面移除后必须中止系统中的缩略图读取", ThumbnailResolver.cancelled.await(5, TimeUnit.SECONDS))
        ThumbnailResolver.release.countDown()
        request.cancelAndJoin()
        assertEquals(0, ThumbnailResolver.active.get())
    }

    private fun fetcher(id: Int) = PhotoThumbnailFetcher(RuntimeEnvironment.getApplication().contentResolver,
        Uri.parse("content://media/external/images/media/$id"))
}

/** 只替换系统边界，真实的协程调度、并发上限与取消传播仍由生产 Fetcher 执行。 */
@Implements(ContentResolver::class)
class ThumbnailResolver : ShadowContentResolver() {
    @Implementation
    protected fun loadThumbnail(uri: Uri, size: Size, signal: CancellationSignal?): Bitmap {
        requestedSizes += size
        val count = active.incrementAndGet()
        peak.updateAndGet { maxOf(it, count) }
        signal?.setOnCancelListener { cancelled.countDown() }
        started.countDown()
        try {
            check(release.await(5, TimeUnit.SECONDS))
            signal?.throwIfCanceled()
            return Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        } finally {
            active.decrementAndGet()
        }
    }

    companion object {
        val requestedSizes = ConcurrentLinkedQueue<Size>()
        val active = AtomicInteger()
        val peak = AtomicInteger()
        var started = CountDownLatch(1)
        var release = CountDownLatch(1)
        var cancelled = CountDownLatch(1)
        fun reset() {
            requestedSizes.clear()
            active.set(0)
            peak.set(0)
            started = CountDownLatch(1)
            release = CountDownLatch(1)
            cancelled = CountDownLatch(1)
        }
    }
}
