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

/** 核对与上课时间不符的照片；[courseName] 不为空时只显示该课程的照片。 */
@Serializable
data class ConflictsRoute(val courseName: String? = null) : Route

@Serializable
data class PhotoViewerRoute(val photoId: Long, val sourceKey: String? = null) : Route

@Serializable
data object NamingFormatRoute : Route

@Serializable
data object HistoryRoute : Route

@Serializable
data object LicensesRoute : Route
