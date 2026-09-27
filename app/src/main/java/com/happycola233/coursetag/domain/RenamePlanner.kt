package com.happycola233.coursetag.domain

import com.happycola233.coursetag.data.RenameBatch
import com.happycola233.coursetag.data.naming.FileNames

sealed interface RenameRequest {
    val title: String

    /** 为照片指定课程，值为 null 表示移除课程后缀。 */
    data class Assign(
        override val title: String,
        val assignments: Map<Long, String?>,
        /** 统一修改课程名时，重命名成功后同步更新课程列表。 */
        val courseRename: CourseRename? = null,
    ) : RenameRequest

    /** 把某次批量重命名的照片恢复为原文件名。 */
    data class Revert(override val title: String, val batch: RenameBatch) : RenameRequest
}

data class CourseRename(val from: String, val to: String)

enum class RenameChange { Add, Replace, Remove, Reformat, Revert }

enum class RenameIssue(val message: String) {
    TooLong("文件名过长，系统无法保存"),
    Conflict("同一文件夹中已有同名文件"),
}

data class RenameItem(
    val entry: PhotoEntry,
    val newName: String,
    val newCourse: String?,
    val change: RenameChange,
    val issue: RenameIssue?,
) {
    val photoId: Long get() = entry.id
}

data class RenamePlan(
    val request: RenameRequest,
    /** 文件名会发生变化的照片，包含无法执行而需跳过的项。 */
    val items: List<RenameItem>,
    /** 文件名已符合目标、无需改动的照片数。 */
    val unchangedCount: Int,
    /** 撤销时已被移动、删除或再次改名的照片数。 */
    val missingCount: Int,
)

fun planRename(request: RenameRequest, library: Library): RenamePlan {
    val parser = library.parser
    val occupied = library.entries.mapTo(HashSet()) { nameKey(it.photo.folder, it.photo.volume, it.photo.name) }
    var unchanged = 0
    var missing = 0
    val items = mutableListOf<RenameItem>()

    fun addItem(entry: PhotoEntry, newName: String, newCourse: String?, change: RenameChange) {
        val photo = entry.photo
        if (newName == photo.name) {
            unchanged++
            return
        }
        val key = nameKey(photo.folder, photo.volume, newName)
        val onlyCaseChanged = newName.equals(photo.name, ignoreCase = true)
        val issue = when {
            FileNames.byteLength(newName) > FileNames.MAX_NAME_BYTES -> RenameIssue.TooLong
            !onlyCaseChanged && key in occupied -> RenameIssue.Conflict
            else -> null
        }
        if (issue == null) occupied += key
        items += RenameItem(entry, newName, newCourse, change, issue)
    }

    when (request) {
        is RenameRequest.Assign -> for ((photoId, course) in request.assignments) {
            val entry = library.byId[photoId] ?: continue
            val newName = parser.compose(entry.parsed, course)
            val change = when {
                course == null -> RenameChange.Remove
                entry.course == null -> RenameChange.Add
                entry.course == course -> RenameChange.Reformat
                else -> RenameChange.Replace
            }
            addItem(entry, newName, course, change)
        }
        is RenameRequest.Revert -> for (record in request.batch.records) {
            val entry = library.byId[record.mediaId]
            if (entry == null || entry.photo.name != record.to) {
                missing++
                continue
            }
            addItem(entry, record.from, parser.parse(record.from).course, RenameChange.Revert)
        }
    }
    // 按拍摄时间排列，与图库浏览顺序保持一致。
    items.sortByDescending { it.entry.photo.takenAt }
    return RenamePlan(request, items, unchanged, missing)
}

/** 外部存储大多不区分文件名大小写，冲突检查统一按小写比较。 */
private fun nameKey(folder: String, volume: String, name: String): String =
    "$volume/$folder/$name".lowercase()
