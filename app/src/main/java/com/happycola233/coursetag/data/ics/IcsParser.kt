package com.happycola233.coursetag.data.ics

import com.happycola233.coursetag.data.ClassMeeting
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

class IcsFormatException(message: String) : Exception(message)

data class ParsedCalendar(
    /** 日历自带的名称（X-WR-CALNAME），没有时由调用方用文件名代替。 */
    val name: String?,
    val zone: ZoneId,
    val meetings: List<ClassMeeting>,
)

/**
 * RFC 5545 日历解析，覆盖课程表类应用导出的常见写法：
 * 折行、TZID / UTC / 浮动时间、RRULE（DAILY、WEEKLY、MONTHLY、YEARLY，含 INTERVAL、UNTIL、
 * COUNT、BYDAY）、EXDATE、RDATE、RECURRENCE-ID 单次改期与 STATUS:CANCELLED。
 * 全天事件不代表具体上课时段，直接忽略；VALARM 等子组件的属性不会覆盖事件本身。
 */
object IcsParser {
    private const val MAX_OCCURRENCES_PER_EVENT = 1000
    private val unboundedHorizon: Duration = Duration.ofDays(366 * 2L)
    private val dateTimeFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val dateFormat = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun parse(text: String, defaultZone: ZoneId = ZoneId.systemDefault()): ParsedCalendar {
        val root = buildTree(unfold(text.removePrefix("﻿")))
        val calendar = root.children.firstOrNull { it.type == "VCALENDAR" }
            ?: throw IcsFormatException("不是有效的日历文件")
        val isWakeUp = calendar.value("PRODID")?.contains("WakeUp", ignoreCase = true) == true
        val calendarZone = calendar.value("X-WR-TIMEZONE")?.let(::zoneOrNull) ?: defaultZone
        val timeZones = calendar.children.filter { it.type == "VTIMEZONE" }
            .mapNotNull { component ->
                val id = component.value("TZID") ?: return@mapNotNull null
                id to (zoneOrNull(id) ?: fixedOffsetOf(component) ?: calendarZone)
            }
            .toMap()
        val resolver = ZoneResolver(timeZones, calendarZone)
        val events = calendar.children.filter { it.type == "VEVENT" }
            .mapNotNull { runCatching { readEvent(it, resolver, isWakeUp) }.getOrNull() }
        if (events.isEmpty()) throw IcsFormatException("日历中没有可用的课程时间")

        val overrides = events.filter { it.recurrenceId != null }
        val overriddenStarts = overrides.groupBy({ it.uid }, { it.recurrenceId!! })
        val meetings = buildList {
            for (event in events) {
                if (event.cancelled) continue
                val skipped = if (event.recurrenceId == null) {
                    overriddenStarts[event.uid].orEmpty().toSet()
                } else {
                    emptySet()
                }
                for (start in expand(event)) {
                    if (start.toInstant() in skipped || start.toInstant() in event.exDates) continue
                    val end = start.plus(event.duration)
                    add(
                        ClassMeeting(
                            course = event.summary,
                            start = start.toInstant().toEpochMilli(),
                            end = end.toInstant().toEpochMilli(),
                            location = event.location,
                            teacher = event.teacher,
                        ),
                    )
                }
            }
        }.distinct().sortedBy { it.start }
        if (meetings.isEmpty()) throw IcsFormatException("日历中没有可用的课程时间")
        return ParsedCalendar(
            name = calendar.value("X-WR-CALNAME")?.let(::unescape)?.trim()?.ifEmpty { null },
            zone = mostCommonZone(events) ?: calendarZone,
            meetings = meetings,
        )
    }

    // region 词法

    private class Property(val name: String, val params: Map<String, String>, val value: String)

    private class Component(val type: String) {
        val properties = mutableListOf<Property>()
        val children = mutableListOf<Component>()

        fun property(name: String): Property? = properties.firstOrNull { it.name == name }
        fun value(name: String): String? = property(name)?.value
    }

    /** 折行以空格或制表符开头，拼回上一行时去掉这一个空白字符。 */
    private fun unfold(text: String): List<String> {
        val lines = mutableListOf<StringBuilder>()
        for (raw in text.split("\r\n", "\n", "\r")) {
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && lines.isNotEmpty()) {
                lines.last().append(raw, 1, raw.length)
            } else if (raw.isNotBlank()) {
                lines += StringBuilder(raw)
            }
        }
        return lines.map { it.toString() }
    }

    private fun buildTree(lines: List<String>): Component {
        val root = Component("ROOT")
        val stack = ArrayDeque<Component>().apply { addLast(root) }
        for (line in lines) {
            val property = parseLine(line) ?: continue
            when (property.name) {
                "BEGIN" -> Component(property.value.trim().uppercase()).also {
                    stack.last().children += it
                    stack.addLast(it)
                }
                "END" -> if (stack.size > 1) stack.removeLast()
                else -> stack.last().properties += property
            }
        }
        return root
    }

    /** 拆分 `NAME;PARAM=VALUE:内容`，参数值可用双引号包裹冒号与分号。 */
    private fun parseLine(line: String): Property? {
        var inQuotes = false
        var colon = -1
        for ((index, char) in line.withIndex()) {
            if (char == '"') inQuotes = !inQuotes
            if (char == ':' && !inQuotes) {
                colon = index
                break
            }
        }
        if (colon <= 0) return null
        val head = line.substring(0, colon)
        val segments = mutableListOf<String>()
        val current = StringBuilder()
        inQuotes = false
        for (char in head) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ';' && !inQuotes -> {
                    segments += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        segments += current.toString()
        val params = segments.drop(1).mapNotNull { segment ->
            val equals = segment.indexOf('=')
            if (equals <= 0) null else segment.substring(0, equals).uppercase() to segment.substring(equals + 1)
        }.toMap()
        return Property(segments.first().trim().uppercase(), params, line.substring(colon + 1))
    }

    private fun unescape(value: String): String {
        val builder = StringBuilder()
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '\\' && index + 1 < value.length) {
                when (val next = value[index + 1]) {
                    'n', 'N' -> builder.append('\n')
                    else -> builder.append(next)
                }
                index += 2
            } else {
                builder.append(char)
                index++
            }
        }
        return builder.toString()
    }

    // endregion

    // region 事件

    private class Event(
        val uid: String,
        val summary: String,
        val location: String?,
        val teacher: String?,
        val start: ZonedDateTime,
        val duration: Duration,
        val rule: RecurrenceRule?,
        val exDates: Set<Instant>,
        val rDates: List<ZonedDateTime>,
        val recurrenceId: Instant?,
        val cancelled: Boolean,
    )

    private class ZoneResolver(private val known: Map<String, ZoneId>, val fallback: ZoneId) {
        fun resolve(tzid: String?): ZoneId =
            tzid?.let { known[it] ?: zoneOrNull(it) } ?: fallback
    }

    private fun readEvent(component: Component, zones: ZoneResolver, isWakeUp: Boolean): Event? {
        val summary = component.value("SUMMARY")?.let(::unescape)?.trim()?.ifEmpty { null } ?: return null
        val startProperty = component.property("DTSTART") ?: return null
        // 全天事件没有具体上课时段，无法用于匹配照片。
        val start = parseDateTime(startProperty, zones) ?: return null
        val end = component.property("DTEND")?.let { parseDateTime(it, zones) }
        val duration = when {
            end != null -> Duration.between(start, end)
            else -> component.value("DURATION")?.let(::parseDuration) ?: return null
        }
        if (duration.isNegative || duration.isZero) return null

        val rawLocation = component.value("LOCATION")?.let(::unescape)?.trim()?.ifEmpty { null }
        val (location, teacher) = if (isWakeUp) {
            splitWakeUpLocation(rawLocation, component.value("DESCRIPTION")?.let(::unescape))
        } else {
            rawLocation to null
        }

        return Event(
            uid = component.value("UID") ?: summary + start,
            summary = summary,
            location = location,
            teacher = teacher,
            start = start,
            duration = duration,
            rule = component.value("RRULE")?.let { parseRule(it, start.zone) },
            exDates = component.properties.filter { it.name == "EXDATE" }
                .flatMap { parseDateTimeList(it, zones) }
                .map { it.toInstant() }
                .toSet(),
            rDates = component.properties.filter { it.name == "RDATE" }.flatMap { parseDateTimeList(it, zones) },
            recurrenceId = component.property("RECURRENCE-ID")?.let { parseDateTime(it, zones) }?.toInstant(),
            cancelled = component.value("STATUS").equals("CANCELLED", ignoreCase = true),
        )
    }

    /**
     * WakeUp 课程表的 LOCATION 是「教室 老师」，备注中逐行列出教室、老师（自定义节次的课程前面还多一行节次）。
     * 取备注中作为 LOCATION 前缀的那一行作为教室，剩余部分即为老师。
     */
    private fun splitWakeUpLocation(location: String?, description: String?): Pair<String?, String?> {
        val raw = location ?: return null to null
        val room = description?.lines()?.map { it.trim() }
            ?.filter { it.isNotEmpty() && (raw == it || raw.startsWith("$it ")) }
            ?.maxByOrNull { it.length }
            ?: return raw to null
        return room to raw.removePrefix(room).trim().ifEmpty { null }
    }

    private fun parseDateTime(property: Property, zones: ZoneResolver): ZonedDateTime? =
        parseDateTimeValue(property.value.trim(), property.params, zones)

    private fun parseDateTimeList(property: Property, zones: ZoneResolver): List<ZonedDateTime> =
        property.value.split(',').mapNotNull { parseDateTimeValue(it.trim(), property.params, zones) }

    private fun parseDateTimeValue(value: String, params: Map<String, String>, zones: ZoneResolver): ZonedDateTime? {
        if (params["VALUE"].equals("DATE", ignoreCase = true) || value.length == 8) return null
        return if (value.endsWith("Z", ignoreCase = true)) {
            LocalDateTime.parse(value.dropLast(1), dateTimeFormat).atZone(ZoneOffset.UTC)
                .withZoneSameInstant(zones.fallback)
        } else {
            LocalDateTime.parse(value, dateTimeFormat).atZone(zones.resolve(params["TZID"]?.trim('"')))
        }
    }

    /** 支持 `PT1H35M`、`P1D`、`P1W` 以及带正负号的写法。 */
    private fun parseDuration(value: String): Duration? {
        val trimmed = value.trim().removePrefix("+")
        val weeks = Regex("^(-?)P(\\d+)W$").find(trimmed)
        if (weeks != null) {
            val days = weeks.groupValues[2].toLong() * 7
            return Duration.ofDays(if (weeks.groupValues[1] == "-") -days else days)
        }
        return runCatching { Duration.parse(trimmed) }.getOrNull()
    }

    // endregion

    // region 重复规则

    private class RecurrenceRule(
        val frequency: String,
        val interval: Int,
        val count: Int?,
        val until: Instant?,
        val byDay: List<DayOfWeek>,
    )

    private fun parseRule(value: String, zone: ZoneId): RecurrenceRule? {
        val parts = value.split(';').mapNotNull { part ->
            val equals = part.indexOf('=')
            if (equals <= 0) null else part.substring(0, equals).uppercase() to part.substring(equals + 1)
        }.toMap()
        val frequency = parts["FREQ"]?.uppercase() ?: return null
        val until = parts["UNTIL"]?.let { raw ->
            when {
                raw.length == 8 -> LocalDate.parse(raw, dateFormat).plusDays(1).atStartOfDay(zone).toInstant()
                    .minusMillis(1)
                raw.endsWith("Z", ignoreCase = true) ->
                    LocalDateTime.parse(raw.dropLast(1), dateTimeFormat).toInstant(ZoneOffset.UTC)
                else -> LocalDateTime.parse(raw, dateTimeFormat).atZone(zone).toInstant()
            }
        }
        val byDay = parts["BYDAY"]?.split(',')?.mapNotNull { token ->
            when (token.trim().takeLast(2).uppercase()) {
                "MO" -> DayOfWeek.MONDAY
                "TU" -> DayOfWeek.TUESDAY
                "WE" -> DayOfWeek.WEDNESDAY
                "TH" -> DayOfWeek.THURSDAY
                "FR" -> DayOfWeek.FRIDAY
                "SA" -> DayOfWeek.SATURDAY
                "SU" -> DayOfWeek.SUNDAY
                else -> null
            }
        }.orEmpty()
        return RecurrenceRule(
            frequency = frequency,
            interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
            count = parts["COUNT"]?.toIntOrNull()?.coerceAtLeast(1),
            until = until,
            byDay = byDay,
        )
    }

    private fun expand(event: Event): List<ZonedDateTime> {
        val rule = event.rule ?: return (listOf(event.start) + event.rDates).distinct()
        val limit = rule.until ?: event.start.toInstant().plus(unboundedHorizon)
        val maxCount = rule.count ?: MAX_OCCURRENCES_PER_EVENT
        val result = mutableListOf<ZonedDateTime>()
        var step = 0L
        // 每轮生成一个周期（天/周/月/年）内的候选时间，超出截止或次数后停止。
        while (result.size < maxCount && step < MAX_OCCURRENCES_PER_EVENT) {
            val candidates = periodCandidates(event.start, rule, step * rule.interval)
            if (candidates.isEmpty()) break
            var reachedLimit = false
            for (candidate in candidates) {
                if (candidate.isBefore(event.start)) continue
                if (candidate.toInstant().isAfter(limit)) {
                    reachedLimit = true
                    break
                }
                result += candidate
                if (result.size >= maxCount) break
            }
            if (reachedLimit) break
            step++
        }
        return (result + event.rDates).distinct()
    }

    private fun periodCandidates(start: ZonedDateTime, rule: RecurrenceRule, offset: Long): List<ZonedDateTime> =
        when (rule.frequency) {
            "DAILY" -> listOf(start.plusDays(offset))
            "WEEKLY" -> if (rule.byDay.isEmpty()) {
                listOf(start.plusWeeks(offset))
            } else {
                val weekStart = start.plusWeeks(offset).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                rule.byDay.distinct().sorted().map { day -> weekStart.with(TemporalAdjusters.nextOrSame(day)) }
            }
            "MONTHLY" -> listOf(start.plusMonths(offset))
            "YEARLY" -> listOf(start.plusYears(offset))
            else -> if (offset == 0L) listOf(start) else emptyList()
        }

    // endregion

    // region 时区

    private fun zoneOrNull(id: String): ZoneId? = runCatching { ZoneId.of(id.trim().trim('"')) }.getOrNull()

    /** 非 IANA 名称的时区（如 Outlook 导出）若只有固定偏移，直接采用该偏移。 */
    private fun fixedOffsetOf(component: Component): ZoneId? {
        if (component.children.any { it.type == "DAYLIGHT" }) return null
        val offset = component.children.firstOrNull { it.type == "STANDARD" }?.value("TZOFFSETTO") ?: return null
        return runCatching { ZoneOffset.of(offset.trim()) }.getOrNull()
    }

    private fun mostCommonZone(events: List<Event>): ZoneId? =
        events.groupingBy { it.start.zone }.eachCount().maxByOrNull { it.value }?.key

    // endregion
}
