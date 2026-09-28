package com.happycola233.coursetag.domain

import android.net.Uri
import com.happycola233.coursetag.data.AppData
import com.happycola233.coursetag.data.ClassMeeting
import com.happycola233.coursetag.data.Course
import com.happycola233.coursetag.data.Schedule
import com.happycola233.coursetag.data.ics.IcsParser
import com.happycola233.coursetag.data.media.Photo
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryTest {
    @Test
    fun importedCoursesCanBeListedAlongsidePhotoOnlyCourses() {
        val data = AppData()
        val photos = photosFor("高等数学", "大学英语", "大学英语")
        val library = Library.build(photos, data)
        val calendar = IcsParser.parse(
            """
                BEGIN:VCALENDAR
                VERSION:2.0
                BEGIN:VEVENT
                UID:math
                SUMMARY:高等数学
                DTSTART;TZID=Asia/Shanghai:20260928T080000
                DTEND;TZID=Asia/Shanghai:20260928T093000
                END:VEVENT
                END:VCALENDAR
            """.trimIndent(),
            ZoneId.of("Asia/Shanghai"),
        )
        val draft = buildImportDraft("秋季课表", calendar, data)
        val imported = data.withImportedSchedule(draft, "秋季课表", setOf("高等数学"), now = 1_790_000_000_000L)

        // 数据流可能先收到新课表、后收到重建的图库，两种状态都应正常合并课程。
        for (currentLibrary in listOf(library, Library.build(photos, imported))) {
            val summaries = courseSummaries(imported, currentLibrary)
            assertEquals(listOf("大学英语", "高等数学"), summaries.map { it.name })
            assertEquals(listOf(2, 1), summaries.map { it.photoCount })
            assertNull(summaries[0].course)
            assertEquals(CourseStatus.Detected, summaries[0].status)
            assertNotNull(summaries[1].course)
            assertEquals(CourseStatus.Scheduled, summaries[1].status)
        }
    }

    @Test
    fun mixedCoursesAreOrderedByLastUseThenPhotoCountThenName() {
        val data = AppData(courses = listOf(
            Course(id = "older", name = "较早使用", createdAt = 0, lastUsedAt = 1_790_000_000_000L),
            Course(id = "unused", name = "B课程", createdAt = 0),
            Course(id = "recent", name = "最近使用", createdAt = 0, lastUsedAt = 1_790_000_000_001L),
        ))
        val library = Library.build(
            photosFor("C课程", "C课程", "C课程", "B课程", "A课程", "较早使用", "较早使用"),
            data,
        )

        val summaries = courseSummaries(data, library)

        assertEquals(listOf("最近使用", "较早使用", "C课程", "A课程", "B课程"), summaries.map { it.name })
        assertEquals(listOf(0, 2, 3, 1, 1), summaries.map { it.photoCount })
    }

    @Test
    fun deletedScheduleCourseCanBeIgnoredAndRestored() {
        val name = "高等数学"
        val data = AppData(
            courses = listOf(Course(id = "math", name = name, scheduleNames = listOf(name), createdAt = 0)),
            schedules = listOf(Schedule(
                id = "semester",
                name = "秋季课表",
                importedAt = 0,
                zoneId = "UTC",
                firstWeekEpochDay = 0,
                meetings = listOf(ClassMeeting(course = name, start = 0, end = 3_600_000)),
            )),
        ).withCourseDeleted(name)
        val photos = photosFor(name)
        val detected = courseSummaries(data, Library.build(photos, data)).single()
        assertEquals(CourseStatus.Detected, detected.status)

        val ignoredData = data.copy(ignoredTags = listOf(name))
        val ignoredLibrary = Library.build(photos, ignoredData)
        assertNull(ignoredLibrary.entries.single().course)
        assertEquals(name, ignoredLibrary.entries.single().parsed.ignoredTag)
        assertEquals(listOf(IgnoredTag(name, 1)), ignoredLibrary.ignoredTags)
        assertEquals(0, ignoredLibrary.taggedCount)
        assertEquals(emptyList<CourseSummary>(), courseSummaries(ignoredData, ignoredLibrary))
        // 忽略只改变文件名识别，课表仍可为这些未标记照片推荐课程。
        assertEquals(name, ignoredLibrary.untaggedClassPhotos.single().session?.course)

        val restoredData = ignoredData.copy(ignoredTags = ignoredData.ignoredTags - name)
        val restoredLibrary = Library.build(photos, restoredData)
        assertEquals(name, restoredLibrary.entries.single().course)
        assertEquals(emptyList<IgnoredTag>(), restoredLibrary.ignoredTags)
        assertEquals(CourseStatus.Detected, courseSummaries(restoredData, restoredLibrary).single().status)

        // 显式重新添加课程后，仍应优先识别已保存的课程名。
        val addedLibrary = Library.build(photos, ignoredData.withCoursesAdded(listOf(name), now = 1))
        assertEquals(name, addedLibrary.entries.single().course)
        assertNull(addedLibrary.entries.single().parsed.ignoredTag)
        assertEquals(emptyList<IgnoredTag>(), addedLibrary.ignoredTags)
    }

    private fun photosFor(vararg courseNames: String): List<Photo> = courseNames.mapIndexed { index, courseName ->
        Photo(
            id = index.toLong(),
            uri = Uri.parse("content://media/external/images/media/$index"),
            name = "IMG_$index（$courseName）.jpg",
            folder = "DCIM/Camera/",
            volume = "external",
            albumId = 1,
            albumName = "相机",
            takenAt = 0,
            width = 100,
            height = 100,
        )
    }
}
