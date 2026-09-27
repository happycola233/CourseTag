package com.happycola233.coursetag.ui.rename

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.happycola233.coursetag.ui.AppViewModel
import com.happycola233.coursetag.ui.theme.AppSurfaces

/** 批量重命名进行中时不可关闭，避免用户误以为已经完成。 */
@Composable
fun ApplyProgressDialog(viewModel: AppViewModel) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val current = progress ?: return
    BasicAlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = AppSurfaces.modal) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("正在重命名", style = MaterialTheme.typography.headlineSmall)
                LinearWavyProgressIndicator(
                    progress = { if (current.total == 0) 0f else current.done.toFloat() / current.total },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${current.done} / ${current.total}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun RenameFailuresDialog(viewModel: AppViewModel) {
    val failures by viewModel.failures.collectAsStateWithLifecycle()
    if (failures.isEmpty()) return
    AlertDialog(
        onDismissRequest = viewModel::dismissFailures,
        confirmButton = { TextButton(onClick = viewModel::dismissFailures) { Text("知道了") } },
        title = { Text("${failures.size} 张照片未能重命名") },
        text = {
            LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(failures) { failure ->
                    Column {
                        Text(failure.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            failure.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
    )
}
