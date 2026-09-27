package com.happycola233.coursetag.data.naming

/** 照片文件名的解析结果：[stem] 为去掉课程后缀与扩展名的原始文件名。 */
data class ParsedName(
    val stem: String,
    val extension: String,
    val course: String?,
    /** 识别出课程时所用的格式；为 null 表示未标记。 */
    val format: TagFormat?,
)

/**
 * 按「当前格式优先、再依次尝试旧格式」的顺序识别文件名中的课程。
 * 旧格式仅用于识别与转换，新的重命名始终使用当前格式。
 */
class PhotoNameParser(
    private val current: TagFormat,
    previous: List<TagFormat>,
    private val isKnownCourse: (String) -> Boolean,
) {
    private val formats = listOf(current) + previous.filter { it != current }

    fun parse(displayName: String): ParsedName {
        val parts = FileNames.split(displayName)
        for (format in formats) {
            val match = format.extract(parts.stem, isKnownCourse) ?: continue
            return ParsedName(match.stem, parts.extension, match.course, format)
        }
        return ParsedName(parts.stem, parts.extension, null, null)
    }

    /** 生成使用当前格式的新文件名；[course] 为 null 表示移除课程后缀。 */
    fun compose(parsed: ParsedName, course: String?): String =
        (if (course == null) parsed.stem else current.compose(parsed.stem, course)) + parsed.extension
}
