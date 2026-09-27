package com.happycola233.coursetag.data

import androidx.core.util.AtomicFile
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 以 JSON 文件保存用户数据。读取在后台完成，写入前会等待读取结束，
 * 避免初始空状态覆盖已有数据；每次修改后按最新状态原子写入。
 */
class AppStore(directory: File, private val scope: CoroutineScope) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val dataFile = PersistedValue(File(directory, "app_data.json"), AppData.serializer(), AppData())
    private val historyFile = PersistedValue(
        File(directory, "rename_history.json"),
        ListSerializer(RenameBatch.serializer()),
        emptyList(),
    )

    val data: StateFlow<AppData> = dataFile.state
    val history: StateFlow<List<RenameBatch>> = historyFile.state

    /** 读取完成后变为 true；此前界面应视为正在加载。 */
    val ready: StateFlow<Boolean> get() = dataFile.ready

    init {
        dataFile.load()
        historyFile.load()
    }

    fun update(transform: (AppData) -> AppData) = dataFile.update(transform)

    fun updateHistory(transform: (List<RenameBatch>) -> List<RenameBatch>) = historyFile.update(transform)

    private inner class PersistedValue<T>(file: File, private val serializer: KSerializer<T>, initial: T) {
        private val atomicFile = AtomicFile(file)
        private val mutableState = MutableStateFlow(initial)
        private val loaded = CompletableDeferred<Unit>()
        private val writeLock = Mutex()
        private val mutableReady = MutableStateFlow(false)

        val state: StateFlow<T> = mutableState.asStateFlow()
        val ready: StateFlow<Boolean> = mutableReady.asStateFlow()

        fun load() {
            scope.launch(Dispatchers.IO) {
                // 文件损坏时回退到默认值，不阻止应用启动。
                runCatching { json.decodeFromString(serializer, atomicFile.readFully().decodeToString()) }
                    .onSuccess { mutableState.value = it }
                loaded.complete(Unit)
                mutableReady.value = true
            }
        }

        /** 读取完成后立即生效（便于调用方紧接着读取新状态），写盘在后台进行。 */
        fun update(transform: (T) -> T) {
            if (loaded.isCompleted) {
                mutableState.update(transform)
                scope.launch { persist() }
            } else {
                scope.launch {
                    loaded.await()
                    mutableState.update(transform)
                    persist()
                }
            }
        }

        private suspend fun persist() = writeLock.withLock {
            val content = json.encodeToString(serializer, mutableState.value)
            withContext(Dispatchers.IO) {
                val stream = atomicFile.startWrite()
                runCatching {
                    stream.write(content.encodeToByteArray())
                    atomicFile.finishWrite(stream)
                }.onFailure { atomicFile.failWrite(stream) }
            }
        }
    }
}
