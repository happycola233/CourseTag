package com.happycola233.coursetag.data.ics

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsParserTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")

    private fun at(text: String): Long = LocalDateTime.parse(text).atZone(shanghai).toInstant().toEpochMilli()

    /** 结构与 WakeUp 课程表导出一致：LF 换行、折行、VALARM 子组件与「教室\n老师\n编号」备注。 */
    private val wakeUpCalendar = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//YZune//WakeUpSchedule//EN
        BEGIN:VTIMEZONE
        TZID:Asia/Shanghai
        BEGIN:STANDARD
        TZOFFSETFROM:+0800
        TZOFFSETTO:+0800
        DTSTART:19700101T000000
        END:STANDARD
        END:VTIMEZONE
        BEGIN:VEVENT
        UID:WakeUpSchedule-1
        SUMMARY:大学物理A（下）
        DTSTART;TZID=Asia/Shanghai:20260917T150000
        DTEND;TZID=Asia/Shanghai:20260917T162000
        RRULE:FREQ=WEEKLY;UNTIL=20261001T160000Z;INTERVAL=1
        LOCATION:F120多 张老师
        DESCRIPTION:F120多\n张老师\n1204
        BEGIN:VALARM
        ACTION:DISPLAY
        TRIGGER;RELATED=START:-PT20M
        DESCRIPTION:大学物理A（下）@F120多\n
        END:VALARM
        END:VEVENT
        BEGIN:VEVENT
        UID:WakeUpSchedule-2
        SUMMARY:电工实习
        DTSTART;TZID=Asia/Shanghai:20261026T163500
        DTEND;TZID=Asia/Shanghai:20261026T181000
        RRULE:FREQ=WEEKLY;UNTIL=20261101T160000Z;INTERVAL=1
        LOCATION:（电工实验室）训1353
        DESCRIPTION:（电工实验室）训1353\n\n1563授课时间：周一~周四 17:00-20:20；周五：13:00-16:20，17:00
         -20:20
        END:VEVENT
        BEGIN:VEVENT
        UID:WakeUpSchedule-3
        SUMMARY:大学物理实验A（上）
        DTSTART;TZID=Asia/Shanghai:20260428T081500
        DTEND;TZID=Asia/Shanghai:20260428T093500
        LOCATION:2206 李老师
        DESCRIPTION:第1 - 2节\n2206\n李老师
        END:VEVENT
        END:VCALENDAR
    """.trimIndent()

    @Test
    fun parsesWakeUpExport() {
        val calendar = IcsParser.parse(wakeUpCalendar, shanghai)
        val physics = calendar.meetings.filter { it.course == "大学物理A（下）" }
        // 9 月 17 日起每周一次，UNTIL 为 10 月 2 日 0 点（北京时间），共 3 次。
        assertEquals(3, physics.size)
        assertEquals(at("2026-09-17T15:00"), physics[0].start)
        assertEquals(at("2026-09-17T16:20"), physics[0].end)
        assertEquals(at("2026-10-01T15:00"), physics[2].start)
        assertEquals("F120多", physics[0].location)
        assertEquals("张老师", physics[0].teacher)

        val workshop = calendar.meetings.single { it.course == "电工实习" }
        assertEquals("（电工实验室）训1353", workshop.location)
        assertNull(workshop.teacher)
        assertEquals(shanghai, calendar.zone)

        // 自定义节次的课程在备注首行多出节次信息。
        val lab = calendar.meetings.single { it.course == "大学物理实验A（上）" }
        assertEquals("2206", lab.location)
        assertEquals("李老师", lab.teacher)
    }

    @Test
    fun supportsByDayExDateAndOverrides() {
        val text = """
            BEGIN:VCALENDAR
            X-WR-CALNAME:春季学期
            BEGIN:VEVENT
            UID:math
            SUMMARY:高等数学
            DTSTART:20260302T000000Z
            DURATION:PT1H35M
            RRULE:FREQ=WEEKLY;COUNT=4;BYDAY=MO,WE
            EXDATE:20260304T000000Z
            END:VEVENT
            BEGIN:VEVENT
            UID:math
            RECURRENCE-ID:20260309T000000Z
            SUMMARY:高等数学
            DTSTART:20260309T060000Z
            DTEND:20260309T073500Z
            END:VEVENT
            BEGIN:VEVENT
            UID:holiday
            SUMMARY:假期
            DTSTART;VALUE=DATE:20260310
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val calendar = IcsParser.parse(text, shanghai)
        assertEquals("春季学期", calendar.name)
        val starts = calendar.meetings.map { it.start }
        // COUNT=4 生成 3/2、3/4、3/9、3/11，排除 3/4，3/9 改期到下午。
        assertEquals(
            listOf(at("2026-03-02T08:00"), at("2026-03-09T14:00"), at("2026-03-11T08:00")),
            starts,
        )
        assertTrue(calendar.meetings.none { it.course == "假期" })
    }

    @Test(expected = IcsFormatException::class)
    fun rejectsNonCalendarText() {
        IcsParser.parse("hello world", shanghai)
    }

    private fun recurringEvent(start: String, rule: String, extra: String = ""): String = """
        BEGIN:VEVENT
        UID:math
        SUMMARY:高等数学
        DTSTART;TZID=Asia/Shanghai:${start}T080000
        DURATION:PT1H
        RRULE:$rule
        $extra
        END:VEVENT
    """.trimIndent()

    private fun calendar(vararg events: String): String =
        "BEGIN:VCALENDAR\nVERSION:2.0\n${events.joinToString("\n")}\nEND:VCALENDAR"

    private fun dates(text: String): List<String> = IcsParser.parse(text, shanghai).meetings.map {
        java.time.Instant.ofEpochMilli(it.start).atZone(shanghai).toLocalDate().toString()
    }

    @Test
    fun monthlyOrdinalWeekdaysFollowTheCalendar() {
        assertEquals(
            listOf("2026-09-07", "2026-10-05", "2026-11-02"),
            dates(calendar(recurringEvent("20260907", "FREQ=MONTHLY;BYDAY=1MO;COUNT=3"))),
        )
    }

    @Test
    fun dailyWeekdaysSkipWeekends() {
        assertEquals(
            listOf("2026-09-25", "2026-09-28", "2026-09-29"),
            dates(calendar(recurringEvent("20260925", "FREQ=DAILY;BYDAY=MO,TU,WE,TH,FR;COUNT=3"))),
        )
    }

    @Test
    fun invalidMonthlyAndYearlyDatesAreSkippedInsteadOfClamped() {
        assertEquals(
            listOf("2026-01-31", "2026-03-31", "2026-05-31"),
            dates(calendar(recurringEvent("20260131", "FREQ=MONTHLY;COUNT=3"))),
        )
        assertEquals(
            listOf("2024-02-29", "2028-02-29", "2032-02-29"),
            dates(calendar(recurringEvent("20240229", "FREQ=YEARLY;COUNT=3"))),
        )
    }

    @Test
    fun supportsLastWeekdayOfMonth() {
        assertEquals(
            listOf("2026-09-30", "2026-10-30", "2026-11-30"),
            dates(calendar(recurringEvent("20260930", "FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-1;COUNT=3"))),
        )
    }

    @Test
    fun appliesExdatesRdatesAndCancellationWithoutDurationOrSummary() {
        val cancellation = """
            BEGIN:VEVENT
            UID:math
            RECURRENCE-ID;TZID=Asia/Shanghai:20260914T080000
            STATUS:CANCELLED
            END:VEVENT
        """.trimIndent()
        val base = recurringEvent("20260907", "FREQ=WEEKLY;COUNT=4")
            .replace("END:VEVENT", "EXDATE;TZID=Asia/Shanghai:20260921T080000\nRDATE;TZID=Asia/Shanghai:20260929T080000\nEND:VEVENT")
        assertEquals(listOf("2026-09-07", "2026-09-28", "2026-09-29"), dates(calendar(base, cancellation)))
    }

    @Test(expected = IcsFormatException::class)
    fun invalidRuleRejectsImportInsteadOfSilentlyDroppingOneCourse() {
        IcsParser.parse(calendar(
            recurringEvent("20260907", "FREQ=WEEKLY;COUNT=3"),
            recurringEvent("20260907", "FREQ=UNKNOWN;COUNT=3").replace("UID:math", "UID:physics"),
        ), shanghai)
    }

    @Test(expected = IcsFormatException::class)
    fun unsupportedRangeOverrideIsNotTreatedAsASingleChange() {
        val override = """
            BEGIN:VEVENT
            UID:math
            RECURRENCE-ID;TZID=Asia/Shanghai;RANGE=THISANDFUTURE:20260914T080000
            SUMMARY:高等数学
            DTSTART;TZID=Asia/Shanghai:20260914T100000
            DURATION:PT1H
            END:VEVENT
        """.trimIndent()
        IcsParser.parse(calendar(recurringEvent("20260907", "FREQ=WEEKLY;COUNT=3"), override), shanghai)
    }

    @Test
    fun utcRecurrencesKeepUtcTimeAcrossDaylightSavingChanges() {
        val event = recurringEvent("20261025", "FREQ=WEEKLY;COUNT=2")
            .replace("DTSTART;TZID=Asia/Shanghai:20261025T080000", "DTSTART:20261025T080000Z")
        val parsed = IcsParser.parse(calendar(event), ZoneId.of("America/New_York"))
        assertEquals(7 * 24 * 60 * 60 * 1_000L, parsed.meetings[1].start - parsed.meetings[0].start)
    }
}
