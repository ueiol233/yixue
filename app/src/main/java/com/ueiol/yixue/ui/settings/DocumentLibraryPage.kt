package com.ueiol.yixue.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ueiol.yixue.YixueApp
import com.ueiol.yixue.data.attachment.AttachmentStore
import com.ueiol.yixue.data.attachment.typeLabel
import com.ueiol.yixue.data.db.AttachmentEntity
import com.ueiol.yixue.ui.common.YxConfirmDialog
import com.ueiol.yixue.ui.common.YxEmptyState
import com.ueiol.yixue.ui.common.YxLargeTitle
import com.ueiol.yixue.ui.navigation.LocalScaffoldPadding
import com.ueiol.yixue.ui.theme.YxCardShape
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 文档库管理页：查看/删除/重新解析上传的 Word / PPT / Excel / PDF 文档。
 * 删除为全局删除：所有会话不再可用（历史消息徽标保留名称）。
 */
class DocumentLibraryViewModel(app: YixueApp) : ViewModel() {
    val store = app.container.attachmentStore
    val docs = app.container.attachmentDao.observeAll()
}

@Composable
fun DocumentLibraryPage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as YixueApp
    val vm: DocumentLibraryViewModel = viewModel(factory = viewModelFactory {
        initializer { DocumentLibraryViewModel(app) }
    })
    val docs by vm.docs.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var deleting by remember { mutableStateOf<AttachmentEntity?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(modifier = modifier.fillMaxSize().padding(padding)) {
            YxLargeTitle(title = "文档库", actions = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = MaterialTheme.colorScheme.onSurface)
                }
            })
            if (docs.isEmpty()) {
                YxEmptyState(
                    icon = Icons.Outlined.Description,
                    title = "还没有上传文档",
                    hint = "在对话里点「+ → 文档」上传\nWord / PPT / Excel / PDF 都支持",
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(docs, key = { it.id }) { doc ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            shape = YxCardShape,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.size(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        doc.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                    val state = when (doc.status) {
                                        AttachmentStore.STATUS_READY -> doc.typeLabel()
                                        AttachmentStore.STATUS_PROCESSING -> "解析中…"
                                        else -> "解析失败：${doc.errorMsg ?: "未知原因"}"
                                    }
                                    Text(
                                        "$state · ${formatSize(doc.size)} · ${formatDate(doc.createdAt)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (doc.status == AttachmentStore.STATUS_FAILED || doc.status == AttachmentStore.STATUS_READY) {
                                    IconButton(onClick = {
                                        scope.launch {
                                            vm.store.parse(doc.id)
                                            snackbar.showSnackbar("已重新解析《${doc.name}》", duration = SnackbarDuration.Short)
                                        }
                                    }) {
                                        Icon(
                                            Icons.Outlined.Refresh,
                                            contentDescription = "重新解析",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    "删除",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier
                                        .clickable { deleting = doc }
                                        .padding(horizontal = 8.dp, vertical = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    deleting?.let { doc ->
        YxConfirmDialog(
            title = "删除《${doc.name}》？",
            text = "文档会从所有对话中移除，AI 将无法再查询它的内容；聊天记录里的文件名会保留但标记为已删除。",
            confirmText = "删除",
            danger = true,
            onDismiss = { deleting = null },
            onConfirm = {
                scope.launch {
                    vm.store.deleteCompletely(doc.id)
                    deleting = null
                }
            }
        )
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1 shl 20 -> "%.1f MB".format(bytes / 1048576f)
    bytes >= 1 shl 10 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

private val dateFormat by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
private fun formatDate(ms: Long) = dateFormat.format(Date(ms))
