package com.happycola233.coursetag.data.schedule

import com.happycola233.coursetag.data.ClassMeeting
import com.happycola233.coursetag.data.Course
import com.happycola233.coursetag.data.Schedule
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClassIndexTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    private fun at(text: String): Long = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private val meetings = listOf(
        ClassMeeting("概率论与数理统计", at("2026-09-17T08:15"), at("2026-09-17T09:35")),
        ClassMeeting("数字逻辑", at("2026-09-17T09:55"), at("2026-09-17T11:15")),
        ClassMeeting("数字逻辑", at("2026-09-24T09:55"), at("2026-09-24T11:15")),
    )
    private val schedule = Schedule(
        id = "s",
        name = "大二上",
        importedAt = 0,
        zoneId = zone.id,
        firstWeekEpochDay = ClassIndex.firstWeekEpochDay(meetings, zone),
        meetings = meetings,
    )
    private val index = ClassIndex(
        schedules = listOf(schedule),
        courses = listOf(Course(id = "c", name = "数逻", scheduleNames = listOf("数字逻辑"), createdAt = 0)),
        beforeMillis = 15 * 60_000L,
        afterMillis = 15 * 60_000L,
    )

    @Test
    fun prefersSessionInProgressWhenWindowsOverlap() {
        // 课间同时处于两节课的容差内，归入时间上更近的一节；距离相同时归入即将开始的一节。
        assertEquals("数逻", index.match(at("2026-09-17T09:47"))?.course)
        assertEquals("数逻", index.match(at("2026-09-17T09:45"))?.course)
        assertEquals("概率论与数理统计", index.match(at("2026-09-17T09:38"))?.course)
        assertEquals("概率论与数理统计", index.match(at("2026-09-17T08:00"))?.course)
    }

    @Test
    fun ignoresPhotosOutsideClassWindow() {
        assertNull(index.match(at("2026-09-17T11:40")))
        assertNull(index.match(at("2026-09-18T10:00")))
    }

    @Test
    fun countsTeachingWeeksFromFirstMonday() {
        assertEquals(1, index.match(at("2026-09-17T10:00"))?.week)
        assertEquals(2, index.match(at("2026-09-24T10:00"))?.week)
    }
}
