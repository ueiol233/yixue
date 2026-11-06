package com.ueiol.yixue.ui.quiz

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FactCheck
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
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ueiol.yixue.data.db.QuestionEntity
import com.ueiol.yixue.ui.common.YxCard
import com.ueiol.yixue.ui.common.YxEmptyState
import com.ueiol.yixue.ui.common.ExplainStreamDialog
import com.ueiol.yixue.ui.common.YxLargeTitle
import com.ueiol.yixue.ui.common.YxPill
import com.ueiol.yixue.ui.common.MarkdownBlock
import com.ueiol.yixue.ui.common.inlineMd
import com.ueiol.yixue.ui.theme.YxCardShape
import com.ueiol.yixue.ui.theme.YxExt
import com.ueiol.yixue.ui.theme.YxRadius

/** 结果页：得分、逐题对错、解析；错题可勾选待精讲。 */
@Composable
fun QuizResultPage(
    vm: QuizViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val quiz by vm.quiz.collectAsState()
    val questions by vm.questions.collectAsState()
    val records by vm.records.collectAsState()
    val selected = remember { mutableListOf<Long>().toMutableStateList() }

    val correct = questions.count { records[it.id]?.state == "LOCAL_OK" }
    val needReview = questions.count { records[it.id]?.state == "NEED_REVIEW" }
    val wrongs = questions.filter { records[it.id]?.state == "LOCAL_WRONG" || records[it.id]?.state == "NEED_REVIEW" }
    val reviewing by vm.reviewing.collectAsState()
    val reviewProgress by vm.reviewProgress.collectAsState()
    val reviewError by vm.reviewError.collectAsState()
    val explaining by vm.explaining.collectAsState()
    val explainStream by vm.explainStream.collectAsState()
    val explainProgress by vm.explainProgress.collectAsState()
    var showExplain by remember { mutableStateOf(false) }

    val headerActions: @Composable () -> Unit = {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
        }
    }

    // 没有可展示的测验结果时给空状态，避免标题下空白
    if (quiz == null || questions.isEmpty()) {
        Column(modifier = modifier.fillMaxSize()) {
            YxLargeTitle(title = "测试结果", actions = headerActions)
            YxEmptyState(
                icon = Icons.AutoMirrored.Outlined.FactCheck,
                title = "暂无测验结果",
                hint = "先生成一份试卷并完成作答",
                modifier = Modifier.weight(1f)
            )
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        YxLargeTitle(title = "测试结果", actions = headerActions)
        YxCard(modifier = Modifier.padding(horizontal = 16.dp)) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${quiz?.title ?: ""}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$correct", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
                    Text(" / ${questions.size}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (needReview > 0) {
                    Text("其中 $needReview 题待 AI 复核", style = MaterialTheme.typography.labelMedium, color = YxExt.warning)
                }
                if (wrongs.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    YxPill(
                        text = if (reviewing) reviewProgress.ifEmpty { "复核中…" } else "AI 复核并归档错题",
                        primary = true,
                        enabled = !reviewing,
                        height = 40.dp
                    ) { vm.reviewAndArchive() }
                }
                reviewError?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (wrongs.isNotEmpty()) {
            Text(
                "勾选错题让 AI 精讲（已选 ${selected.size}）",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                // 没有底部操作条时自己补手势条间距
                .then(if (selected.isEmpty()) Modifier.navigationBarsPadding() else Modifier),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(questions, key = { it.id }) { q ->
                ResultCard(
                    question = q,
                    record = records[q.id],
                    selected = q.id in selected,
                    onToggle = {
                        if (q.id in selected) selected.remove(q.id) else selected.add(q.id)
                    }
                )
            }
        }
        if (selected.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    YxPill(
                        text = if (explaining) "精讲生成中…" else "AI 精讲已选 ${selected.size} 题",
                        primary = true,
                        enabled = !reviewing && !explaining,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        showExplain = true
                        vm.explainSelected(selected.toList()) { }
                    }
                }
            }
        }
    }

    // AI 精讲：实时流式展示生成过程，结束后内容已归档到错题本
    if (showExplain) {
        ExplainStreamDialog(
            title = "AI 精讲",
            progress = explainProgress,
            text = explainStream,
            running = explaining,
            error = reviewError,
            onDismiss = { showExplain = false }
        )
    }
}
@Composable
private fun ResultCard(
    question: QuestionEntity,
    record: com.ueiol.yixue.data.db.AnswerRecordEntity?,
    selected: Boolean,
    onToggle: () -> Unit
) {
    val state = record?.state ?: "UNANSWERED"
    val correct = state == "LOCAL_OK"
    val needReview = state == "NEED_REVIEW"
    val accent = when {
        correct -> YxExt.success
        needReview -> YxExt.warning
        else -> MaterialTheme.colorScheme.error
    }
    val onAccent = when {
        correct -> YxExt.onSuccess
        needReview -> YxExt.onWarning
        else -> MaterialTheme.colorScheme.onError
    }
    YxCard {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(22.dp).background(accent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (correct) "√" else if (needReview) "?" else "×",
                        style = MaterialTheme.typography.labelLarge,
                        color = onAccent
                    )
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    if (correct) "答对" else if (needReview) "待 AI 复核" else "答错",
                    style = MaterialTheme.typography.labelLarge,
                    color = accent
                )
                Spacer(Modifier.weight(1f))
                if (!correct) {
                    Row(
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .clickable(onClick = onToggle)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(20.dp).background(
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                RoundedCornerShape(YxRadius.ExtraMini)
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) Text("✓", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                        }
                        Spacer(Modifier.size(6.dp))
                        Text("精讲", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            MarkdownBlock(text = question.stem)
            val myAnswer = record?.valueJson ?: "未作答"
            Text(
                inlineMd("我的答案：$myAnswer"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            val (_, accepted) = com.ueiol.yixue.domain.grading.GradingEngine.decodeAnswer(question.answerJson, question.type != "FILL_BLANK")
            val correctText = if (question.type == "FILL_BLANK") (accepted.firstOrNull() ?: "") else record?.let { "" } ?: ""
            val choiceAnswer = if (question.type != "FILL_BLANK") {
                com.ueiol.yixue.domain.grading.GradingEngine.decodeAnswer(question.answerJson, true).first.joinToString("")
            } else correctText
            Text(
                inlineMd("正确答案：$choiceAnswer"),
                style = MaterialTheme.typography.bodySmall,
                color = YxExt.success,
                modifier = Modifier.padding(top = 2.dp)
            )
            question.explanation?.let {
                Spacer(Modifier.height(6.dp))
                Text("解析", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                MarkdownBlock(text = it)
            }
        }
    }
}