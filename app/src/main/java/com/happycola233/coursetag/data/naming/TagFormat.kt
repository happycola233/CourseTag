package com.happycola233.coursetag.data.naming

import kotlinx.serialization.Serializable

/**
 * 课程后缀格式：在原文件名末尾追加 `opening + 课程名 + closing`。
 *
 * [opening] 必须非空，否则无法从文件名中定位课程；[closing] 可为空（如下划线格式）。
 */
@Serializable
data class TagFormat(val opening: String, val closing: String) {

    fun compose(stem: String, course: String): String = stem + opening + course + closing

    /**
     * 从不含扩展名的文件名中拆出原始文件名与课程名，不符合本格式时返回 null。
     *
     * 括号类格式按嵌套配对查找，课程名自身带括号（如「大学物理A（下）」）也能完整识别。
     * 没有结尾符号或使用 ASCII 符号的格式容易与系统自动生成的文件名混淆（如 `IMG (1)`、
     * `Screenshot_xxx_com.tencent.mm`），这类格式只接受已知课程或含汉字的名称。
     */
    fun extract(stem: String, isKnownCourse: (String) -> Boolean): TagMatch? {
        val start: Int
        val courseEnd: Int
        if (closing.isEmpty()) {
            start = stem.lastIndexOf(opening)
            courseEnd = stem.length
        } else {
            if (!stem.endsWith(closing)) return null
            courseEnd = stem.length - closing.length
            start = if (opening == closing) {
                stem.lastIndexOf(opening, courseEnd - opening.length)
            } else {
                findBalancedOpening(stem, courseEnd)
            }
        }
        if (start <= 0) return null
        val course = stem.substring(start + opening.length, courseEnd)
        if (course.isBlank() || course != course.trim() || course.all { it.isDigit() }) return null
        val distinctive = closing.isNotEmpty() && opening.any { it.code > 0x7F }
        if (!distinctive && !isKnownCourse(course) && course.none(::isHan)) return null
        return TagMatch(stem = stem.substring(0, start), course = course)
    }

    /** 自 [courseEnd] 向前查找与末尾结束符配对的开始符位置。 */
    private fun findBalancedOpening(stem: String, courseEnd: Int): Int {
        var depth = 1
        var index = courseEnd
        while (index > 0) {
            when {
                index >= closing.length && stem.startsWith(closing, index - closing.length) -> {
                    depth++
                    index -= closing.length
                }
                index >= opening.length && stem.startsWith(opening, index - opening.length) -> {
                    depth--
                    index -= opening.length
                    if (depth == 0) return index
                }
                else -> index--
            }
        }
        return -1
    }

    val isValid: Boolean
        get() = opening.isNotEmpty() && FileNames.isValidName(opening + closing)

    companion object {
        val Default = TagFormat("（", "）")
    }
}

data class TagMatch(val stem: String, val course: String)

/** 内置的常用格式；列表之外的组合视为自定义格式。 */
enum class TagFormatPreset(val label: String, val format: TagFormat) {
    FullWidthParentheses("全角括号", TagFormat("（", "）")),
    Parentheses("半角括号", TagFormat("(", ")")),
    SquareBrackets("方括号", TagFormat("[", "]")),
    LenticularBrackets("方头括号", TagFormat("【", "】")),
    Underscore("下划线", TagFormat("_", "")),
    Hyphen("短横线", TagFormat(" - ", "")),
    Hashtag("话题标签", TagFormat(" #", "")),
    ;

    companion object {
        fun of(format: TagFormat): TagFormatPreset? = entries.firstOrNull { it.format == format }
    }
}

private fun isHan(char: Char): Boolean =
    Character.UnicodeScript.of(char.code) == Character.UnicodeScript.HAN
