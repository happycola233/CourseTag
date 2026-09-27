package com.happycola233.coursetag.data.schedule

import com.happycola233.coursetag.data.ClassMeeting
import com.happycola233.coursetag.data.Course
import com.happycola233.coursetag.data.Schedule
import com.happycola233.coursetag.data.naming.FileNames
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** 课表中的一节具体课程，[course] 已换算为写入文件名时使用的课程名。 */
data class ClassSession(
    val key: String,
    val scheduleName: String,
    val scheduleCourse: String,
    val course: String,
    val start: Long,
    val end: Long,
    val location: String?,
    val teacher: String?,
    val week: Int,
    val zone: ZoneId,
)

/**
 * 所有课表上课时段的有序索引。照片拍摄时间落在「课前 [beforeMillis] 至课后 [afterMillis]」内即视为课上照片；
 * 相邻两节课的容差区间重叠时，优先选择拍摄时间真正处于上课期间的那节，否则取距离更近的一节。
 */
class ClassIndex(
    schedules: List<Schedule>,
    courses: List<Course>,
    private val beforeMillis: Long,
    private val afterMillis: Long,
) {
    private val sessions: List<ClassSession>
    private val longestMillis: Long

    init {
        val nameByScheduleCourse = buildMap {
            for (course in courses) for (name in course.scheduleNames) putIfAbsent(name, course.name)
        }
        sessions = schedules.flatMap { schedule ->
            val zone = zoneOf(schedule)
            schedule.meetings.map { meeting ->
                ClassSession(
                    key = "${schedule.id}:${meeting.start}:${meeting.course}",
                    scheduleName = schedule.name,
                    scheduleCourse = meeting.course,
                    course = nameByScheduleCourse[meeting.course] ?: FileNames.sanitizeCourseName(meeting.course),
                    start = meeting.start,
                    end = meeting.end,
                    location = meeting.location,
                    teacher = meeting.teacher,
                    week = weekOf(schedule, meeting.start, zone),
                    zone = zone,
                )
            }
        }.sortedBy { it.start }
        longestMillis = sessions.maxOfOrNull { it.end - it.start } ?: 0
    }

    val isEmpty: Boolean get() = sessions.isEmpty()

    fun match(time: Long): ClassSession? {
        if (sessions.isEmpty()) return null
        // 找到最后一个「开始前容差」不晚于拍摄时间的课程，再向前检查仍可能覆盖该时间的课程。
        var low = 0
        var high = sessions.lastIndex
        var last = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (sessions[mid].start - beforeMillis <= time) {
                last = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        var best: ClassSession? = null
        var bestDistance = Long.MAX_VALUE
        var index = last
        val earliestRelevantStart = time - longestMillis - afterMillis
        while (index >= 0 && sessions[index].start >= earliestRelevantStart) {
            val session = sessions[index]
            if (time <= session.end + afterMillis) {
                val distance = session.distanceTo(time)
                if (distance < bestDistance) {
                    best = session
                    bestDistance = distance
                }
            }
            index--
        }
        return best
    }

    companion object {
        fun zoneOf(schedule: Schedule): ZoneId =
            runCatching { ZoneId.of(schedule.zoneId) }.getOrDefault(ZoneId.systemDefault())

        fun weekOf(schedule: Schedule, time: Long, zone: ZoneId = zoneOf(schedule)): Int {
            val day = Instant.ofEpochMilli(time).atZone(zone).toLocalDate().toEpochDay()
            return Math.floorDiv(day - schedule.firstWeekEpochDay, 7L).toInt() + 1
        }

        /** 以最早一节课所在周的周一作为第 1 教学周的起点。 */
        fun firstWeekEpochDay(meetings: List<ClassMeeting>, zone: ZoneId): Long {
            val first = meetings.minOf { it.start }
            return Instant.ofEpochMilli(first).atZone(zone).toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toEpochDay()
        }
    }
}

/** 课程在课表中每周固定的一个时段，[weeks] 为该时段出现的教学周。 */
data class WeeklySlot(
    val scheduleName: String,
    val dayOfWeek: DayOfWeek,
    val start: LocalTime,
    val end: LocalTime,
    val location: String?,
    val teacher: String?,
    val weeks: List<Int>,
)

fun weeklySlots(schedules: List<Schedule>, scheduleCourses: Collection<String>): List<WeeklySlot> =
    schedules.flatMap { schedule ->
        val zone = ClassIndex.zoneOf(schedule)
        schedule.meetings.filter { it.course in scheduleCourses }
            .groupBy { meeting ->
                val start = Instant.ofEpochMilli(meeting.start).atZone(zone)
                val end = Instant.ofEpochMilli(meeting.end).atZone(zone)
                listOf(start.dayOfWeek, start.toLocalTime(), end.toLocalTime(), meeting.location, meeting.teacher)
            }
            .map { (key, meetings) ->
                WeeklySlot(
                    scheduleName = schedule.name,
                    dayOfWeek = key[0] as DayOfWeek,
                    start = key[1] as LocalTime,
                    end = key[2] as LocalTime,
                    location = key[3] as String?,
                    teacher = key[4] as String?,
                    weeks = meetings.map { ClassIndex.weekOf(schedule, it.start, zone) }.distinct().sorted(),
                )
            }
    }.sortedWith(compareBy({ it.weeks.first() }, { it.dayOfWeek }, { it.start }))

/** 课表覆盖的日期范围。 */
fun Schedule.dateRange(): ClosedRange<LocalDate> {
    val zone = ClassIndex.zoneOf(this)
    val first = Instant.ofEpochMilli(meetings.minOf { it.start }).atZone(zone).toLocalDate()
    val last = Instant.ofEpochMilli(meetings.maxOf { it.end }).atZone(zone).toLocalDate()
    return first..last
}

fun Schedule.courseNames(): List<String> = meetings.map { it.course }.distinct()

/** 拍摄时间与上课时段的距离，处于上课期间时为 0。 */
private fun ClassSession.distanceTo(time: Long): Long = when {
    time < start -> start - time
    time > end -> time - end
    else -> 0
}
