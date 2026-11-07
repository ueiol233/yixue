package com.ueiol.yixue.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ueiol.yixue.YixueApp
import com.ueiol.yixue.data.db.MessageHit
import com.ueiol.yixue.data.db.NoteEntity
import com.ueiol.yixue.data.db.QuestionHit
import com.ueiol.yixue.data.db.TextbookHit
import com.ueiol.yixue.data.db.TextbookDao
import com.ueiol.yixue.data.db.MessageDao
import com.ueiol.yixue.data.db.NoteDao
import com.ueiol.yixue.data.db.QuestionDao
import com.ueiol.yixue.data.db.escapeLike
import com.ueiol.yixue.ui.common.YxEmptyState
import com.ueiol.yixue.ui.common.YxLargeTitle
import com.ueiol.yixue.ui.common.YxPill
import com.ueiol.yixue.ui.common.YxCard
import com.ueiol.yixue.ui.common.formatShortTime
import com.ueiol.yixue.ui.theme.YxSheetShape
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 一次全局搜索的分组结果。 */
data class SearchResults(
    val notes: List<NoteEntity>,
    val messages: List<MessageHit>,
    val questions: List<QuestionHit>,
    val textbooks: List<TextbookHit>
) {
    val isEmpty: Boolean
        get() = notes.isEmpty() && messages.isEmpty() && questions.isEmpty() && textbooks.isEmpty()
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class GlobalSearchViewModel(
    private val noteDao: NoteDao,
    private val messageDao: MessageDao,
    private val questionDao: QuestionDao,
    private val textbookDao: TextbookDao
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    /** null = 空查询未搜索；SearchResults = 最近一次结果。 */
    val results: StateFlow<SearchResults?> = _query
        .debounce(250)
        .map { it.trim() }
        .flatMapLatest { q ->
            flow { emit(if (q.isEmpty()) null else searchAll(q)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setQuery(q: String) {
        _query.value = q
    }

    private suspend fun searchAll(q: String): SearchResults = coroutineScope {
        val escaped = escapeLike(q)
        val notes = async { noteDao.search(escaped) }
        val messages = async { messageDao.search(escaped) }
        val questions = async { questionDao.searchStems(escaped) }
        val textbooks = async { textbookDao.searchChunksGlobal(escaped) }
        SearchResults(notes.await(), messages.await(), questions.await(), textbooks.await())
    }

    companion object {
        @Composable
        fun create(): GlobalSearchViewModel = viewModel(factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as YixueApp
                val c = app.container
                GlobalSearchViewModel(c.noteDao, c.messageDao, c.questionDao, c.textbookDao)
            }
        })
    }
}

/**
 * 全局搜索：同时检索笔记、对话消息、试卷题干、教材正文；
 * 每处命中单独一行展示，点击某一行直接跳转并滚动到对应位置。
 */
@Composable
fun GlobalSearchPage(
    onBack: () -> Unit,
    onOpenNote: (noteId: Long, matchOffset: Int, matchLen: Int) -> Unit,
    onOpenSession: (sessionId: Long, messageId: Long) -> Unit,
    onOpenQuiz: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val vm = GlobalSearchViewModel.create()
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    var previewTextbook by remember { mutableStateOf<TextbookHit?>(null) }

    Column(modifier = modifier.fillMaxSize().navigationBarsPadding()) {
        YxLargeTitle(
            title = "全局搜索",
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
        OutlinedTextField(
            value = query,
            onValueChange = { vm.setQuery(it) },
            placeholder = { Text("搜索笔记 / 对话 / 题目 / 教材") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        val q = query.trim()
        when {
            q.isEmpty() -> YxEmptyState(
                icon = Icons.Outlined.Search,
                title = "搜索全部内容",
                hint = "输入关键词，同时检索笔记、对话、题目与教材",
                modifier = Modifier.weight(1f)
            )
            results == null -> {}
            results!!.isEmpty -> YxEmptyState(
                icon = Icons.Outlined.Search,
                title = "没有找到相关内容",
                hint = "换个关键词试试",
                modifier = Modifier.weight(1f)
            )
            else -> {
                val r = results!!
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (r.notes.isNotEmpty()) {
                        item { SectionHeader("笔记 · ${r.notes.size}") }
                        items(r.notes, key = { "n${it.id}" }) { note ->
                            val (rows, total) = hitRows(note.contentMarkdown, q, maxRows = 5)
                            ResultCard(
                                title = note.title.ifBlank { "无标题笔记" },
                                rows = rows,
                                total = total,
                                query = q,
                                time = formatShortTime(note.updatedAt),
                                onOpen = { onOpenNote(note.id, rows.firstOrNull()?.offset ?: 0, rows.firstOrNull()?.len ?: 0) },
                                onOpenOccurrence = { row -> onOpenNote(note.id, row.offset, row.len) }
                            )
                        }
                    }
                    if (r.messages.isNotEmpty()) {
                        item { SectionHeader("对话 · ${r.messages.size}") }
                        items(r.messages, key = { "m${it.id}" }) { hit ->
                            val (rows, total) = hitRows(hit.content, q, maxRows = 5)
                            ResultCard(
                                title = hit.sessionTitle,
                                rows = rows,
                                total = total,
                                query = q,
                                time = formatShortTime(hit.createdAt),
                                onOpen = { onOpenSession(hit.sessionId, hit.id) },
                                onOpenOccurrence = { _ -> onOpenSession(hit.sessionId, hit.id) }
                            )
                        }
                    }
                    if (r.questions.isNotEmpty()) {
                        item { SectionHeader("题目 · ${r.questions.size}") }
                        items(r.questions, key = { "q${it.id}" }) { hit ->
                            val (rows, total) = hitRows(hit.stem, q, maxRows = 1)
                            ResultCard(
                                title = "题目",
                                rows = rows,
                                total = total,
                                query = q,
                                onOpen = { onOpenQuiz(hit.quizId) },
                                onOpenOccurrence = { _ -> onOpenQuiz(hit.quizId) }
                            )
                        }
                    }
                    if (r.textbooks.isNotEmpty()) {
                        item { SectionHeader("教材 · ${r.textbooks.size}") }
                        items(r.textbooks, key = { "t${it.textbookId}-${it.seq}" }) { hit ->
                            val (rows, total) = hitRows(hit.content, q, maxRows = 3)
                            ResultCard(
                                title = hit.textbookTitle + if (hit.page > 0) " · 第${hit.page}页" else "",
                                rows = rows,
                                total = total,
                                query = q,
                                onOpen = { previewTextbook = hit },
                                onOpenOccurrence = { _ -> previewTextbook = hit }
                            )
                        }
                    }
                }
            }
        }
    }

    previewTextbook?.let { hit ->
        Dialog(onDismissRequest = { previewTextbook = null }) {
            Surface(color = MaterialTheme.colorScheme.surface, shape = YxSheetShape, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        hit.textbookTitle + if (hit.page > 0) " · 第${hit.page}页" else "",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        hit.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    )
                    Spacer(Modifier.height(14.dp))
                    YxPill(
                        text = "关闭",
                        onClick = { previewTextbook = null },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun ResultCard(
    title: String,
    rows: List<SearchHitRow>,
    total: Int,
    query: String,
    time: String? = null,
    onOpen: () -> Unit,
    onOpenOccurrence: (SearchHitRow) -> Unit
) {
    val highlightColor = MaterialTheme.colorScheme.primary
    YxCard(onClick = onOpen) {
        Column(modifier = Modifier.padding(14.dp)) {
            // 标题里的关键词同样高亮
            Text(
                highlight(title, query, highlightColor),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(Modifier.height(4.dp))
            // 每处命中一行「…前后文…」，行级可点击 → 跳转并定位到该处
            rows.forEachIndexed { i, row ->
                if (i > 0) Spacer(Modifier.height(3.dp))
                Text(
                    highlight(row.text, query, highlightColor),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = if (total > 1) {
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenOccurrence(row) }
                            .padding(vertical = 2.dp)
                    } else {
                        Modifier
                    }
                )
            }
            if (total > rows.size) {
                Spacer(Modifier.height(3.dp))
                Text(
                    "…共 $total 处命中",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (time != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 一行命中摘要：显示文本 + 命中在原文中的绝对偏移与长度（定位用）。 */
data class SearchHitRow(val text: String, val offset: Int, val len: Int)

/** 展示用轻清理：去掉会干扰阅读的 Markdown 标记，保留括号等数学符号。 */
private fun displayClean(s: String): String =
    s.replace(Regex("[$#>`*]"), "").replace(Regex("\\s+"), " ").trim()

/**
 * 摘要：在**原文**中逐处统计命中（保证偏移与后续定位一致），
 * 每处生成一行以命中位置为中心的上下文（最多 [maxRows] 行），返回 <行列表, 命中总数>。
 * 完全没命中（如仅标题命中）时退化为开头一段。
 */
private fun hitRows(raw: String, query: String, maxRows: Int = 5, width: Int = 110): Pair<List<SearchHitRow>, Int> {
    if (raw.isBlank() || query.isBlank()) {
        val head = displayClean(raw.take(width))
        return (if (head.isBlank()) emptyList() else listOf(SearchHitRow(head, 0, 0))) to 0
    }
    val lower = raw.lowercase()
    val q = query.lowercase()
    val rows = mutableListOf<SearchHitRow>()
    var from = 0
    var total = 0
    while (true) {
        val idx = lower.indexOf(q, from)
        if (idx < 0) break
        if (rows.size < maxRows) {
            val start = maxOf(0, idx - width / 3)
            val end = minOf(raw.length, idx + q.length + width - width / 3)
            val prefix = if (start > 0) "…" else ""
            val suffix = if (end < raw.length) "…" else ""
            rows += SearchHitRow(
                prefix + displayClean(raw.substring(start, end)) + suffix,
                idx, q.length
            )
        }
        total++
        from = idx + q.length
    }
    if (total == 0) {
        val head = displayClean(raw.take(width))
        if (head.isNotBlank()) rows += SearchHitRow(head, 0, 0)
    }
    return rows to total
}

/** 关键词高亮（大小写不敏感）。 */
private fun highlight(text: String, query: String, color: androidx.compose.ui.graphics.Color): AnnotatedString =
    buildAnnotatedString {
        append(text)
        if (query.isBlank()) return@buildAnnotatedString
        val lower = text.lowercase()
        val q = query.lowercase()
        var i = 0
        while (true) {
            val idx = lower.indexOf(q, i)
            if (idx < 0) break
            addStyle(
                SpanStyle(color = color, fontWeight = FontWeight.Bold),
                idx, idx + q.length
            )
            i = idx + q.length
        }
    }
