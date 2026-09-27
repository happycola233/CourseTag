package com.happycola233.coursetag.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.happycola233.coursetag.ui.Navigator
import com.happycola233.coursetag.ui.components.BackButton
import com.happycola233.coursetag.ui.components.GroupGap
import com.happycola233.coursetag.ui.components.GroupedItem
import com.happycola233.coursetag.ui.components.pageTopBarColors
import com.happycola233.coursetag.ui.theme.AppSurfaces
import com.happycola233.coursetag.ui.theme.Symbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class License(val name: String, val url: String, val noticeAsset: String? = null)

private val licenses = listOf(
    License(
        "Material Symbols Rounded",
        "https://github.com/google/material-design-icons",
        noticeAsset = "licenses/material-symbols-rounded/NOTICE.txt",
    ),
    License("Jetpack Compose 与 Material 3", "https://developer.android.com/jetpack/androidx/releases/compose-material3"),
    License("Jetpack Navigation 3", "https://developer.android.com/jetpack/androidx/releases/navigation3"),
    License("AndroidX Core、Activity 与 Lifecycle", "https://developer.android.com/jetpack/androidx"),
    License("Kotlin 协程与序列化", "https://github.com/Kotlin"),
    License("Coil", "https://github.com/coil-kt/coil"),
)

@Composable
fun LicensesScreen(navigator: Navigator) {
    val uriHandler = LocalUriHandler.current
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        containerColor = AppSurfaces.page,
        topBar = {
            TopAppBar(
                title = { Text("开源许可") },
                navigationIcon = { BackButton(navigator::back) },
                colors = pageTopBarColors(),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(GroupGap),
        ) {
            itemsIndexed(licenses) { index, license ->
                GroupedItem(
                    index = index,
                    count = licenses.size,
                    headline = license.name,
                    supporting = "Apache License 2.0",
                    trailing = { Icon(Symbols.OpenInNew, contentDescription = null) },
                    onClick = {
                        if (license.noticeAsset != null) notice = license.noticeAsset else uriHandler.openUri(license.url)
                    },
                )
            }
        }
    }
    notice?.let { asset -> NoticeDialog(asset, onDismiss = { notice = null }) }
}

@Composable
private fun NoticeDialog(asset: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text by produceState("", asset) {
        value = withContext(Dispatchers.IO) {
            context.assets.open(asset).use { it.readBytes().decodeToString() } + "\n" +
                context.assets.open(asset.replaceAfterLast('/', "LICENSE.txt")).use { it.readBytes().decodeToString() }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        title = { Text("Material Symbols Rounded") },
        text = {
            Text(
                text,
                Modifier.verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall,
            )
        },
    )
}
