package com.happycola233.coursetag.data.naming

/** 照片文件名的解析结果：[stem] 为去掉课程后缀与扩展名的原始文件名。 */
data class ParsedName(
    val stem: String,
    val extension: String,
    val course: String?,
    /** 识别出课程时所用的格式；为 null 表示未标记。 */
    val format: TagFormat?,
    /** 文件名末尾被用户忽略的后缀；此时视为未标记，[stem] 保留该后缀。 */
    val ignoredTag: String? = null,
)

/**
 * 同时比较新旧格式：完整的已知课程优先，其次是有结束符的明确后缀。
 * 旧格式仅用于识别与转换，新的重命名始终使用当前格式。
 * 被忽略的后缀不参与识别，文件名按未标记处理，再次标记时课程追加在该后缀之后。
 */
class PhotoNameParser(
    private val current: TagFormat,
    previous: List<TagFormat>,
    private val isIgnored: (String) -> Boolean = { false },
    private val isKnownCourse: (String) -> Boolean,
) {
    private val formats = listOf(current) + previous.filter { it != current }

    fun parse(displayName: String): ParsedName {
        val parts = FileNames.split(displayName)
        // 下划线等宽松格式不能抢先吞掉 IMG_时间（课程）中的时间和括号后缀。
        val (ignored, matches) = formats.mapNotNull { format ->
            format.extract(parts.stem, isKnownCourse)?.let { match -> format to match }
        }.partition { isIgnored(it.second.course) }
        val best = matches.maxWithOrNull(
            compareBy<Pair<TagFormat, TagMatch>> { isKnownCourse(it.second.course) }
                .thenBy { it.first.closing.isNotEmpty() }
                .thenBy { it.second.stem.length },
        ) ?: return ParsedName(parts.stem, parts.extension, null, null, ignored.firstOrNull()?.second?.course)
        return ParsedName(best.second.stem, parts.extension, best.second.course, best.first)
    }

    /** 生成使用当前格式的新文件名；[course] 为 null 表示移除课程后缀。 */
    fun compose(parsed: ParsedName, course: String?): String =
        (if (course == null) parsed.stem else current.compose(parsed.stem, course)) + parsed.extension
}
