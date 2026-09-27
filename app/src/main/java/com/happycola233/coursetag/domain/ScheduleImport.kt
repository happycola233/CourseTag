package com.happycola233.coursetag.domain

import com.happycola233.coursetag.data.AppData
import com.happycola233.coursetag.data.Course
import com.happycola233.coursetag.data.Schedule
import com.happycola233.coursetag.data.ics.ParsedCalendar
import com.happycola233.coursetag.data.naming.FileNames
import com.happycola233.coursetag.data.schedule.ClassIndex
import java.util.UUID

/** 导入前的确认信息：用户可修改课表名称、取消勾选不需要的课程。 */
data class ImportDraft(
    val suggestedName: String,
    val calendar: ParsedCalendar,
    val courses: List<ImportCourse>,
)

data class ImportCourse(
    /** 课表中的原始课程名。 */
    val scheduleName: String,
    /** 导入后写入文件名时使用的课程名（已关联的课程沿用用户改过的名称）。 */
    val courseName: String,
    val meetingCount: Int,
)

fun buildImportDraft(suggestedName: String, calendar: ParsedCalendar, data: AppData): ImportDraft {
    val courses = calendar.meetings.groupBy { it.course }.map { (scheduleName, meetings) ->
        ImportCourse(
            scheduleName = scheduleName,
            courseName = data.courses.firstOrNull { scheduleName in it.scheduleNames }?.name
                ?: FileNames.sanitizeCourseName(scheduleName),
            meetingCount = meetings.size,
        )
    }.sortedByDescending { it.meetingCount }
    return ImportDraft(suggestedName, calendar, courses)
}

/**
 * 保存课表并关联课程：已关联的课程保持不变；同名的已有课程补充关联；其余课程新建。
 * 同名课表视为重新导入，原位替换。
 */
fun AppData.withImportedSchedule(draft: ImportDraft, name: String, included: Set<String>, now: Long): AppData {
    val meetings = draft.calendar.meetings.filter { it.course in included }
    val schedule = Schedule(
        id = UUID.randomUUID().toString(),
        name = name,
        importedAt = now,
        zoneId = draft.calendar.zone.id,
        firstWeekEpochDay = ClassIndex.firstWeekEpochDay(draft.calendar.meetings, draft.calendar.zone),
        meetings = meetings,
    )
    val existingIndex = schedules.indexOfFirst { it.name == name }
    val nextSchedules = if (existingIndex >= 0) {
        schedules.toMutableList().apply { set(existingIndex, schedule) }
    } else {
        schedules + schedule
    }

    val nextCourses = courses.toMutableList()
    for (scheduleName in draft.courses.map { it.scheduleName }.filter { it in included }) {
        if (nextCourses.any { scheduleName in it.scheduleNames }) continue
        val courseName = FileNames.sanitizeCourseName(scheduleName)
        val sameName = nextCourses.indexOfFirst { it.name == courseName }
        if (sameName >= 0) {
            val course = nextCourses[sameName]
            nextCourses[sameName] = course.copy(scheduleNames = course.scheduleNames + scheduleName)
        } else {
            nextCourses += Course(
                id = UUID.randomUUID().toString(),
                name = courseName,
                scheduleNames = listOf(scheduleName),
                createdAt = now,
            )
        }
    }
    return copy(schedules = nextSchedules, courses = nextCourses)
}
