package com.ueiol.yixue.ui.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ueiol.yixue.YixueApp
import com.ueiol.yixue.data.ai.OpenAiClient
import com.ueiol.yixue.data.db.NoteDao
import com.ueiol.yixue.data.db.NoteEntity
import com.ueiol.yixue.data.db.QuizSummary
import com.ueiol.yixue.data.db.SessionEntity
import com.ueiol.yixue.data.db.SubjectDao
import com.ueiol.yixue.data.db.TextbookDao
import com.ueiol.yixue.data.db.escapeLike
import com.ueiol.yixue.data.repo.ChatRepository
import com.ueiol.yixue.data.repo.QuizRepository
import com.ueiol.yixue.domain.agent.Prompts
import com.ueiol.yixue.domain.agent.QuizAgent
import com.ueiol.yixue.domain.model.ApiFuncCall
import com.ueiol.yixue.domain.model.ApiMessage
import com.ueiol.yixue.domain.model.ApiToolCall
import com.ueiol.yixue.domain.model.ChatMessage
import com.ueiol.yixue.domain.model.ToolDef
import com.ueiol.yixue.domain.model.ToolFunction
import com.ueiol.yixue.domain.model.assistantToolCallMessage
import com.ueiol.yixue.domain.model.toApiMessage
import com.ueiol.yixue.domain.model.toolResultMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 工具调用状态：name = 工具名，label = 展示给用户的一句话。 */
data class ToolActivity(val name: String, val label: String)

class ChatViewModel(
    private val repo: ChatRepository,
    private val quizRepo: QuizRepository,
    private val noteDao: NoteDao,
    private val subjectDao: SubjectDao,
    private val textbookDao: TextbookDao,
    private val quizAgent: QuizAgent,
    private val aiClient: OpenAiClient,
    private val app: YixueApp,
    private val isConfigured: () -> Boolean
) : ViewModel() {

    /** 主页「+」里切换模型用。 */
    val settingsStore: com.ueiol.yixue.data.settings.SettingsStore get() = app.container.settings

    val sessions: StateFlow<List<SessionEntity>> = repo.observeSessions()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _currentSessionId = MutableStateFlow<Long?>(null)
    val currentSessionId: StateFlow<Long?> = _currentSessionId

    val messages = _currentSessionId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.observeMessages(id) }

    /** 非空表示正在流式接收 */
    private val _streamingText = MutableStateFlow<String?>(null)
    val streamingText: StateFlow<String?> = _streamingText

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // ---------- Agent 工具：generate_quiz ----------

    /** 工具调用进行中的状态（聊天页展示"正在做什么"）。 */
    private val _toolActivity = MutableStateFlow<ToolActivity?>(null)
    val toolActivity: StateFlow<ToolActivity?> = _toolActivity

    /** 会话中练习卡片所需的试卷汇总（按 id 索引）。 */
    val quizSummaries: StateFlow<Map<Long, QuizSummary>> = quizRepo.observeSummaries()
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    companion object {
        const val TOOL_QUIZ = "generate_quiz"
        const val TOOL_NOTE = "append_note"
        const val TOOL_TEXTBOOK = "search_textbook"

        val quizTool = ToolDef(
            function = ToolFunction(
                name = TOOL_QUIZ,
                description = "生成一套练习题并自动打开答题卡。当用户想要出题、练习、做题、测验、巩固某个知识点时必须调用此工具，不要在回复文本中直接写题目。",
                parameters = Json.parseToJsonElement(
                    """
                    {"type":"object","properties":{
                      "subject":{"type":"string","description":"科目，如 数学/物理/英语"},
                      "types":{"type":"array","items":{"type":"string","enum":["single","multiple","fill"]},"description":"题型：single单选 multiple多选 fill填空"},
                      "count":{"type":"integer","description":"题目数量"},
                      "difficulty":{"type":"string","description":"难度：简单/中等/困难"},
                      "topic":{"type":"string","description":"考察的知识点或范围描述"}
                    },"required":["subject","count"]}
                    """.trimIndent()
                )
            )
        )

        val noteTool = ToolDef(
            function = ToolFunction(
                name = TOOL_NOTE,
                description = "把整理好的内容写入用户的笔记本。当用户要求记录、保存、整理、收藏知识点/讲解/总结到笔记时调用。内容为 Markdown，可含 LaTeX 公式（行内 \$...\$，独立 \$\$...\$\$）、Markdown 表格、plot 绘图代码块。",
                parameters = Json.parseToJsonElement(
                    """
                    {"type":"object","properties":{
                      "title":{"type":"string","description":"笔记标题；用户指定了已有笔记就填该笔记标题，否则为内容起一个简短标题"},
                      "content":{"type":"string","description":"要写入/追加的 Markdown 内容"}
                    },"required":["title","content"]}
                    """.trimIndent()
                )
            )
        )

        val textbookTool = ToolDef(
            function = ToolFunction(
                name = TOOL_TEXTBOOK,
                description = "在用户导入的教材中检索原文。当问题涉及教材里的定义、定理、例题、章节内容，或用户要求按教材讲解/核对教材说法时调用。关键词要精准（如「正弦定理」「摩擦力」）；一次没找到可换关键词再试。",
                parameters = Json.parseToJsonElement(
                    """
                    {"type":"object","properties":{
                      "subject":{"type":"string","description":"科目名，如 数学/物理/英语"},
                      "query":{"type":"string","description":"要检索的关键词或短语（2-10 字，尽量精准）"}
                    },"required":["subject","query"]}
                    """.trimIndent()
                )
            )
        )

        /** 从工具参数构造出题请求描述。 */
        fun quizRequestFromArgs(args: String): String {
            val obj = runCatching { Json.parseToJsonElement(args).jsonObject }.getOrNull()
            if (obj == null) return args
            val subject = (obj["subject"] as? JsonPrimitive)?.content ?: "综合"
            val count = (obj["count"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 5
            val types = (obj["types"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.content }
                ?.joinToString("、")
                ?.let { mapTypes(it) } ?: "单选、填空"
            val difficulty = (obj["difficulty"] as? JsonPrimitive)?.content ?: "中等"
            val topic = (obj["topic"] as? JsonPrimitive)?.content ?: ""
            return "科目：$subject；题型：$types；数量：$count；难度：$difficulty" +
                (if (topic.isNotBlank()) "；知识点：$topic" else "") + "。请生成一套练习题。"
        }

        private fun mapTypes(joined: String): String {
            var s = joined
            s = s.replace("single", "单选").replace("multiple", "多选").replace("fill", "填空")
            return s
        }
    }
    init {
        viewModelScope.launch {
            // 空会话不保存：先清掉历史遗留的空会话，再打开最近一条有内容的会话
            repo.deleteEmptySessions()
            _currentSessionId.value = repo.observeSessions().first().firstOrNull()?.id
        }
    }

    /** 新对话先不落库，第一次发消息时才创建会话。 */
    fun newChat() {
        if (_streamingText.value != null) return
        _currentSessionId.value = null
    }

    fun selectSession(id: Long) {
        if (_streamingText.value != null) return
        _currentSessionId.value = id
    }

    // ---------- 全局搜索跳转：滚到指定消息并短暂高亮 ----------

    private val _scrollToMessage = MutableStateFlow<Long?>(null)
    val scrollToMessage: StateFlow<Long?> = _scrollToMessage

    /** 全局搜索点中某条对话消息后调用：切换会话 + 请求滚动定位。 */
    fun requestScrollToMessage(id: Long) {
        _scrollToMessage.value = id
    }

    /** ChatPage 完成滚动后消费掉，避免切回该会话时重复滚动。 */
    fun consumeScrollToMessage() {
        _scrollToMessage.value = null
    }

    /** 交卷报告：挂到输入框上方作为"附件"，由用户确认后再发送。 */
    private val _pendingReport = MutableStateFlow<String?>(null)
    val pendingReport: StateFlow<String?> = _pendingReport

    fun attachReport(sessionId: Long?, report: String) {
        if (report.isBlank()) return
        // AI 正在回复时先挂上报告，等回复结束再发；此时不切换会话来打断流
        if (_streamingText.value == null && (sessionId ?: 0L) > 0) {
            _currentSessionId.value = sessionId
        }
        _pendingReport.value = report.trim()
    }

    fun clearPendingReport() { _pendingReport.value = null }

    fun renameSession(id: Long, title: String) {
        viewModelScope.launch { repo.renameSession(id, title.trim().ifEmpty { "未命名" }) }
    }

    fun togglePin(session: SessionEntity) {
        viewModelScope.launch { repo.setPinned(session.id, !session.pinned) }
    }

    fun deleteSession(session: SessionEntity) {
        viewModelScope.launch {
            repo.deleteSession(session)
            if (_currentSessionId.value == session.id) _currentSessionId.value = null
        }
    }

    fun dismissError() {
        _error.value = null
    }

    // ---------- 图片附件（视觉提问） ----------

    /** 待发送附件：气泡展示用（文件名 + 压缩后的 data URL），后续贴文件沿用此结构。 */
    data class PendingAttachment(val name: String, val dataUrl: String)

    private val _pendingImage = MutableStateFlow<PendingAttachment?>(null)
    val pendingImage: StateFlow<PendingAttachment?> = _pendingImage

    fun attachImage(uri: Uri) {
        viewModelScope.launch {
            try {
                val attached = withContext(Dispatchers.IO) {
                    val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@withContext null
                    // 量尺寸 → 按最长边 1280 降采样 → JPEG 85% 压缩：
                    // 原图（可达数 MB）不压缩会撑爆内存，data URL 还会整段写进数据库
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    val maxSide = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
                    var sample = 1
                    while (maxSide / (sample * 2) >= 1280) sample *= 2
                    val bitmap = BitmapFactory.decodeByteArray(
                        bytes, 0, bytes.size,
                        BitmapFactory.Options().apply { inSampleSize = sample }
                    ) ?: return@withContext null
                    val out = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    bitmap.recycle()
                    val name = queryDisplayName(uri) ?: "图片.jpg"
                    PendingAttachment(name, "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
                }
                if (attached != null) _pendingImage.value = attached
            } catch (t: Throwable) {
                _error.value = "图片读取失败：${t.message ?: "未知错误"}"
            }
        }
    }

    /** 附件气泡展示用：取内容 Uri 的显示文件名，取不到返回 null。 */
    private fun queryDisplayName(uri: Uri): String? = try {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (t: Throwable) {
        null
    }

    fun clearPendingImage() { _pendingImage.value = null }

    // ---------- Agent 对话循环 ----------

    fun send(text: String) = sendWithImages(text, emptyList())

    fun sendWithImages(text: String, externalImages: List<String>) {
        // 输入框文字 + 挂着的答题报告一起发送
        val typed = text.trim()
        val report = _pendingReport.value
        val body = buildString {
            if (typed.isNotEmpty()) append(typed)
            if (report != null) {
                if (typed.isNotEmpty()) append("\n\n")
                append(report)
            }
        }.trim()
        if (body.isEmpty() || _streamingText.value != null) return
        if (!isConfigured()) {
            _error.value = "请先到「我的 → AI 服务设置」配置接口地址与 API Key"
            return
        }
        viewModelScope.launch {
            try {
                val sessionId = _currentSessionId.value ?: repo.createSession()
                    .also { _currentSessionId.value = it }
                val images = if (externalImages.isEmpty()) listOfNotNull(_pendingImage.value?.dataUrl) else externalImages
                _pendingImage.value = null
                _pendingReport.value = null
                repo.addUserMessage(sessionId, body, images)
                _streamingText.value = ""

                val apiMsgs = ArrayList<ApiMessage>()
                // 教材上下文：告诉 AI 用户导入了哪些教材、是否与学生所用版本一致
                apiMsgs += ChatMessage("system", Prompts.CHAT_SYSTEM + textbookContext()).toApiMessage()
                repo.historyMessages(sessionId).forEach { m ->
                    apiMsgs += ChatMessage(m.role, m.content, repo.attachmentsOf(m)).toApiMessage()
                }

                // Agent 循环：模型可多次调用工具，最多 3 轮
                var finalText: String? = null
                var quizIdForReply: Long? = null
                var round = 0
                try {
                    while (finalText == null && round < 3) {
                        round++
                        val result = aiClient.streamApi(apiMsgs, tools = listOf(quizTool, noteTool, textbookTool)) { delta ->
                            _streamingText.value = (_streamingText.value ?: "") + delta
                        }
                        if (!result.hasToolCalls) {
                            finalText = result.content
                            break
                        }
                        apiMsgs += assistantToolCallMessage(
                            result.toolCalls.map { ApiToolCall(it.id, "function", ApiFuncCall(it.name, it.arguments)) }
                        )
                        for (tc in result.toolCalls) {
                            val toolResult = when (tc.name) {
                                TOOL_QUIZ -> {
                                    _toolActivity.value = ToolActivity(TOOL_QUIZ, "正在生成练习…")
                                    val (message, quizId) = executeQuizTool(tc.arguments)
                                    if (quizId != null) quizIdForReply = quizId
                                    _toolActivity.value = null
                                    message
                                }
                                TOOL_NOTE -> {
                                    _toolActivity.value = ToolActivity(TOOL_NOTE, "正在写入笔记…")
                                    val message = executeNoteTool(tc.arguments)
                                    _toolActivity.value = null
                                    message
                                }
                                TOOL_TEXTBOOK -> {
                                    _toolActivity.value = ToolActivity(TOOL_TEXTBOOK, "正在翻教材…")
                                    val message = executeTextbookSearchTool(tc.arguments)
                                    _toolActivity.value = null
                                    message
                                }
                                else -> "未知工具：${tc.name}"
                            }
                            apiMsgs += toolResultMessage(tc.id, toolResult)
                        }
                        _streamingText.value = (_streamingText.value ?: "") + "\n\n"
                    }
                } finally {
                    _toolActivity.value = null
                }

                val reply = finalText ?: "（工具调用轮次已达上限）"
                // 把试卷挂在这条回复上，聊天页据此渲染练习卡片
                repo.addAssistantMessage(sessionId, reply.trim(), quizIdForReply)
                _streamingText.value = null
            } catch (t: Throwable) {
                _error.value = t.message ?: "请求失败"
                _streamingText.value = null
            }
        }
    }

    /** 执行出题工具：生成试卷 → 入库，返回（给模型的结果文本, 试卷 id）。 */
    private suspend fun executeQuizTool(argsJson: String): Pair<String, Long?> {
        val request = quizRequestFromArgs(argsJson)
        return try {
            val spec = quizAgent.generate(request)
            val quizId = quizRepo.createFromSpec(_currentSessionId.value, spec)
            "试卷《${spec.title}》已生成（共 ${spec.questions.size} 题），已在对话中生成练习卡片。" to quizId
        } catch (t: Throwable) {
            "出题失败：${t.message ?: "未知错误"}。请告知用户稍后重试。" to null
        }
    }

    /**
     * 执行教材检索工具：按科目找教材，对每本做关键词全文匹配，
     * 返回带页码与「是否与学生教材一致」标注的原文片段。
     */
    private suspend fun executeTextbookSearchTool(argsJson: String): String {
        return try {
            val obj = Json.parseToJsonElement(argsJson).jsonObject
            val subject = ((obj["subject"] as? JsonPrimitive)?.content ?: "").trim()
            val query = ((obj["query"] as? JsonPrimitive)?.content ?: "").trim()
            if (subject.isEmpty() || query.isEmpty()) return "参数不完整：需要 subject 和 query。"
            val subj = subjectDao.getByName(subject)
                ?: subjectDao.observeAll().first().firstOrNull {
                    it.name.contains(subject) || subject.contains(it.name)
                }
                ?: return "科目「$subject」不存在，用户已导入教材的科目见系统提示中的教材库清单。"
            val books = textbookDao.getBySubject(subj.id)
            if (books.isEmpty()) return "科目「${subj.name}」尚未导入教材。可建议用户到「我的 → 我的教材」导入。"
            val sb = StringBuilder()
            var totalHits = 0
            for (book in books) {
                val hits = textbookDao.searchChunks(book.id, escapeLike(query), 6)
                val tag = if (book.isExactMatch) "与学生教材完全一致" else "仅供参考，版本可能不同"
                if (hits.isEmpty()) {
                    sb.append("《${book.title}》[$tag]：未找到包含「$query」的内容。\n")
                } else {
                    totalHits += hits.size
                    sb.append("《${book.title}》[$tag] 命中 ${hits.size} 段：\n")
                    hits.forEach { chunk ->
                        val page = if (chunk.page > 0) "第 ${chunk.page} 页" else "第 ${chunk.seq + 1} 段"
                        sb.append("[$page] ${chunk.content}\n")
                    }
                }
                sb.append("\n")
            }
            if (totalHits == 0) {
                "在科目「${subj.name}」的教材中未找到「$query」。请换更短或不同的关键词再试，或基于已有知识回答并说明教材中未检索到。"
            } else {
                "检索结果（来源：用户导入的教材，引用时说明出自《教材名》与页码）：\n$sb"
            }
        } catch (t: Throwable) {
            "教材检索失败：${t.message ?: "未知错误"}"
        }
    }

    /**
     * 每次对话注入的教材库上下文：
     * 列出各科教材，并按「是否与学生教材一致」给出不同的讲解口径。
     */
    private suspend fun textbookContext(): String {
        val books = textbookDao.observeAll().first()
        if (books.isEmpty()) return ""
        val subjects = subjectDao.observeAll().first().associate { it.id to it.name }
        return buildString {
            append("\n\n【用户教材库】用户已导入以下教材（可用 search_textbook 工具检索原文）：\n")
            books.forEach { b ->
                val name = subjects[b.subjectId] ?: "未知科目"
                val note = if (b.isExactMatch) {
                    "该教材与用户在校使用的教材完全一致：讲解请贴合其内容、术语、符号与章节顺序，可放心引用其页码与章节"
                } else {
                    "该教材仅供参考，可能与用户实际使用的教材版本不同：涉及章节、页码或具体编排时提醒用户以自己的教材为准"
                }
                append("- 科目：$name，《${b.title}》（约 ${b.charCount} 字）—— $note\n")
            }
        }
    }

    /**
     * 执行写笔记工具：按标题匹配已有笔记则追加到末尾，否则新建笔记。
     * 返回给模型的结果文本（让它向用户确认写到了哪篇笔记）。
     */
    private suspend fun executeNoteTool(argsJson: String): String {
        return try {
            val obj = Json.parseToJsonElement(argsJson).jsonObject
            val title = ((obj["title"] as? JsonPrimitive)?.content ?: "").trim().ifEmpty { "AI 笔记" }
            val content = ((obj["content"] as? JsonPrimitive)?.content ?: "").trim()
            if (content.isEmpty()) return "写入失败：content 为空。请提供要保存的内容。"
            val now = System.currentTimeMillis()
            val existing = noteDao.getByTitle(title)
            if (existing != null) {
                noteDao.update(
                    existing.copy(
                        contentMarkdown = existing.contentMarkdown.trimEnd('\n') + "\n\n" + content,
                        updatedAt = now
                    )
                )
                "已把内容追加到已有笔记《$title》末尾。"
            } else {
                noteDao.insert(NoteEntity(id = 0, title = title, contentMarkdown = content, updatedAt = now))
                "已创建新笔记《$title》并写入内容。"
            }
        } catch (t: Throwable) {
            "写入笔记失败：${t.message ?: "未知错误"}。请告知用户稍后重试。"
        }
    }
}