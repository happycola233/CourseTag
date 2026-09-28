package com.happycola233.coursetag.domain

import com.happycola233.coursetag.data.AppData
import com.happycola233.coursetag.data.Course
import com.happycola233.coursetag.data.DeviceState
import com.happycola233.coursetag.data.media.Photo
import com.happycola233.coursetag.data.naming.FileNames
import com.happycola233.coursetag.data.naming.ParsedName
import com.happycola233.coursetag.data.naming.PhotoNameParser
import com.happycola233.coursetag.data.schedule.ClassIndex
import com.happycola233.coursetag.data.schedule.ClassSession
import com.happycola233.coursetag.data.schedule.courseNames
import java.time.DayOfWeek
import java.time.Instant

data class PhotoEntry(
    val photo: Photo,
    val parsed: ParsedName,
    /** 按课表识别出的所在课程；拍摄时间不在任何上课时段内时为 null。 */
    val session: ClassSession?,
    /** 文件名使用的是旧命名格式。 */
    val usesPreviousFormat: Boolean,
    /**
     * 文件名中的课程与拍摄时段在课表中的课程不同，例如物理课上拍摄的照片标记为「高等数学」。
     * 用户确认保留后不再提示。
     */
    val conflictsWithSchedule: Boolean = false,
) {
    val id: Long get() = photo.id
    val course: String? get() = parsed.course
}

data class Album(val id: Long, val name: String, val count: Int, val cover: Photo)

/** 课程的来源与关联状态。 */
enum class CourseStatus {
    /** 已添加，并关联了当前已导入课表中的课程。 */
    Scheduled,

    /** 已添加，但不在任何已导入的课表中（手动添加，或所属课表已移除）。 */
    Standalone,

    /** 只出现在照片文件名中，尚未添加到课程列表。 */
    Detected,
}

/** 课程列表中的一项；[course] 为 null 表示该课程只出现在照片文件名中、尚未保存。 */
data class CourseSummary(
    val name: String,
    val course: Course?,
    val status: CourseStatus,
    val photoCount: Int,
    /** 标记为本课程、却拍摄于其他课程上课时间的照片数。 */
    val conflictCount: Int,
    /** 课表中每周上课的日子，仅 [CourseStatus.Scheduled] 课程有值。 */
    val classDays: List<DayOfWeek> = emptyList(),
)

/** 被忽略的文件名后缀，以及仍带有该后缀的照片数。 */
data class IgnoredTag(val name: String, val photoCount: Int)

/** 图库的派生视图：每张照片的课程标记与课表匹配结果，以及界面需要的统计。 */
data class Library(
    val entries: List<PhotoEntry>,
    val albums: List<Album>,
    val parser: PhotoNameParser,
    val hasSchedule: Boolean,
    /** 当前生效的忽略后缀；已成为课程的名称不再忽略。 */
    val ignoredTagNames: List<String> = emptyList(),
) {
    val byId: Map<Long, PhotoEntry> = entries.associateBy { it.id }
    val courseCounts: Map<String, Int> = entries.mapNotNull { it.course }.groupingBy { it }.eachCount()
    val previousFormatCount: Int = entries.count { it.usesPreviousFormat }
    val untaggedClassPhotos: List<PhotoEntry> = entries.filter { it.session != null && it.course == null }
    val taggedCount: Int = entries.count { it.course != null }
    val conflicts: List<PhotoEntry> = entries.filter { it.conflictsWithSchedule }
    val conflictCounts: Map<String, Int> = conflicts.groupingBy { it.course!! }.eachCount()
    val ignoredTags: List<IgnoredTag> = run {
        val counts = entries.mapNotNull { it.parsed.ignoredTag }.groupingBy { it }.eachCount()
        ignoredTagNames.map { IgnoredTag(it, counts[it] ?: 0) }
    }

    companion object {
        fun build(photos: List<Photo>, data: AppData, deviceState: DeviceState = DeviceState()): Library {
            val settings = data.settings
            val knownCourses = buildSet {
                data.courses.forEach { add(it.name) }
                data.schedules.forEach { schedule ->
                    schedule.courseNames().forEach { add(FileNames.sanitizeCourseName(it)) }
                }
            }
            val ignoredTagNames = data.ignoredTags.filter { it !in knownCourses }
            val ignoredTagSet = ignoredTagNames.toSet()
            val parser = PhotoNameParser(settings.tagFormat, settings.previousFormats, { it in ignoredTagSet }) {
                it in knownCourses
            }
            val index = ClassIndex(
                schedules = data.schedules,
                courses = data.courses,
                beforeMillis = settings.minutesBeforeClass * 60_000L,
                afterMillis = settings.minutesAfterClass * 60_000L,
            )
            val confirmed = deviceState.confirmedCourses
            val entries = photos.map { photo ->
                val parsed = parser.parse(photo.name)
                val session = index.match(photo.takenAt)
                val course = parsed.course
                PhotoEntry(
                    photo = photo,
                    parsed = parsed,
                    session = session,
                    usesPreviousFormat = parsed.format != null && parsed.format != settings.tagFormat,
                    conflictsWithSchedule = course != null && session != null &&
                        session.course != course && confirmed[photo.id] != course,
                )
            }
            val albums = photos.groupBy { it.albumId }
                .map { (id, items) -> Album(id, items.first().albumName, items.size, items.first()) }
                .sortedByDescending { it.count }
            return Library(entries, albums, parser, hasSchedule = !index.isEmpty, ignoredTagNames)
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
    // 课表课程名 → 每周上课的日子，按课表自身的时区换算。
    val classDaysByScheduleCourse = buildMap<String, MutableSet<DayOfWeek>> {
        for (schedule in data.schedules) {
            val zone = ClassIndex.zoneOf(schedule)
            for (meeting in schedule.meetings) {
                getOrPut(meeting.course) { mutableSetOf() } += Instant.ofEpochMilli(meeting.start).atZone(zone).dayOfWeek
            }
        }
    }
    val saved = data.courses.map { course ->
        val linked = course.scheduleNames.filter { it in classDaysByScheduleCourse }
        CourseSummary(
            name = course.name,
            course = course,
            status = if (linked.isEmpty()) CourseStatus.Standalone else CourseStatus.Scheduled,
            photoCount = library.courseCounts[course.name] ?: 0,
            conflictCount = library.conflictCounts[course.name] ?: 0,
            classDays = linked.flatMap { classDaysByScheduleCourse.getValue(it) }.distinct().sorted(),
        )
    }
    val savedNames = data.courses.map { it.name }.toSet()
    val unsaved = library.courseCounts.filterKeys { it !in savedNames }.map { (name, count) ->
        CourseSummary(
            name = name,
            course = null,
            status = CourseStatus.Detected,
            photoCount = count,
            conflictCount = library.conflictCounts[name] ?: 0,
        )
    }
    return (saved + unsaved).sortedWith(
        // 比较键必须统一为 Long，避免未保存课程的默认值被装箱成 Int。
        compareByDescending<CourseSummary> { it.course?.lastUsedAt ?: 0L }
            .thenByDescending { it.photoCount }
            .thenBy { it.name },
    )
}
