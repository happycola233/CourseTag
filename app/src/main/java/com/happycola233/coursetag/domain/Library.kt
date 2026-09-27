package com.happycola233.coursetag.domain

import com.happycola233.coursetag.data.AppData
import com.happycola233.coursetag.data.Course
import com.happycola233.coursetag.data.media.Photo
import com.happycola233.coursetag.data.naming.FileNames
import com.happycola233.coursetag.data.naming.ParsedName
import com.happycola233.coursetag.data.naming.PhotoNameParser
import com.happycola233.coursetag.data.schedule.ClassIndex
import com.happycola233.coursetag.data.schedule.ClassSession
import com.happycola233.coursetag.data.schedule.courseNames

data class PhotoEntry(
    val photo: Photo,
    val parsed: ParsedName,
    /** 按课表识别出的所在课程；拍摄时间不在任何上课时段内时为 null。 */
    val session: ClassSession?,
    /** 文件名使用的是旧命名格式。 */
    val usesPreviousFormat: Boolean,
) {
    val id: Long get() = photo.id
    val course: String? get() = parsed.course
}

data class Album(val id: Long, val name: String, val count: Int, val cover: Photo)

/** 课程列表中的一项；[course] 为 null 表示该课程只出现在照片文件名中、尚未保存。 */
data class CourseSummary(
    val name: String,
    val course: Course?,
    val photoCount: Int,
    val linkedToSchedule: Boolean,
)

/** 图库的派生视图：每张照片的课程标记与课表匹配结果，以及界面需要的统计。 */
data class Library(
    val entries: List<PhotoEntry>,
    val albums: List<Album>,
    val parser: PhotoNameParser,
    val hasSchedule: Boolean,
) {
    val byId: Map<Long, PhotoEntry> = entries.associateBy { it.id }
    val courseCounts: Map<String, Int> = entries.mapNotNull { it.course }.groupingBy { it }.eachCount()
    val previousFormatCount: Int = entries.count { it.usesPreviousFormat }
    val untaggedClassPhotos: List<PhotoEntry> = entries.filter { it.session != null && it.course == null }
    val taggedCount: Int = entries.count { it.course != null }

    companion object {
        fun build(photos: List<Photo>, data: AppData): Library {
            val settings = data.settings
            val knownCourses = buildSet {
                data.courses.forEach { add(it.name) }
                data.schedules.forEach { schedule ->
                    schedule.courseNames().forEach { add(FileNames.sanitizeCourseName(it)) }
                }
            }
            val parser = PhotoNameParser(settings.tagFormat, settings.previousFormats) { it in knownCourses }
            val index = ClassIndex(
                schedules = data.schedules,
                courses = data.courses,
                beforeMillis = settings.minutesBeforeClass * 60_000L,
                afterMillis = settings.minutesAfterClass * 60_000L,
            )
            val entries = photos.map { photo ->
                val parsed = parser.parse(photo.name)
                PhotoEntry(
                    photo = photo,
                    parsed = parsed,
                    session = index.match(photo.takenAt),
                    usesPreviousFormat = parsed.format != null && parsed.format != settings.tagFormat,
                )
            }
            val albums = photos.groupBy { it.albumId }
                .map { (id, items) -> Album(id, items.first().albumName, items.size, items.first()) }
                .sortedByDescending { it.count }
            return Library(entries, albums, parser, hasSchedule = !index.isEmpty)
        }

        val Empty = Library(
            entries = emptyList(),
            albums = emptyList(),
            parser = PhotoNameParser(AppData().settings.tagFormat, emptyList()) { false },
            hasSchedule = false,
        )
    }
}

/** 合并已保存的课程与照片中出现的课程名，按最近使用与照片数量排序。 */
fun courseSummaries(data: AppData, library: Library): List<CourseSummary> {
    val scheduleCourses = data.schedules.flatMap { it.courseNames() }.toSet()
    val saved = data.courses.map { course ->
        CourseSummary(
            name = course.name,
            course = course,
            photoCount = library.courseCounts[course.name] ?: 0,
            linkedToSchedule = course.scheduleNames.any { it in scheduleCourses },
        )
    }
    val savedNames = data.courses.map { it.name }.toSet()
    val unsaved = library.courseCounts.filterKeys { it !in savedNames }.map { (name, count) ->
        CourseSummary(name = name, course = null, photoCount = count, linkedToSchedule = false)
    }
    return (saved + unsaved).sortedWith(
        // 比较键必须统一为 Long，避免未保存课程的默认值被装箱成 Int。
        compareByDescending<CourseSummary> { it.course?.lastUsedAt ?: 0L }
            .thenByDescending { it.photoCount }
            .thenBy { it.name },
    )
}
