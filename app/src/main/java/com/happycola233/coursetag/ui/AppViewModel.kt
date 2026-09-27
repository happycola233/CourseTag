package com.happycola233.coursetag.ui

import android.app.Application
import android.content.IntentSender
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.happycola233.coursetag.CourseTagApplication
import com.happycola233.coursetag.data.AppData
import com.happycola233.coursetag.data.RenameBatch
import com.happycola233.coursetag.data.RenameRecord
import com.happycola233.coursetag.data.ThemeMode
import com.happycola233.coursetag.data.ics.IcsFormatException
import com.happycola233.coursetag.data.ics.IcsParser
import com.happycola233.coursetag.data.media.Photo
import com.happycola233.coursetag.data.media.RenameOperation
import com.happycola233.coursetag.data.media.RenameOutcome
import com.happycola233.coursetag.data.naming.TagFormat
import com.happycola233.coursetag.domain.CourseRename
import com.happycola233.coursetag.domain.CourseSummary
import com.happycola233.coursetag.domain.ImportDraft
import com.happycola233.coursetag.domain.Library
import com.happycola233.coursetag.domain.RenameChange
import com.happycola233.coursetag.domain.RenameItem
import com.happycola233.coursetag.domain.RenamePlan
import com.happycola233.coursetag.domain.RenameRequest
import com.happycola233.coursetag.domain.buildImportDraft
import com.happycola233.coursetag.domain.courseSummaries
import com.happycola233.coursetag.domain.planRename
import com.happycola233.coursetag.domain.withCourseDeleted
import com.happycola233.coursetag.domain.withCourseRenamed
import com.happycola233.coursetag.domain.withCoursesAdded
import com.happycola233.coursetag.domain.withCoursesUsed
import com.happycola233.coursetag.domain.withImportedSchedule
import com.happycola233.coursetag.domain.withScheduleRemoved
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MediaAccess { Unknown, Denied, Partial, Full }

data class ApplyProgress(val done: Int, val total: Int)

data class FailedRename(val name: String, val reason: String)

sealed interface UiEvent {
    data class Message(val text: String, val undoBatchId: String? = null) : UiEvent
    data class RequestWrite(val intentSender: IntentSender) : UiEvent
    /** 重命名完成，预览页应当关闭。 */
    data object Applied : UiEvent
}

/** 课上照片确认页的调整：按节次修改课程、逐张排除照片。 */
data class SmartTagDraft(
    val courseOverrides: Map<String, String> = emptyMap(),
    val excluded: Set<Long> = emptySet(),
)

@OptIn(FlowPreview::class)
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CourseTagApplication
    private val store = app.store
    private val media = app.media

    val data: StateFlow<AppData> = store.data
    val history: StateFlow<List<RenameBatch>> = store.history

    private val mutableAccess = MutableStateFlow(MediaAccess.Unknown)
    val access: StateFlow<MediaAccess> = mutableAccess.asStateFlow()

    private val photos = MutableStateFlow<List<Photo>?>(null)
    private val reloadRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** 照片尚未读取完成时为 null。 */
    val library: StateFlow<Library?> = combine(photos, store.data, store.ready) { photos, data, ready ->
        if (photos == null || !ready) null else Library.build(photos, data)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val courses: StateFlow<List<CourseSummary>> = combine(store.data, library) { data, library ->
        courseSummaries(data, library ?: Library.Empty)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // region 照片

    private val mutableSelection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = mutableSelection.asStateFlow()

    /** 大图浏览时可左右切换的照片，按进入时所在列表的顺序排列。 */
    var viewerPhotoIds: List<Long> = emptyList()

    init {
        viewModelScope.launch {
            merge(reloadRequests, media.changes()).debounce(250).collectLatest {
                if (mutableAccess.value == MediaAccess.Full || mutableAccess.value == MediaAccess.Partial) {
                    photos.value = runCatching { media.loadPhotos() }.getOrDefault(photos.value.orEmpty())
                }
            }
        }
    }

    /** 每次回到前台都会检查权限；部分授权时用户可能调整了可见照片，因此同样重新读取。 */
    fun onAccessChecked(access: MediaAccess) {
        mutableAccess.value = access
        if (access == MediaAccess.Denied) {
            photos.value = emptyList()
            mutableSelection.value = emptySet()
        } else {
            reloadRequests.tryEmit(Unit)
        }
    }

    fun setSelection(ids: Set<Long>) {
        mutableSelection.value = ids
    }

    fun toggleSelection(id: Long) = mutableSelection.update { if (id in it) it - id else it + id }

    fun clearSelection() = setSelection(emptySet())

    // endregion

    // region 重命名预览与执行

    private val request = MutableStateFlow<RenameRequest?>(null)
    private val mutableExcluded = MutableStateFlow<Set<Long>>(emptySet())
    val excluded: StateFlow<Set<Long>> = mutableExcluded.asStateFlow()

    val plan: StateFlow<RenamePlan?> = combine(request, library) { request, library ->
        if (request == null || library == null) null else planRename(request, library)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val mutableProgress = MutableStateFlow<ApplyProgress?>(null)
    val progress: StateFlow<ApplyProgress?> = mutableProgress.asStateFlow()

    private val mutableFailures = MutableStateFlow<List<FailedRename>>(emptyList())
    val failures: StateFlow<List<FailedRename>> = mutableFailures.asStateFlow()

    private val events = Channel<UiEvent>(Channel.BUFFERED)
    val uiEvents: Flow<UiEvent> = events.receiveAsFlow()

    private var pendingApply: PendingApply? = null

    private class PendingApply(val request: RenameRequest, val items: List<RenameItem>, val closesPreview: Boolean)

    /** 设置待确认的重命名，界面随后进入预览页。 */
    fun preview(request: RenameRequest) {
        this.request.value = request
        mutableExcluded.value = emptySet()
    }

    fun setExcluded(id: Long, excluded: Boolean) =
        mutableExcluded.update { if (excluded) it + id else it - id }

    fun confirmPlan() {
        val plan = plan.value ?: return
        val items = plan.items.filter { it.issue == null && it.photoId !in excluded.value }
        startApply(plan.request, items, closesPreview = true)
    }

    fun undo(batchId: String) {
        val batch = history.value.firstOrNull { it.id == batchId } ?: return
        val library = library.value ?: return
        val plan = planRename(RenameRequest.Revert("撤销「${batch.title}」", batch), library)
        val items = plan.items.filter { it.issue == null }
        if (items.isEmpty()) {
            message("这些照片已被移动、删除或再次改名，无法撤销")
            return
        }
        startApply(plan.request, items, closesPreview = false)
    }

    private fun startApply(request: RenameRequest, items: List<RenameItem>, closesPreview: Boolean) {
        if (items.isEmpty() || mutableProgress.value != null) return
        pendingApply = PendingApply(request, items, closesPreview)
        val intent = media.createWriteRequest(items.map { it.entry.photo.uri })
        events.trySend(UiEvent.RequestWrite(intent.intentSender))
    }

    fun onWriteResult(granted: Boolean) {
        val pending = pendingApply ?: return
        pendingApply = null
        if (!granted) {
            message("未获得修改权限，照片保持原样")
            return
        }
        viewModelScope.launch { runApply(pending) }
    }

    private suspend fun runApply(pending: PendingApply) {
        val items = pending.items
        mutableProgress.value = ApplyProgress(0, items.size)
        val outcomes = media.rename(items.map { RenameOperation(it.photoId, it.entry.photo.uri, it.newName) }) { done ->
            mutableProgress.value = ApplyProgress(done, items.size)
        }
        val itemsById = items.associateBy { it.photoId }
        val renamed = outcomes.filterIsInstance<RenameOutcome.Renamed>()
        val failed = outcomes.filterIsInstance<RenameOutcome.Failed>()
        val now = System.currentTimeMillis()

        val batchId = UUID.randomUUID().toString()
        if (renamed.isNotEmpty()) {
            val batch = RenameBatch(
                id = batchId,
                title = pending.request.title,
                createdAt = now,
                records = renamed.map { RenameRecord(it.photoId, itemsById.getValue(it.photoId).entry.photo.name, it.actualName) },
            )
            val revertedId = (pending.request as? RenameRequest.Revert)?.batch?.id
            store.updateHistory { batches ->
                (listOf(batch) + batches.map { if (it.id == revertedId) it.copy(undone = true) else it }).take(MAX_HISTORY)
            }
            val usedCourses = renamed.mapNotNull { itemsById.getValue(it.photoId).newCourse }.toSet()
            val courseRename = (pending.request as? RenameRequest.Assign)?.courseRename
            store.update { data ->
                val renamedData = courseRename?.let { data.withCourseRenamed(it.from, it.to, now) } ?: data
                renamedData.withCoursesUsed(usedCourses, now)
            }
        }
        photos.value = runCatching { media.loadPhotos() }.getOrDefault(photos.value.orEmpty())
        mutableProgress.value = null
        mutableSelection.value = emptySet()
        mutableFailures.value = failed.map { FailedRename(itemsById.getValue(it.photoId).entry.photo.name, it.reason) }
        if (pending.closesPreview) events.send(UiEvent.Applied)
        if (renamed.isNotEmpty()) {
            val undoable = pending.request !is RenameRequest.Revert
            events.send(UiEvent.Message(successMessage(pending.request, items, renamed.size), batchId.takeIf { undoable }))
        } else if (failed.isNotEmpty()) {
            message("照片未能重命名")
        }
    }

    private fun successMessage(request: RenameRequest, items: List<RenameItem>, count: Int): String = when {
        request is RenameRequest.Revert -> "已撤销，$count 张照片恢复原名"
        items.all { it.change == RenameChange.Remove } -> "已移除 $count 张照片的课程"
        items.all { it.change == RenameChange.Reformat } -> "已将 $count 张照片更新为新格式"
        request is RenameRequest.Assign && request.courseRename != null -> "已将 $count 张照片改为「${request.courseRename.to}」"
        else -> "已为 $count 张照片标记课程"
    }

    fun dismissFailures() {
        mutableFailures.value = emptyList()
    }

    // endregion

    // region 课上照片确认

    private val mutableSmartDraft = MutableStateFlow(SmartTagDraft())
    val smartDraft: StateFlow<SmartTagDraft> = mutableSmartDraft.asStateFlow()

    fun resetSmartDraft() {
        mutableSmartDraft.value = SmartTagDraft()
    }

    fun overrideSessionCourse(sessionKey: String, course: String) =
        mutableSmartDraft.update { it.copy(courseOverrides = it.courseOverrides + (sessionKey to course)) }

    fun setSmartExcluded(ids: Collection<Long>, excluded: Boolean) =
        mutableSmartDraft.update { it.copy(excluded = if (excluded) it.excluded + ids else it.excluded - ids.toSet()) }

    // endregion

    // region 课程

    fun addCourses(names: List<String>) {
        store.update { it.withCoursesAdded(names, System.currentTimeMillis()) }
    }

    /** 修改课程名称；有照片使用该课程时进入预览，返回 true。 */
    fun renameCourse(from: String, to: String): Boolean {
        val library = library.value
        val tagged = library?.entries?.filter { it.course == from }.orEmpty()
        if (tagged.isEmpty()) {
            store.update { it.withCourseRenamed(from, to, System.currentTimeMillis()) }
            return false
        }
        preview(
            RenameRequest.Assign(
                title = "将「$from」改为「$to」",
                assignments = tagged.associate { it.id to to },
                courseRename = CourseRename(from, to),
            ),
        )
        return true
    }

    fun deleteCourse(name: String) = store.update { it.withCourseDeleted(name) }

    // endregion

    // region 课表

    private val mutableImportDraft = MutableStateFlow<ImportDraft?>(null)
    val importDraft: StateFlow<ImportDraft?> = mutableImportDraft.asStateFlow()

    fun readSchedule(uri: Uri) {
        viewModelScope.launch {
            val resolver = getApplication<Application>().contentResolver
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                    val text = resolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                    val calendar = IcsParser.parse(text)
                    val suggestedName = calendar.name
                        ?: displayName?.substringBeforeLast('.')?.trim()?.ifEmpty { null }
                        ?: "我的课表"
                    buildImportDraft(suggestedName, calendar, store.data.value)
                }
            }
            result.onSuccess { mutableImportDraft.value = it }.onFailure { error ->
                message((error as? IcsFormatException)?.message ?: "无法读取这个文件，请选择 .ics 格式的课程表")
            }
        }
    }

    fun confirmImport(name: String, included: Set<String>) {
        val draft = mutableImportDraft.value ?: return
        mutableImportDraft.value = null
        store.update { it.withImportedSchedule(draft, name, included, System.currentTimeMillis()) }
        message("已导入「$name」，共 ${included.size} 门课程")
    }

    fun dismissImport() {
        mutableImportDraft.value = null
    }

    fun renameSchedule(id: String, name: String) = store.update { data ->
        data.copy(schedules = data.schedules.map { if (it.id == id) it.copy(name = name) else it })
    }

    fun removeSchedule(id: String, removeUnusedCourses: Boolean) {
        val counts = library.value?.courseCounts.orEmpty()
        store.update { it.withScheduleRemoved(id, removeUnusedCourses, counts) }
    }

    // endregion

    // region 设置

    fun setTagFormat(format: TagFormat) = store.update { data ->
        val settings = data.settings
        if (settings.tagFormat == format) return@update data
        val previous = (listOf(settings.tagFormat) + settings.previousFormats).filter { it != format }.distinct()
        data.copy(settings = settings.copy(tagFormat = format, previousFormats = previous.take(MAX_PREVIOUS_FORMATS)))
    }

    fun setClassWindow(minutesBefore: Int, minutesAfter: Int) = store.update { data ->
        data.copy(settings = data.settings.copy(minutesBeforeClass = minutesBefore, minutesAfterClass = minutesAfter))
    }

    fun setThemeMode(mode: ThemeMode) = store.update { it.copy(settings = it.settings.copy(themeMode = mode)) }

    fun clearHistory() = store.updateHistory { emptyList() }

    // endregion

    fun message(text: String) {
        events.trySend(UiEvent.Message(text))
    }

    private companion object {
        const val MAX_HISTORY = 50
        const val MAX_PREVIOUS_FORMATS = 5
    }
}
