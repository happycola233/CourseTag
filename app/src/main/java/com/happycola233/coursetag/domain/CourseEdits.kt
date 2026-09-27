package com.happycola233.coursetag.domain

import com.happycola233.coursetag.data.AppData
import com.happycola233.coursetag.data.Course
import com.happycola233.coursetag.data.schedule.courseNames
import java.util.UUID

private fun newCourse(name: String, now: Long, lastUsedAt: Long = 0) =
    Course(id = UUID.randomUUID().toString(), name = name, createdAt = now, lastUsedAt = lastUsedAt)

/** 添加课程，已存在的名称会被忽略。 */
fun AppData.withCoursesAdded(names: List<String>, now: Long): AppData {
    val existing = courses.map { it.name }.toMutableSet()
    val added = names.filter { existing.add(it) }.map { newCourse(it, now) }
    return copy(courses = courses + added)
}

/** 记录课程的最近使用时间；照片中用到的新课程名自动加入课程列表。 */
fun AppData.withCoursesUsed(names: Set<String>, now: Long): AppData {
    val updated = courses.map { if (it.name in names) it.copy(lastUsedAt = now) else it }
    val missing = names - courses.map { it.name }.toSet()
    return copy(courses = updated + missing.map { newCourse(it, now, lastUsedAt = now) })
}

/**
 * 修改课程名称并保留课表关联。新名称已被其他课程使用时两者合并，
 * 合并后的课程同时匹配两门课在课表中的上课时段。
 */
fun AppData.withCourseRenamed(from: String, to: String, now: Long): AppData {
    val source = courses.firstOrNull { it.name == from }
    val target = courses.firstOrNull { it.name == to }
    val next = when {
        source == null && target == null -> courses + newCourse(to, now, lastUsedAt = now)
        source == null -> courses
        target == null -> courses.map { if (it.id == source.id) it.copy(name = to) else it }
        else -> courses.filter { it.id != source.id }.map {
            if (it.id == target.id) {
                it.copy(
                    scheduleNames = (it.scheduleNames + source.scheduleNames).distinct(),
                    lastUsedAt = maxOf(it.lastUsedAt, source.lastUsedAt),
                )
            } else {
                it
            }
        }
    }
    return copy(courses = next)
}

fun AppData.withCourseDeleted(name: String): AppData = copy(courses = courses.filter { it.name != name })

/**
 * 移除课表。[removeUnusedCourses] 为 true 时，一并移除只来自该课表、且没有任何照片的课程；
 * 其他课程保留，文件名中的课程不受影响。
 */
fun AppData.withScheduleRemoved(id: String, removeUnusedCourses: Boolean, courseCounts: Map<String, Int>): AppData {
    val removed = schedules.firstOrNull { it.id == id } ?: return this
    val remaining = schedules.filter { it.id != id }
    if (!removeUnusedCourses) return copy(schedules = remaining)
    val removedNames = removed.courseNames().toSet()
    val stillScheduled = remaining.flatMap { it.courseNames() }.toSet()
    val nextCourses = courses.filterNot { course ->
        course.scheduleNames.isNotEmpty() &&
            course.scheduleNames.all { it in removedNames && it !in stillScheduled } &&
            (courseCounts[course.name] ?: 0) == 0
    }
    return copy(schedules = remaining, courses = nextCourses)
}
