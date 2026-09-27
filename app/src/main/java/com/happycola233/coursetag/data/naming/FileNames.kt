package com.happycola233.coursetag.data.naming

object FileNames {
    /** Android 外部存储（FUSE）与常见文件系统单个文件名的字节上限。 */
    const val MAX_NAME_BYTES = 255

    /** 外部存储按 FAT 规则拒绝的字符，映射为外观相同的全角字符，保留课程名原意。 */
    private val fullWidthReplacements = mapOf(
        '/' to '／',
        '\\' to '＼',
        ':' to '：',
        '*' to '＊',
        '?' to '？',
        '"' to '＂',
        '<' to '＜',
        '>' to '＞',
        '|' to '｜',
    )

    fun split(name: String): NameParts {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) NameParts(name.substring(0, dot), name.substring(dot)) else NameParts(name, "")
    }

    /** 将任意来源的课程名整理为可安全写入文件名的形式。 */
    fun sanitizeCourseName(raw: String): String =
        raw.map { fullWidthReplacements[it] ?: if (it.isISOControl()) ' ' else it }
            .joinToString("")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.')
            .trim()

    fun isValidName(name: String): Boolean =
        name.none { it in fullWidthReplacements || it.isISOControl() }

    fun byteLength(name: String): Int = name.toByteArray(Charsets.UTF_8).size
}

data class NameParts(val stem: String, val extension: String)
