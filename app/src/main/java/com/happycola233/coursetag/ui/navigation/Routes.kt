package com.happycola233.coursetag.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey

@Serializable
data object HomeRoute : Route

/** 按课表识别出的课上照片确认页。 */
@Serializable
data object SmartTagRoute : Route

@Serializable
data object RenamePreviewRoute : Route

@Serializable
data class CourseDetailRoute(val courseName: String) : Route

@Serializable
data class PhotoViewerRoute(val photoId: Long) : Route

@Serializable
data object NamingFormatRoute : Route

@Serializable
data object HistoryRoute : Route

@Serializable
data object LicensesRoute : Route
