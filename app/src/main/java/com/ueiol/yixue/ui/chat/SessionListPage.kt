package com.ueiol.yixue.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ueiol.yixue.data.db.SessionEntity
import com.ueiol.yixue.ui.common.formatShortTime
import com.ueiol.yixue.ui.common.YxCard
import com.ueiol.yixue.ui.common.YxConfirmDialog
import com.ueiol.yixue.ui.common.YxEmptyState
import com.ueiol.yixue.ui.common.YxInputDialog
import com.ueiol.yixue.ui.common.YxLargeTitle
import com.ueiol.yixue.ui.theme.YxCardShape

/** 历史会话列表：置顶优先，支持重命名/删除。 */
@Composable
fun SessionListPage(
    vm: ChatViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sessions by vm.sessions.collectAsState()
    var renaming by remember { mutableStateOf<SessionEntity?>(null) }
    var renamingText by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<SessionEntity?>(null) }

    Column(modifier = modifier.fillMaxSize().navigationBarsPadding()) {
        YxLargeTitle(title = "历史会话", actions = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
            }
        })
        if (sessions.isEmpty()) {
            YxEmptyState(
                icon = Icons.Outlined.History,
                title = "还没有会话",
                hint = "在对话页提问后，会话会自动保存到这里",
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sessions, key = { it.id }) { session ->
                    SessionCard(
                        session = session,
                        onClick = { vm.selectSession(session.id); onBack() },
                        onRename = { renaming = session; renamingText = session.title },
                        onPin = { vm.togglePin(session) },
                        onDelete = { deleting = session }
                    )
                }
            }
        }
    }

    // 删除会话需二次确认，避免误删
    deleting?.let { target ->
        YxConfirmDialog(
            title = "删除会话「${target.title}」？",
            text = "会话里的消息会一并删除，无法恢复。",
            confirmText = "删除",
            danger = true,
            onDismiss = { deleting = null },
            onConfirm = {
                deleting = null
                vm.deleteSession(target)
            }
        )
    }

    renaming?.let { target ->
        YxInputDialog(
            title = "重命名会话",
            value = renamingText,
            onValueChange = { renamingText = it },
            confirmEnabled = renamingText.isNotBlank(),
            onDismiss = { renaming = null },
            onConfirm = {
                vm.renameSession(target.id, renamingText)
                renaming = null
            }
        )
    }
}
@Composable
private fun SessionCard(
    session: SessionEntity,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    
    YxCard(modifier = Modifier, onClick = onClick) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(session.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(formatShortTime(session.updatedAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (session.pinned) {
                Icon(Icons.Outlined.PushPin, contentDescription = "已置顶", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 4.dp))
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "更多", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(if (session.pinned) "取消置顶" else "置顶") }, onClick = { menuOpen = false; onPin() })
                    DropdownMenuItem(text = { Text("重命名") }, onClick = { menuOpen = false; onRename() })
                    DropdownMenuItem(text = { Text("删除") }, onClick = { menuOpen = false; onDelete() })
                }
            }
        }
    }
}