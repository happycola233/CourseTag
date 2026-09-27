package com.happycola233.coursetag.ui

import com.happycola233.coursetag.data.schedule.ClassSession
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

fun weekdayName(day: DayOfWeek): String = "周" + "一二三四五六日"[day.value - 1]

fun formatTime(time: LocalTime): String = time.format(timeFormat)

fun formatTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(epochMillis).atZone(zone).format(timeFormat)

fun formatTimeRange(start: Long, end: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    "${formatTime(start, zone)}–${formatTime(end, zone)}"

/** 省略今年的年份，并在最近两天使用「今天」「昨天」。 */
fun formatDay(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    val weekday = weekdayName(date.dayOfWeek)
    return when {
        date == today -> "今天 · $weekday"
        date == today.minusDays(1) -> "昨天 · $weekday"
        date.year == today.year -> "${date.monthValue}月${date.dayOfMonth}日 $weekday"
        else -> "${date.year}年${date.monthValue}月${date.dayOfMonth}日 $weekday"
    }
}

fun formatDay(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    formatDay(Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate())

fun formatDateTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    "${formatDay(epochMillis, zone)} ${formatTime(epochMillis, zone)}"

fun formatDate(date: LocalDate): String = "${date.year}/${date.monthValue}/${date.dayOfMonth}"

/** 例如「第 3 周 周四 10:15–11:35」。 */
fun formatSessionTime(session: ClassSession): String {
    val start = Instant.ofEpochMilli(session.start).atZone(session.zone)
    return "第 ${session.week} 周 ${weekdayName(start.dayOfWeek)} ${formatTimeRange(session.start, session.end, session.zone)}"
}

/** 带日期的节次描述，例如「9月24日 第 2 周 周四 10:15–11:35」。 */
fun formatSessionDate(session: ClassSession): String {
    val date = Instant.ofEpochMilli(session.start).atZone(session.zone).toLocalDate()
    val today = LocalDate.now(session.zone)
    val day = when {
        date == today -> "今天"
        date == today.minusDays(1) -> "昨天"
        date.year == today.year -> "${date.monthValue}月${date.dayOfMonth}日"
        else -> "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
    }
    return "$day · ${formatSessionTime(session)}"
}

/** 把教学周合并为区间，例如 [1,2,3,5,7,8] →「第 1–3、5、7–8 周」。 */
fun formatWeeks(weeks: List<Int>): String {
    if (weeks.isEmpty()) return ""
    val ranges = mutableListOf<String>()
    var start = weeks.first()
    var previous = start
    for (week in weeks.drop(1) + Int.MIN_VALUE) {
        if (week == previous + 1) {
            previous = week
            continue
        }
        ranges += if (start == previous) "$start" else "$start–$previous"
        start = week
        previous = week
    }
    return "第 ${ranges.joinToString("、")} 周"
}

fun formatCount(count: Int): String = "%,d".format(count)
