package com.ueiol.yixue.ui.notebook

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ueiol.yixue.data.db.NoteEntity
import com.ueiol.yixue.ui.common.YxCard
import com.ueiol.yixue.ui.common.YxEmptyState
import com.ueiol.yixue.ui.common.YxLargeTitle
import com.ueiol.yixue.ui.common.formatShortTime
import com.ueiol.yixue.ui.theme.YxCardShape
import kotlinx.coroutines.launch

/** 笔记本列表：搜索 + 新建 + 右滑删除（Snackbar 撤销）。 */
@Composable
fun NotebookPage(
    vm: NotebookViewModel,
    onOpenNote: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val notes by vm.notes.collectAsState()
    val query by vm.query.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun onDeleteNote(note: NoteEntity) {
        vm.delete(note)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "已删除「${note.title.ifBlank { "无标题笔记" }}」",
                actionLabel = "撤销",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) vm.undoDelete()
        }
    }

    // 外层 MainScreen 已有 Scaffold，这里不能再套一层（会造成双倍状态栏/手势条空白）
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            YxLargeTitle(title = "笔记本", actions = {
                // 新建入口与科目管理的「+」一致
                IconButton(onClick = { onOpenNote(0L) }) {
                    Icon(Icons.Outlined.Add, contentDescription = "新建笔记", tint = MaterialTheme.colorScheme.onSurface)
                }
            })
            OutlinedTextField(
                value = query,
                onValueChange = { vm.setQuery(it) },
                placeholder = { Text("搜索笔记") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            val list = notes.filter {
                query.isBlank() || it.title.contains(query, true) || it.contentMarkdown.contains(query, true)
            }
            if (list.isEmpty()) {
                YxEmptyState(
                    icon = Icons.Outlined.EditNote,
                    title = if (query.isBlank()) "还没有笔记" else "没有匹配的笔记",
                    hint = if (query.isBlank()) "记录学习心得，支持图片、函数图和公式" else "换个关键词试试",
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(list, key = { it.id }) { note ->
                        SwipeableNoteCard(
                            note = note,
                            onClick = { onOpenNote(note.id) },
                            onDelete = { onDeleteNote(note) }
                        )
                    }
                }
            }
        }
        // 撤销提示挂在页面底部，不遮挡列表
        SnackbarHost(
            snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
        )
    }
}

/** 右滑删除的笔记卡：只有从右往左滑动触发，误触面最小。 */
@Composable
private fun SwipeableNoteCard(
    note: NoteEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(YxCardShape)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) {
        NoteCard(note, onClick = onClick)
    }
}

@Composable
private fun NoteCard(note: NoteEntity, onClick: () -> Unit) {
    YxCard(onClick = onClick) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(note.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.size(4.dp))
            Text(
                note.contentMarkdown.replace(Regex("[$#>`]"), "").take(60),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
            Spacer(Modifier.size(4.dp))
            Text(formatShortTime(note.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
