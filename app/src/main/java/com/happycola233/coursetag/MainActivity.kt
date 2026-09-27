package com.happycola233.coursetag

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import com.happycola233.coursetag.ui.AppViewModel
import com.happycola233.coursetag.ui.CourseTagApp

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleScheduleIntent(intent)
        setContent { CourseTagApp(viewModel) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleScheduleIntent(intent)
    }

    /** 从其他应用打开或分享的 .ics 文件直接进入课表导入确认。 */
    private fun handleScheduleIntent(intent: Intent) {
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        } ?: return
        viewModel.readSchedule(uri)
    }
}
