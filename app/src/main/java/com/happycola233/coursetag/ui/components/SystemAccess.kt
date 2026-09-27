package com.happycola233.coursetag.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.happycola233.coursetag.ui.AppViewModel
import com.happycola233.coursetag.ui.MediaAccess

private fun Context.granted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun Context.mediaAccess(): MediaAccess = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && granted(Manifest.permission.READ_MEDIA_IMAGES) ->
        MediaAccess.Full
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && granted(Manifest.permission.READ_EXTERNAL_STORAGE) ->
        MediaAccess.Full
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> MediaAccess.Partial
    else -> MediaAccess.Denied
}

private val mediaPermissions: Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

/** 申请读取照片；部分授权时再次调用会让系统重新弹出照片选择。 */
@Composable
fun rememberMediaPermissionRequest(viewModel: AppViewModel): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.onAccessChecked(context.mediaAccess())
    }
    return { launcher.launch(mediaPermissions) }
}

/** 选择 .ics 文件导入课程表。部分应用把 .ics 标记为通用二进制类型，因此一并列出。 */
@Composable
fun rememberScheduleFilePicker(viewModel: AppViewModel): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::readSchedule)
    }
    return {
        launcher.launch(
            arrayOf("text/calendar", "text/x-vcalendar", "application/ics", "application/octet-stream", "text/plain"),
        )
    }
}

fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/** Android 12 起可在系统「媒体管理」中允许本应用直接修改照片，重命名前不再逐次确认。 */
val supportsMediaManagement: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

fun Context.canManageMedia(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MediaStore.canManageMedia(this)

/** 系统免确认写入还会检查照片读取权限及原始媒体位置信息权限。 */
fun Context.canRenameWithoutConfirmation(): Boolean =
    canManageMedia() && mediaAccess() == MediaAccess.Full && granted(Manifest.permission.ACCESS_MEDIA_LOCATION)

/** 仅在用户主动开启免确认时申请附加权限；拒绝后仍保留普通的系统确认流程。 */
@Composable
fun rememberMediaManagementRequest(viewModel: AppViewModel, onAccessChanged: () -> Unit): () -> Unit {
    val context = LocalContext.current
    fun finishRequest() {
        viewModel.onAccessChecked(context.mediaAccess())
        onAccessChanged()
        if (context.mediaAccess() == MediaAccess.Full && context.granted(Manifest.permission.ACCESS_MEDIA_LOCATION)) {
            if (!context.canManageMedia()) context.openMediaManagementSettings()
        } else {
            viewModel.message("尚未开启免确认，可在应用权限设置中允许全部照片和照片位置信息")
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        finishRequest()
    }
    return {
        if (context.mediaAccess() != MediaAccess.Full || !context.granted(Manifest.permission.ACCESS_MEDIA_LOCATION)) {
            launcher.launch(mediaPermissions + Manifest.permission.ACCESS_MEDIA_LOCATION)
        } else {
            context.openMediaManagementSettings()
        }
    }
}

fun Context.openMediaManagementSettings() {
    if (!supportsMediaManagement) return
    startActivity(
        Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, "package:$packageName".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
