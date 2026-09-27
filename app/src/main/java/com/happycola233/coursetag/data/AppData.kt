package com.happycola233.coursetag.data

import com.happycola233.coursetag.data.naming.TagFormat
import kotlinx.serialization.Serializable

/** 随系统备份迁移的全部用户数据：课程、课表与偏好设置。 */
@Serializable
data class AppData(
    val courses: List<Course> = emptyList(),
    val schedules: List<Schedule> = emptyList(),
    val settings: AppSettings = AppSettings(),
)

@Serializable
data class Course(
    val id: String,
    /** 写入文件名的课程名称。 */
    val name: String,
    /**
     * 关联的课表课程名。导入课表时自动关联；用户修改课程名称后仍按原课表名称匹配上课时段，
     * 合并同名课程时会汇总多个课表名称。
     */
    val scheduleNames: List<String> = emptyList(),
    val createdAt: Long,
    val lastUsedAt: Long = 0,
)

@Serializable
data class Schedule(
    val id: String,
    val name: String,
    val importedAt: Long,
    /** 课表所在时区，用于换算上课时间与教学周。 */
    val zoneId: String,
    /** 第 1 教学周周一的 epochDay。 */
    val firstWeekEpochDay: Long,
    val meetings: List<ClassMeeting>,
)

/** 展开重复规则后的一次具体上课，时间均为 epoch 毫秒。 */
@Serializable
data class ClassMeeting(
    /** 课表中的原始课程名。 */
    val course: String,
    val start: Long,
    val end: Long,
    val location: String? = null,
    val teacher: String? = null,
)

@Serializable
enum class ThemeMode { System, Light, Dark }

@Serializable
data class AppSettings(
    val tagFormat: TagFormat = TagFormat.Default,
    /** 曾经使用过的命名格式（新到旧），用于继续识别旧格式照片并提供一键转换。 */
    val previousFormats: List<TagFormat> = emptyList(),
    val minutesBeforeClass: Int = 10,
    val minutesAfterClass: Int = 10,
    val themeMode: ThemeMode = ThemeMode.System,
)

/** 一次批量重命名的记录，用于撤销。媒体 ID 绑定本机媒体库，因此记录单独存放、不参与备份。 */
@Serializable
data class RenameBatch(
    val id: String,
    val title: String,
    val createdAt: Long,
    val records: List<RenameRecord>,
    val undone: Boolean = false,
    /** 逐张记录已恢复的照片；保留原始记录，便于展示部分撤销并重试其余照片。 */
    val revertedMediaIds: Set<Long> = emptySet(),
) {
    val pendingRecords: List<RenameRecord>
        get() = if (undone) emptyList() else records.filter { it.mediaId !in revertedMediaIds }

    fun withRevertedPhotos(mediaIds: Set<Long>): RenameBatch {
        val reverted = revertedMediaIds + mediaIds
        return copy(revertedMediaIds = reverted, undone = records.all { it.mediaId in reverted })
    }
}

@Serializable
data class RenameRecord(
    val mediaId: Long,
    val from: String,
    val to: String,
)
