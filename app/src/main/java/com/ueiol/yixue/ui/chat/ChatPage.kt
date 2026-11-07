package com.ueiol.yixue.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.window.Dialog
import com.ueiol.yixue.ui.common.YxPill
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ueiol.yixue.data.db.MessageEntity
import com.ueiol.yixue.data.db.QuizSummary
import com.ueiol.yixue.ui.common.YxEmptyState
import com.ueiol.yixue.ui.common.FunctionPlot
import com.ueiol.yixue.ui.common.PlotParser
import com.ueiol.yixue.ui.common.PlotInputDialog
import com.ueiol.yixue.ui.common.YxActionChip
import com.ueiol.yixue.ui.common.YxLargeTitle
import com.ueiol.yixue.ui.common.MarkdownBlock
import com.ueiol.yixue.ui.formula.FormulaEditorDialog
import com.ueiol.yixue.ui.navigation.LocalScaffoldPadding
import com.ueiol.yixue.ui.plot.PlotViewerActivity
import com.ueiol.yixue.ui.theme.YxSheetShape
import com.ueiol.yixue.ui.theme.YxRadius
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.io.File

/** 对话页：消息流 + Agent 流式回复 + 工具面板输入栏。 */
@Composable
fun ChatPage(
    vm: ChatViewModel,
    onOpenSessions: () -> Unit,
    onOpenQuiz: (Long) -> Unit,
    onOpenResult: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val messages by vm.messages.collectAsState(initial = emptyList())
    val streamingText by vm.streamingText.collectAsState()
    val error by vm.error.collectAsState()
    val toolActivity by vm.toolActivity.collectAsState()
    val quizSummaries by vm.quizSummaries.collectAsState()
    val currentSessionId by vm.currentSessionId.collectAsState()
    val listState = rememberLazyListState()
    // 进入/切换会话后强制滚到底一次；之后交给「不抢用户滚动」逻辑
    var jumpedForSession by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(currentSessionId) { jumpedForSession = null }
    // 只在内容条数变化（新消息/工具行/流式气泡出现消失）时触发，不随每个 token 重启
    LaunchedEffect(messages.size, toolActivity, streamingText != null) {
        val count = messages.size +
            (if (streamingText != null && toolActivity == null) 1 else 0) +
            (if (toolActivity != null) 1 else 0)
        if (count <= 0) return@LaunchedEffect
        // scrollToItem(index) 只把该项顶端对齐视口顶；带上大 scrollOffset 会在测量阶段
        // 被 clamp 到列表最大滚动位置，即最后一项的底部贴住视口底——一次调用直达底部
        if (jumpedForSession == null && messages.isNotEmpty()) {
            listState.scrollToItem(count - 1, 1_000_000)
            jumpedForSession = currentSessionId
            return@LaunchedEffect
        }
        // 用户手动上翻时不要抢滚动；流式过程中用 scrollTo 避免长表格反复动画抖动
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (lastVisible >= count - 2) {
            listState.scrollToItem(count - 1, 1_000_000)
        }
    }
    // 流式 token 增长时的跟随滚动：snapshotFlow 只在长度变化时发射
    LaunchedEffect(Unit) {
        snapshotFlow { streamingText?.length ?: 0 }.collectLatest {
            if (streamingText == null) return@collectLatest
            val count = messages.size +
                (if (toolActivity == null) 1 else 0)
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (lastVisible >= count - 2) {
                listState.scrollToItem(count - 1, 1_000_000)
            }
        }
    }

    // 全局搜索跳转：滚到指定消息并短暂高亮（延迟清除避免常驻色块）
    val scrollTarget by vm.scrollToMessage.collectAsState()
    var highlightMessageId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(scrollTarget, messages) {
        val target = scrollTarget ?: return@LaunchedEffect
        val idx = messages.indexOfFirst { it.id == target }
        if (idx >= 0) {
            listState.scrollToItem(idx)
            highlightMessageId = target
            vm.consumeScrollToMessage()
            delay(2500)
            highlightMessageId = null
        }
    }

    // 底部 Tab 栏间距已由外层 Scaffold/NavHost 扣掉，这里只补「键盘高度 - 已扣掉的底部间距」，
    // 否则键盘收起时会多出一段空白
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val reservedBottom = LocalScaffoldPadding.current.calculateBottomPadding()
    val extraBottom = (imeBottom - reservedBottom).coerceAtLeast(0.dp)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = extraBottom)
    ) {
        YxLargeTitle(title = "易学", actions = {
            IconButton(onClick = onOpenSessions) {
                Icon(Icons.Outlined.History, contentDescription = "历史会话", tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = { vm.newChat() }) {
                Icon(Icons.Outlined.Add, contentDescription = "新对话", tint = MaterialTheme.colorScheme.onSurface)
            }
        })
        error?.let { ErrorBanner(text = it, onDismiss = { vm.dismissError() }) }
        Box(modifier = Modifier.weight(1f)) {
            if (messages.isEmpty() && streamingText == null) {
                YxEmptyState(
                    icon = Icons.Outlined.Functions,
                    title = "开始新的对话",
                    hint = "向 AI 提问，说「给我出几道二次函数题」它就会自动出题",
                    modifier = Modifier.fillMaxSize()
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val highlighted = msg.id == highlightMessageId
                    Box(
                        modifier = if (highlighted) Modifier.background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(12.dp)
                        ) else Modifier
                    ) {
                        MessageBubble(
                            msg = msg,
                            quiz = msg.quizId?.let { quizSummaries[it] },
                            onOpenQuiz = onOpenQuiz,
                            onOpenResult = onOpenResult
                        )
                    }
                }
                toolActivity?.let { activity ->
                    item(key = "tool") { ToolActivityRow(activity) }
                }
                // 工具执行中先不显示空的流式气泡，避免和工具状态重复
                streamingText?.takeIf { toolActivity == null }?.let { partial ->
                    item(key = "streaming") { AssistantBubble(partial.ifEmpty { "……" }, streaming = true) }
                }
            }
        }
        ChatInputBar(
            vm = vm,
            enabled = streamingText == null,
            onSend = { text -> vm.send(text) }
        )
    }
}
@Composable
private fun ErrorBanner(text: String, onDismiss: () -> Unit) {
    Surface(
        onClick = onDismiss,
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(YxRadius.Mini),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun MessageBubble(
    msg: MessageEntity,
    quiz: QuizSummary?,
    onOpenQuiz: (Long) -> Unit,
    onOpenResult: (Long) -> Unit = {}
) {
    // 解析图片附件
    val images = remember(msg.id) {
        msg.attachmentsJson?.let {
            runCatching {
                com.ueiol.yixue.domain.model.AppJson.decodeFromString(com.ueiol.yixue.domain.model.StringListSerializer, it)
            }.getOrDefault(emptyList())
        } ?: emptyList()
    }
    if (msg.role == "user") {
        UserBubble(msg.content, images)
    } else {
        AssistantBubble(
            text = msg.content,
            streaming = false,
            quizId = msg.quizId,
            quiz = quiz,
            onOpenQuiz = onOpenQuiz,
            onOpenResult = onOpenResult
        )
    }
}

/** Agent 工具执行中：让用户看到「正在生成练习」而不是干等。 */
@Composable
private fun ToolActivityRow(activity: ToolActivity) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(YxRadius.Heavy)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.size(8.dp))
                Icon(
                    Icons.Outlined.Functions,
                    contentDescription = activity.name,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = activity.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 对话中的练习卡片：显示试卷进度，点击进入答题 / 查看结果。 */
@Composable
private fun QuizMessageCard(quizId: Long, quiz: QuizSummary?, onClick: () -> Unit) {
    val graded = quiz?.status == "GRADED"
    val total = quiz?.total ?: 0
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(YxRadius.Medium),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Functions,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = quiz?.title ?: "练习",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = when {
                        quiz == null -> "试卷准备中…"
                        graded -> "$total 题 · 已批改 · 答对 ${quiz.correct}"
                        quiz.answered == 0 -> "$total 题 · 尚未作答"
                        else -> "$total 题 · 已答 ${quiz.answered}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = if (graded) "看结果" else "去答题",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun UserBubble(text: String, images: List<String> = emptyList()) {
    // 用户消息里可能带自己插入的 ```plot 代码块：抽出来渲染成函数图，其余文本正常显示
    val plotSpecs = remember(text) {
        Regex("```plot\\s*\\n([\\s\\S]*?)```").findAll(text)
            .mapNotNull { PlotParser.parseLines(it.groupValues[1].lines()) }
            .toList()
    }
    val plainText = remember(text) {
        text.replace(Regex("```plot\\s*\\n[\\s\\S]*?```"), "").trim()
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(
                topStart = YxRadius.Medium, topEnd = YxRadius.Medium,
                bottomStart = YxRadius.Medium, bottomEnd = YxRadius.ExtraMini
            ),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                images.forEach { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = "图片",
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    )
                }
                val context = LocalContext.current
                plotSpecs.forEach { spec ->
                    FunctionPlot(
                        spec = spec,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clickable { context.startActivity(PlotViewerActivity.intent(context, spec)) }
                    )
                }
                if (plainText.isNotBlank()) {
                    Text(plainText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun AssistantBubble(
    text: String,
    streaming: Boolean,
    quizId: Long? = null,
    quiz: QuizSummary? = null,
    onOpenQuiz: (Long) -> Unit = {},
    onOpenResult: (Long) -> Unit = {}
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(
                topStart = YxRadius.Medium, topEnd = YxRadius.Medium,
                bottomStart = YxRadius.ExtraMini, bottomEnd = YxRadius.Medium
            ),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                // 流式期间渲染纯文本：MarkdownParser 是整段重解析，每个 token 都跑一遍 O(n²)；
                // 回复完成后整段走 MarkdownBlock 渲染
                if (streaming) {
                    Text(
                        text = text + " ▍",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    MarkdownBlock(text = text)
                }
                if (streaming && text.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
                // 练习卡片放进气泡内部，宽度与气泡内容区一致
                if (quizId != null) {
                    Spacer(Modifier.height(8.dp))
                    QuizMessageCard(quizId = quizId, quiz = quiz, onClick = {
                        if (quiz?.status == "GRADED") onOpenResult(quizId) else onOpenQuiz(quizId)
                    })
                }
            }
        }
    }
}


/** 输入栏：左侧 + 弹出工具面板（图片/公式）。 */
@Composable
private fun ChatInputBar(
    vm: ChatViewModel,
    enabled: Boolean,
    onSend: (String) -> Unit
) {
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var showPanel by remember { mutableStateOf(false) }
    var showFormulaDialog by remember { mutableStateOf(false) }
    var showPlotDialog by remember { mutableStateOf(false) }
    var showModelSwitcher by remember { mutableStateOf(false) }
    val pendingImage by vm.pendingImage.collectAsState()
    val pendingReport by vm.pendingReport.collectAsState()
    val focusManager = LocalFocusManager.current

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.attachImage(uri)
    }

    fun insertAtCursor(insert: String) {
        val sel = field.selection
        val start = sel.start.coerceAtMost(sel.end).coerceAtMost(field.text.length)
        val newText = field.text.substring(0, start) + insert + field.text.substring(field.text.length.coerceAtMost(start))
        val cursor = (start + insert.length).coerceAtMost(newText.length)
        field = field.copy(text = newText, selection = TextRange(cursor))
    }

    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 交卷报告以“附件”形式挂在输入框上方，可移除，发送时自动并入消息
            pendingReport?.let { report ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Functions,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "答题报告",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = report.lineSequence().firstOrNull()?.take(32)?.plus("…") ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    IconButton(onClick = { vm.clearPendingReport() }) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "移除答题报告",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            pendingImage?.let { url ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(model = url, contentDescription = "待发送图片", modifier = Modifier.size(56.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("已附加图片", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { vm.clearPendingImage() }) {
                        Icon(Icons.Outlined.Close, contentDescription = "移除图片", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // + 工具面板入口
                IconButton(onClick = { showPanel = !showPanel }) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = "工具",
                        tint = if (showPanel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(YxRadius.Heavy),
                    modifier = Modifier.weight(1f)
                ) {
                    BasicTextField(
                        value = field,
                        onValueChange = { field = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.heightIn(min = 40.dp, max = 120.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        decorationBox = { inner ->
                            Box {
                                if (field.text.isEmpty()) {
                                    Text("有什么可以帮你？", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                inner()
                            }
                        }
                    )
                }
                Spacer(Modifier.size(8.dp))
                val canSend = enabled && (field.text.isNotBlank() || pendingReport != null)
                IconButton(
                    onClick = { onSend(field.text); field = TextFieldValue("") },
                    enabled = canSend
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "发送",
                        tint = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // + 弹出的工具面板
            if (showPanel) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    YxActionChip(icon = Icons.Outlined.Image, text = "图片") {
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        showPanel = false
                    }
                    YxActionChip(icon = Icons.Outlined.Functions, text = "公式") {
                        focusManager.clearFocus()
                        showFormulaDialog = true
                        showPanel = false
                    }
                    YxActionChip(icon = Icons.AutoMirrored.Outlined.ShowChart, text = "函数图") {
                        focusManager.clearFocus()
                        showPlotDialog = true
                        showPanel = false
                    }
                    YxActionChip(icon = Icons.Outlined.Tune, text = "模型") {
                        focusManager.clearFocus()
                        showModelSwitcher = true
                        showPanel = false
                    }
                }
            }
        }
    }

    if (showFormulaDialog) {
        FormulaEditorDialog(
            onDismiss = { showFormulaDialog = false },
            onConfirm = { latex -> insertAtCursor("$" + latex + "$") }
        )
    }

    if (showPlotDialog) {
        PlotInputDialog(
            onDismiss = { showPlotDialog = false },
            onInsert = { md ->
                // 追加到输入框末尾，用户可以再补一句话一起发送
                val base = field.text.trimEnd('\n')
                field = field.copy(
                    text = if (base.isEmpty()) md else "$base\n\n$md",
                    selection = TextRange(base.length + md.length)
                )
            }
        )
    }

    // 主页快速切换模型：按接口分组列出各自的模型库，点选即切换生效
    if (showModelSwitcher) {
        val store = vm.settingsStore
        val s by store.settings.collectAsState()
        Dialog(onDismissRequest = { showModelSwitcher = false }) {
            Surface(color = MaterialTheme.colorScheme.surface, shape = YxSheetShape, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("切换模型", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    val active = s.activeProvider
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (active == null) "还没有添加接口，先到「我的 → AI 服务设置」添加"
                        else "当前：${active.name} · ${s.model}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    if (s.providers.isNotEmpty()) {
                        LazyColumn(modifier = Modifier.heightIn(max = 460.dp)) {
                            s.providers.forEach { p ->
                                item(key = "head_${p.id}") {
                                    Text(
                                        p.name + if (p.id == s.activeProviderId) " · 当前" else "",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                                    )
                                }
                                items(p.models.sortedBy { it != p.selectedModel }, key = { "${p.id}_$it" }) { m ->
                                    val chosen = m == p.selectedModel && p.id == s.activeProviderId
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                store.setActiveModel(p.id, m)
                                                showModelSwitcher = false
                                            }
                                            .heightIn(min = 44.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            m,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (chosen) {
                                            Icon(Icons.Outlined.Check, contentDescription = "当前", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    YxPill(text = "关闭", height = 40.dp, modifier = Modifier.fillMaxWidth()) { showModelSwitcher = false }
                }
            }
        }
    }
}