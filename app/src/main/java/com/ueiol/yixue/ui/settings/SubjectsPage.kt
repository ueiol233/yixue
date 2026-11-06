package com.ueiol.yixue.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ueiol.yixue.YixueApp
import com.ueiol.yixue.data.AppContainer
import com.ueiol.yixue.data.db.SubjectEntity
import com.ueiol.yixue.data.db.TextbookChunkEntity
import com.ueiol.yixue.data.db.TextbookEntity
import com.ueiol.yixue.data.textbook.TextbookImporter
import com.ueiol.yixue.ui.common.YxEmptyState
import com.ueiol.yixue.ui.common.YxConfirmDialog
import com.ueiol.yixue.ui.common.YxInputDialog
import com.ueiol.yixue.ui.common.YxLargeTitle
import com.ueiol.yixue.ui.common.YxPill
import com.ueiol.yixue.ui.theme.YxCardShape
import com.ueiol.yixue.ui.theme.YxExt
import com.ueiol.yixue.ui.theme.YxRadius
import com.ueiol.yixue.ui.theme.YxSheetShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 科目管理：新增 / 删除科目；每个科目可展开管理自己的教材
 * （导入 PDF / Word、设置「与我使用的教材完全一致」、删除教材）。
 * 删除科目时连带清理该科目的教材与分块。
 */
@Composable
fun SubjectsPage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val app = LocalApp()
    val c = app.container
    val scope = rememberCoroutineScope()
    val subjects by c.subjectDao.observeAll().collectAsState(initial = emptyList())
    val books by c.textbookDao.observeAll().collectAsState(initial = emptyList())
    // 教材按科目预分组，避免每个科目卡片组合时各自 O(n) filter
    val booksBySubject = remember(books) { books.groupBy { it.subjectId } }
    val subjectPalette = YxExt.subjectPalette

    var adding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<SubjectEntity?>(null) }
    // 一次只展开一个科目
    var expandedId by remember { mutableStateOf<Long?>(null) }
    var importing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(message) {
        if (message != null) {
            delay(4000)
            message = null
        }
    }

    Column(modifier = modifier.fillMaxSize().navigationBarsPadding()) {
        YxLargeTitle(title = "科目管理", actions = {
            IconButton(onClick = { adding = true }) {
                Icon(Icons.Outlined.Add, contentDescription = "新增科目", tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
            }
        })
        message?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = if (it.startsWith("《")) YxExt.success else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }
        if (subjects.isEmpty()) {
            YxEmptyState(
                icon = Icons.Outlined.School,
                title = "还没有科目",
                hint = "点右上角「+」新增，或让 AI 归档错题时自动创建",
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(subjects, key = { it.id }) { s ->
                    SubjectCard(
                        subject = s,
                        books = booksBySubject[s.id].orEmpty(),
                        expanded = expandedId == s.id,
                        importing = importing,
                        onToggle = { expandedId = if (expandedId == s.id) null else s.id },
                        onImport = {
                            scope.launch {
                                importing = true
                                message = importTextbook(c, s, it)
                                importing = false
                            }
                        },
                        onExactChange = { book, exact ->
                            scope.launch { c.textbookDao.update(book.copy(isExactMatch = exact)) }
                        },
                        onDeleteBook = { book ->
                            scope.launch {
                                c.textbookDao.deleteChunks(book.id)
                                c.textbookDao.delete(book)
                            }
                        },
                        onDeleteSubject = { deleting = s }
                    )
                }
            }
        }
    }

    if (adding) {
        YxInputDialog(
            title = "新增科目",
            value = newName,
            onValueChange = { newName = it },
            placeholder = "科目名",
            confirmEnabled = newName.isNotBlank(),
            onDismiss = { adding = false },
            onConfirm = {
                val n = newName.trim()
                if (n.isNotEmpty()) {
                    scope.launch {
                        val count = c.subjectDao.count()
                        c.subjectDao.insert(SubjectEntity(name = n, colorIndex = count % 7, order = count))
                    }
                    adding = false
                    newName = ""
                }
            }
        )
    }
    deleting?.let { s ->
        YxConfirmDialog(
            title = "删除科目「${s.name}」？",
            text = "该科目下的教材、错题归档关联将一并清理。",
            confirmText = "删除",
            danger = true,
            onDismiss = { deleting = null },
            onConfirm = {
                deleting = null
                scope.launch {
                    // 连带清理教材与分块
                    c.textbookDao.getBySubject(s.id).forEach { book ->
                        c.textbookDao.deleteChunks(book.id)
                        c.textbookDao.delete(book)
                    }
                    c.subjectDao.delete(s)
                }
            }
        )
    }

    // 导入解析中：全屏小遮罩
    if (importing) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(YxRadius.Medium),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.height(10.dp))
                    Text("正在解析教材…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text("大文件可能需要几十秒", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** 解析并入库一本教材，返回给用户的一句话结果。 */
private suspend fun importTextbook(container: AppContainer, subject: SubjectEntity, uri: Uri): String {
    return try {
        val parsed = container.textbookImporter.parse(uri)
        if (parsed.chunks.isEmpty()) return "未能从文档中提取到文字，请换一个文件"
        val now = System.currentTimeMillis()
        val id = container.textbookDao.insert(
            TextbookEntity(subjectId = subject.id, title = parsed.title, fileName = parsed.fileName, createdAt = now)
        )
        container.textbookDao.insertChunks(
            parsed.chunks.map {
                TextbookChunkEntity(textbookId = id, seq = it.seq, page = it.page, content = it.content)
            }
        )
        val chars = parsed.chunks.sumOf { it.content.length }
        container.textbookDao.update(
            TextbookEntity(
                id = id,
                subjectId = subject.id,
                title = parsed.title,
                fileName = parsed.fileName,
                chunkCount = parsed.chunks.size,
                charCount = chars,
                createdAt = now
            )
        )
        "《${parsed.title}》导入成功：${parsed.chunks.size} 段，约 $chars 字"
    } catch (t: Throwable) {
        t.message ?: "导入失败，请重试"
    }
}

/**
 * 科目卡片：主行（色点 + 名称 + 教材数 + 展开箭头），
 * 展开后是该科目的教材行（匹配开关 + 删除）与导入 / 删除科目操作。
 */
@Composable
private fun SubjectCard(
    subject: SubjectEntity,
    books: List<TextbookEntity>,
    expanded: Boolean,
    importing: Boolean,
    onToggle: () -> Unit,
    onImport: (Uri) -> Unit,
    onExactChange: (TextbookEntity, Boolean) -> Unit,
    onDeleteBook: (TextbookEntity) -> Unit,
    onDeleteSubject: () -> Unit
) {
    val subjectPalette = YxExt.subjectPalette
    var confirmDeleteBook by remember { mutableStateOf<TextbookEntity?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport(uri)
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = YxCardShape,
        modifier = Modifier.fillMaxWidth().animateContentSize(tween(160))
    ) {
        Column {
            // 主行：点击展开 / 收起
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(12.dp)
                        .background(subjectPalette[subject.colorIndex.mod(subjectPalette.size)], CircleShape)
                )
                Spacer(Modifier.size(12.dp))
                Text(subject.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Text(
                    if (books.isEmpty()) "无教材" else "${books.size} 本教材",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(if (expanded) 180f else 0f)
                )
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 40.dp))
                if (books.isEmpty()) {
                    Text(
                        "该科目还没有教材",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 40.dp, vertical = 10.dp)
                    )
                }
                books.forEach { book ->
                    // 教材行
                    Row(
                        modifier = Modifier.padding(start = 40.dp, end = 8.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                book.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Text(
                                "${book.chunkCount} 段 · 约 ${book.charCount} 字",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { confirmDeleteBook = book }, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = "删除教材",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    // 匹配开关行
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onExactChange(book, !book.isExactMatch) }
                            .padding(start = 40.dp, end = 8.dp, top = 2.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "与我使用的教材完全一致",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (book.isExactMatch) "AI 将按这本教材的章节与术语讲解"
                                else "AI 会提醒该教材可能与实际版本不同",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = book.isExactMatch,
                            onCheckedChange = { onExactChange(book, it) },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 40.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    YxPill(
                        text = "导入 PDF / Word 教材",
                        enabled = !importing,
                        height = 40.dp,
                        modifier = Modifier.weight(1f)
                    ) { filePicker.launch(TextbookImporter.SUPPORTED_MIME) }
                    Text(
                        "删除科目",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .clickable(onClick = onDeleteSubject)
                            .padding(horizontal = 8.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }

    // 删除教材确认：连全部分块一起删，数据量大且不可恢复
    confirmDeleteBook?.let { book ->
        YxConfirmDialog(
            title = "删除教材「${book.title}」？",
            text = "该教材的全部正文分块将一并删除，AI 讲解将不再引用此教材。",
            confirmText = "删除",
            danger = true,
            onDismiss = { confirmDeleteBook = null },
            onConfirm = {
                confirmDeleteBook = null
                onDeleteBook(book)
            }
        )
    }
}

@androidx.compose.runtime.Composable
private fun LocalApp(): YixueApp =
    androidx.compose.ui.platform.LocalContext.current.applicationContext as YixueApp
