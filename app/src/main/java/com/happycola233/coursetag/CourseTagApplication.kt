package com.happycola233.coursetag

import android.app.Application
import com.happycola233.coursetag.data.AppStore
import com.happycola233.coursetag.data.media.MediaRepository
import kotlinx.coroutines.MainScope

class CourseTagApplication : Application() {
    /** 进程级单例：数据存储需要在界面销毁后继续完成写盘。 */
    val store: AppStore by lazy { AppStore(filesDir, MainScope()) }
    val media: MediaRepository by lazy { MediaRepository(this) }
}
