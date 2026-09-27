package com.happycola233.coursetag.ui

import androidx.navigation3.runtime.NavKey
import com.happycola233.coursetag.ui.navigation.HomeRoute
import com.happycola233.coursetag.ui.navigation.NamingFormatRoute
import com.happycola233.coursetag.ui.navigation.PhotoViewerRoute
import com.happycola233.coursetag.ui.navigation.RenamePreviewRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationRecoveryTest {
    @Test
    fun processRecreationRemovesExpiredPreviewsIncludingCoveredOnes() {
        val stack = mutableListOf<NavKey>(HomeRoute, PhotoViewerRoute(1), RenamePreviewRoute, NamingFormatRoute)
        assertTrue(Navigator(stack).discardExpiredPreview(hasPreview = false))
        assertEquals(listOf(HomeRoute, PhotoViewerRoute(1), NamingFormatRoute), stack)
    }

    @Test
    fun configurationChangePreservesLivePreview() {
        val stack = mutableListOf<NavKey>(HomeRoute, RenamePreviewRoute)
        assertFalse(Navigator(stack).discardExpiredPreview(hasPreview = true))
        assertEquals(listOf(HomeRoute, RenamePreviewRoute), stack)
    }
}
