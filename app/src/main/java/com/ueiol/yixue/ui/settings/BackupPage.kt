package com.ueiol.yixue.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ueiol.yixue.YixueApp
import com.ueiol.yixue.data.backup.BackupManager
import com.ueiol.yixue.ui.common.YxCard
import com.ueiol.yixue.ui.common.YxConfirmDialog
import com.ueiol.yixue.ui.common.YxLargeTitle
import com.ueiol.yixue.ui.common.YxPill
import com.ueiol.yixue.ui.theme.YxExt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class BackupViewModel(private val backup: BackupManager) : ViewModel() {

    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    fun export(uri: Uri) {
        viewModelScope.launch {
            busy.value = true
            message.value = null
            backup.exportTo(uri)
                .onSuccess { message.value = "导出完成：$it" }
                .onFailure { message.value = "导出失败：${it.message}" }
            busy.value = false
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            busy.value = true
            message.value = null
            backup.importFrom(uri)
                .onSuccess { message.value = "恢复完成：$it" }
                .onFailure { message.value = "恢复失败：${it.message}" }
            busy.value = false
        }
    }

    companion object {
        @Composable
        fun create(): BackupViewModel = viewModel(factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as YixueApp
                BackupViewModel(app.container.backupManager)
            }
        })
    }
}

/**
 * 数据备份：导出 / 恢复全部学习数据（对话、笔记、测验、错题、教材、接口配置）。
 * SAF 选位置，ZIP 内含 backup.json + 笔记图片；API 密钥不进备份。
 */
@Composable
fun BackupPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vm = BackupViewModel.create()
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var confirmImport by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupManager.MIME_ZIP)
    ) { uri -> uri?.let { vm.export(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            confirmImport = true
        }
    }

    Column(modifier = modifier.fillMaxSize().navigationBarsPadding()) {
        YxLargeTitle(
            title = "数据备份",
            actions = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        )
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            YxCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "备份内容",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "对话与消息、笔记本、测验与作答记录、错题本、科目与教材、文档库、AI 接口配置，笔记图片与文档随包携带。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "API 密钥不会写入备份文件，恢复后请在「AI 服务设置」重新填写；恢复会覆盖当前全部数据。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            YxPill(
                text = "导出备份",
                primary = true,
                enabled = !busy,
                onClick = { exportLauncher.launch(BackupManager.suggestedFileName()) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            YxPill(
                text = "从备份文件恢复",
                enabled = !busy,
                onClick = { importLauncher.launch(arrayOf(BackupManager.MIME_ZIP)) },
                modifier = Modifier.fillMaxWidth()
            )
            message?.let { msg ->
                Spacer(Modifier.height(14.dp))
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (msg.startsWith("导出失败") || msg.startsWith("恢复失败")) {
                        MaterialTheme.colorScheme.error
                    } else {
                        YxExt.success
                    }
                )
            }
        }
    }

    if (confirmImport) {
        YxConfirmDialog(
            title = "确认恢复备份",
            text = "恢复会先清空当前全部数据（对话、笔记、测验、错题、教材），再用备份文件内容覆盖。此操作不可撤销。",
            confirmText = "覆盖恢复",
            danger = true,
            onDismiss = { confirmImport = false },
            onConfirm = {
                confirmImport = false
                pendingImportUri?.let { vm.import(it) }
                pendingImportUri = null
            }
        )
    }
}
