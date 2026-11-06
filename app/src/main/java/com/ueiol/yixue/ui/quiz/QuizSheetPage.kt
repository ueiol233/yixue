package com.ueiol.yixue.ui.quiz

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ueiol.yixue.ui.common.YxProgressBar
import com.ueiol.yixue.ui.common.YxConfirmDialog
import com.ueiol.yixue.ui.common.YxInputDialog
import com.ueiol.yixue.data.db.QuestionEntity
import com.ueiol.yixue.ui.common.YxProgressBar
import com.ueiol.yixue.ui.common.YxConfirmDialog
import com.ueiol.yixue.ui.common.YxInputDialog
import com.ueiol.yixue.ui.common.YxEmptyState
import com.ueiol.yixue.ui.common.YxProgressBar
import com.ueiol.yixue.ui.common.YxConfirmDialog
import com.ueiol.yixue.ui.common.YxInputDialog
import com.ueiol.yixue.ui.common.YxLargeTitle
import com.ueiol.yixue.ui.common.YxProgressBar
import com.ueiol.yixue.ui.common.YxConfirmDialog
import com.ueiol.yixue.ui.common.YxInputDialog
import com.ueiol.yixue.ui.common.YxPill
import com.ueiol.yixue.ui.common.YxProgressBar
import com.ueiol.yixue.ui.common.YxConfirmDialog
import com.ueiol.yixue.ui.common.YxInputDialog
import com.ueiol.yixue.ui.common.MarkdownBlock
import com.ueiol.yixue.ui.common.YxProgressBar
import com.ueiol.yixue.ui.common.YxConfirmDialog
import com.ueiol.yixue.ui.common.YxInputDialog
import com.ueiol.yixue.ui.formula.FormulaEditorDialog
import com.ueiol.yixue.ui.common.YxProgressBar
import com.ueiol.yixue.ui.common.YxConfirmDialog
import com.ueiol.yixue.ui.common.YxInputDialog
import com.ueiol.yixue.ui.theme.YxCardShape
import com.ueiol.yixue.ui.common.YxProgressBar
import com.ueiol.yixue.ui.common.YxConfirmDialog
import com.ueiol.yixue.ui.common.YxInputDialog
import com.ueiol.yixue.ui.theme.YxRadius
import com.ueiol.yixue.ui.common.YxProgressBar
import com.ueiol.yixue.ui.common.YxConfirmDialog
import com.ueiol.yixue.ui.common.YxInputDialog
import com.ueiol.yixue.ui.theme.YxSheetShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 答题卡：逐题作答、进度保存、答题卡导航、AI 调题。 */
@Composable
fun QuizSheetPage(
    vm: QuizViewModel,
    onBack: () -> Unit,
    onSubmit: (sessionId: Long, report: String) -> Unit,
    onOpenResult: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val quiz by vm.quiz.collectAsState()
    val questions by vm.questions.collectAsState()
    val records by vm.records.collectAsState()
    val adjusting by vm.adjusting.collectAsState()
    val adjustError by vm.adjustError.collectAsState()

    var index by rememberSaveable { mutableIntStateOf(0) }
    var showCard by remember { mutableStateOf(false) }
    var showAdjust by remember { mutableStateOf(false) }
    var showSubmitConfirm by remember { mutableStateOf(false) }
    var showRedoPrompt by remember { mutableStateOf(false) }
    var askedForGraded by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // 单选自动翻页任务：保留引用以便连续点击/手动切题时取消
    var advanceJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val q = questions.getOrNull(index.coerceIn(0, (questions.size - 1).coerceAtLeast(0)))
    val answered = records.values.count { !it.valueJson.isNullOrBlank() }
    val correctCount = records.values.count { it.state == "LOCAL_OK" || it.state == "AI_REVERSED" }
    val graded = quiz?.status == "GRADED"

    // 已交卷的卷子再次进入：询问重新作答或查看结果
    LaunchedEffect(graded) {
        if (graded && !askedForGraded) {
            askedForGraded = true
            showRedoPrompt = true
        } else if (!graded) {
            askedForGraded = false
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Yx 大标题：标题 + 右侧动作（返回在最右，与其它二级页一致）
        YxLargeTitle(
            title = "答题卡",
            actions = {
                IconButton(onClick = { showAdjust = true }) {
                    Icon(Icons.Outlined.AutoFixHigh, contentDescription = "调整题目", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showCard = true }) {
                    Icon(Icons.Outlined.Apps, contentDescription = "答题卡", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "退出", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        )

        // 试卷名 + 进度
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = quiz?.title ?: "练习",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "第 ${(index + 1).coerceAtMost(questions.size)} / ${questions.size} 题",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = "已答 $answered",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(10.dp))
        // Yx 胶囊进度条
        val progressTarget = if (questions.isEmpty()) 0f else answered.toFloat() / questions.size
        val progress by animateFloatAsState(progressTarget, label = "quiz-progress")
        YxProgressBar(
            progress,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(10.dp))

        if (q != null) {
            QuestionBody(
                question = q,
                value = records[q.id]?.valueJson,
                onAnswer = { vm.saveAnswer(q.id, it) },
                // 单选题选完自动翻到下一题（多选题保持原地继续勾选）
                onSingleSelected = {
                    if (index < questions.size - 1) {
                        val expected = index
                        val target = expected + 1
                        advanceJob?.cancel()
                        advanceJob = scope.launch {
                            delay(220)
                            // 期间如果手动切题，就不要再跳
                            if (index == expected) index = target
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )
        } else {
            YxEmptyState(
                icon = Icons.AutoMirrored.Outlined.FactCheck,
                title = "题目还没准备好",
                hint = "稍等片刻，或返回重新生成练习",
                modifier = Modifier.weight(1f)
            )
        }

        // 底部操作条（Yx 胶囊按钮，避开手势条）
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    // 键盘弹出时只补「ime - 手势条」，避免和 navigationBarsPadding 叠加出空白
                    .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                YxPill(
                    text = "上一题",
                    primary = false,
                    enabled = index > 0,
                    modifier = Modifier.weight(1f)
                ) { if (index > 0) index-- }
                YxPill(
                    text = "下一题",
                    primary = false,
                    enabled = index < questions.size - 1,
                    modifier = Modifier.weight(1f)
                ) { if (index < questions.size - 1) index++ }
                YxPill(
                    text = "交卷",
                    primary = true,
                    enabled = questions.isNotEmpty(),
                    modifier = Modifier.weight(1.1f)
                ) { showSubmitConfirm = true }
            }
        }
    }

    if (showCard) {
        CardNavigatorDialog(
            title = quiz?.title ?: "答题卡",
            total = questions.size,
            answered = questions.mapIndexedNotNull { i, item ->
                i.takeIf { !records[item.id]?.valueJson.isNullOrBlank() }
            }.toSet(),
            current = index,
            correct = correctCount,
            graded = graded,
            onJump = { index = it; showCard = false },
            onSubmit = { showCard = false; showSubmitConfirm = true },
            onViewResult = { showCard = false; onOpenResult() },
            onDismiss = { showCard = false }
        )
    }

    // 交卷确认：提示未答数量，避免误交
    if (showSubmitConfirm) {
        val unanswered = questions.size - answered
        YxConfirmDialog(
            title = "确认交卷？",
            text = if (unanswered > 0) {
                "还有 $unanswered 题未作答。交卷后自动判分，之后可在「我的 → 测验记录」里重做。"
            } else {
                "共 ${questions.size} 题，交卷后自动判分。"
            },
            confirmText = "交卷",
            cancelText = "继续作答",
            onDismiss = { showSubmitConfirm = false },
            onConfirm = {
                showSubmitConfirm = false
                val sid = quiz?.sessionId ?: 0L
                vm.submit { report -> onSubmit(sid, report) }
            }
        )
    }

    // 已交卷再次进入：询问重做或看结果
    if (showRedoPrompt) {
        YxConfirmDialog(
            title = "这套题已交卷",
            text = "上次答对 $correctCount / ${questions.size} 题。重新作答会清空已有答案。",
            confirmText = "重新作答",
            cancelText = "查看结果",
            onDismiss = { showRedoPrompt = false; onOpenResult() },
            onConfirm = {
                showRedoPrompt = false
                index = 0
                vm.resetForRedo()
            }
        )
    }
    if (showAdjust) {
        var adjustText by remember { mutableStateOf("") }
        YxInputDialog(
            title = "让 AI 调整题目",
            value = adjustText,
            onValueChange = { adjustText = it },
            placeholder = "输入调整要求",
            hint = "例如：把第 2 题换掉 / 再出 3 道同类题 / 降低难度。已作答的记录会保留。",
            confirmText = "发送",
            confirmEnabled = !adjusting && adjustText.isNotBlank(),
            onDismiss = { if (!adjusting) showAdjust = false },
            onConfirm = { vm.adjustQuestions(adjustText) },
            extraContent = {
                if (adjusting) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                        Text("正在调整…", style = MaterialTheme.typography.labelMedium)
                    }
                }
                adjustError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        )
    }
}
@Composable
private fun QuestionBody(
    question: QuestionEntity,
    value: String?,
    onAnswer: (String) -> Unit,
    onSingleSelected: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = YxCardShape,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = typeName(question.type),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(YxRadius.Mini)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    MarkdownBlock(text = question.stem)
                }
            }
        }
        // 题目配图
        question.plotSpecJson?.let { plotJson ->
            item { PlotQuestionCard(questionId = question.id, plotJson = plotJson) }
        }
        when {
            question.type == "SINGLE_CHOICE" -> item {
                OptionList(
                    question = question,
                    value = value,
                    multi = false,
                    onAnswer = onAnswer,
                    onSingleSelected = onSingleSelected
                )
            }
            question.type == "MULTIPLE_CHOICE" -> {
                val selected = value.orEmpty().split("").filter { it.isNotBlank() }.toSet()
                item {
                    Column {
                        OptionList(question, value, multi = true, onAnswer = onAnswer)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "多选题：可选多项，选择后自动保存",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> item {
                FillBlankInput(value = value.orEmpty(), onAnswer = onAnswer)
            }
        }
    }
}

private fun typeName(type: String) = when (type) {
    "SINGLE_CHOICE" -> "单选题"
    "MULTIPLE_CHOICE" -> "多选题"
    else -> "填空题"
}

@Composable
private fun PlotQuestionCard(questionId: Long, plotJson: String) {
    val spec = remember(questionId, plotJson) {
        runCatching {
            com.ueiol.yixue.domain.model.AppJson.decodeFromString(
                com.ueiol.yixue.domain.model.StringListSerializer, plotJson
            )
        }.getOrNull()?.let { com.ueiol.yixue.ui.common.PlotParser.parseLines(it) }
    }
    if (spec != null) {
        val context = LocalContext.current
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = YxCardShape,
            modifier = Modifier.clickable {
                context.startActivity(com.ueiol.yixue.ui.plot.PlotViewerActivity.intent(context, spec))
            }
        ) {
            Box(Modifier.fillMaxWidth().height(220.dp).padding(4.dp)) {
                com.ueiol.yixue.ui.common.FunctionPlot(spec, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun OptionList(
    question: QuestionEntity,
    value: String?,
    multi: Boolean,
    onAnswer: (String) -> Unit,
    onSingleSelected: (() -> Unit)? = null
) {
    val options = remember(question.id) {
        question.optionsJson?.let {
            runCatching {
                com.ueiol.yixue.domain.model.AppJson.decodeFromString(
                    com.ueiol.yixue.domain.model.StringListSerializer, it
                )
            }.getOrDefault(emptyList())
        } ?: emptyList()
    }
    val selected = value.orEmpty().filter { it.isLetter() }.map { it.uppercase() }.toSet()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { opt ->
            val letter = opt.trim().takeWhile { it.isLetter() }.uppercase().ifEmpty { "?" }
            val isSelected = letter in selected
            Surface(
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = YxCardShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isSelected) Modifier.border(YxRadius.StrokeWidth, MaterialTheme.colorScheme.primary, YxCardShape)
                        else Modifier
                    )
                    .clickable {
                        if (multi) {
                            // 多选题：只调整选择，不自动翻页
                            val next = if (isSelected) selected - letter else selected + letter
                            onAnswer(next.sorted().joinToString(""))
                        } else {
                            onAnswer(letter)
                            onSingleSelected?.invoke()
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = letter,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Box(Modifier.weight(1f)) {
                        MarkdownBlock(text = opt.substringAfterFirstDot())
                    }
                }
            }
        }
    }
}

private fun String.substringAfterFirstDot(): String {
    val idx = indexOf('.')
    return if (idx > 0 && idx < 3) substring(idx + 1).trim() else this
}

@Composable
private fun CardNavigatorDialog(
    title: String,
    total: Int,
    answered: Set<Int>,
    current: Int,
    correct: Int,
    graded: Boolean,
    onJump: (Int) -> Unit,
    onSubmit: () -> Unit,
    onViewResult: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = YxSheetShape,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "答题卡",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (graded) {
                                "$title · 答对 $correct / $total"
                            } else {
                                "$title · 已答 ${answered.size} / $total"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                // 卡片式题号：每格是一张小卡片，当前题描边、已答填充
                (0 until total).chunked(5).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { i ->
                            val isCurrent = i == current
                            val isAnswered = i in answered
                            val cellBg = when {
                                isCurrent -> MaterialTheme.colorScheme.primary
                                isAnswered -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceContainerLow
                            }
                            val cellFg = when {
                                isCurrent -> MaterialTheme.colorScheme.onPrimary
                                isAnswered -> MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .background(cellBg, RoundedCornerShape(YxRadius.Small))
                                    .then(
                                        if (isCurrent) Modifier.border(
                                            YxRadius.StrokeWidth,
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(YxRadius.Small)
                                        ) else Modifier
                                    )
                                    .clickable { onJump(i) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${i + 1}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = cellFg
                                )
                            }
                        }
                        repeat(5 - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(8.dp))
                YxPill(
                    text = if (graded) "查看批改结果" else "交卷",
                    primary = true,
                    enabled = graded || total > 0,
                    modifier = Modifier.fillMaxWidth()
                ) { if (graded) onViewResult() else onSubmit() }
            }
        }
    }
}

@Composable
private fun FillBlankInput(value: String, onAnswer: (String) -> Unit) {
    var field by remember {
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(value))
    }
    // 只有外部值真的变了（重新作答 / 恢复答案）才覆盖输入框，避免打字时光标跳到末尾
    LaunchedEffect(value) {
        if (value != field.text) {
            field = androidx.compose.ui.text.input.TextFieldValue(value)
        }
    }
    var showFormulaDialog by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    Column {
        OutlinedTextField(
            value = field,
            onValueChange = {
                field = it
                onAnswer(it.text)
            },
            placeholder = { Text("输入答案，可点下方按钮插入公式") },
            shape = RoundedCornerShape(YxRadius.Medium),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            YxPill(
                text = "∫ 插入公式",
                primary = false,
                modifier = Modifier.weight(1f)
            ) {
                focusManager.clearFocus()
                showFormulaDialog = true
            }
            YxPill(
                text = "记录答案",
                primary = true,
                enabled = field.text.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) { onAnswer(field.text) }
        }
        if (showFormulaDialog) {
            FormulaEditorDialog(
                onDismiss = { showFormulaDialog = false },
                onConfirm = { latex ->
                    val sel = field.selection
                    val start = minOf(sel.start, sel.end).coerceIn(0, field.text.length)
                    val insert = "$" + latex + "$"
                    val newText = field.text.substring(0, start) + insert + field.text.substring(start)
                    field = field.copy(
                        text = newText,
                        selection = androidx.compose.ui.text.TextRange(start + insert.length)
                    )
                    onAnswer(newText)
                }
            )
        }
    }
}