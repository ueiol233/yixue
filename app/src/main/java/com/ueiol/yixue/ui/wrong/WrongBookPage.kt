package com.ueiol.yixue.ui.wrong

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ueiol.yixue.ui.common.ExplainStreamDialog
import com.ueiol.yixue.ui.common.MarkdownBlock
import com.ueiol.yixue.ui.common.formatShortTime
import com.ueiol.yixue.ui.common.inlineMd
import com.ueiol.yixue.ui.common.YxCard
import com.ueiol.yixue.ui.common.YxConfirmDialog
import com.ueiol.yixue.ui.common.YxEmptyState
import com.ueiol.yixue.ui.common.YxLargeTitle
import com.ueiol.yixue.ui.common.YxPill
import com.ueiol.yixue.ui.theme.YxCardShape
import com.ueiol.yixue.ui.theme.YxExt
import com.ueiol.yixue.ui.theme.YxRadius
import com.ueiol.yixue.ui.theme.YxSheetShape

/** 错题本：按科目分组、勾选精讲、标记掌握、错题重练。 */
@Composable
fun WrongBookPage(
    vm: WrongBookViewModel,
    onOpenQuiz: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val subjects by vm.subjects.collectAsState()
    val items by vm.wrongItems.collectAsState()
    val selectedSubject by vm.selectedSubjectId.collectAsState()
    val explaining by vm.explaining.collectAsState()
    val explainError by vm.explainError.collectAsState()
    val explainStream by vm.explainStream.collectAsState()
    val explainProgress by vm.explainProgress.collectAsState()
    val buildingQuiz by vm.buildingQuiz.collectAsState()
    val dueCount by vm.dueCount.collectAsState()
    val dueOnly by vm.dueOnly.collectAsState()
    val context = LocalContext.current
    // 点进复习模式时顺带申请通知权限（API 33+），让每日提醒能送达
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    fun ensureNotifPermission(ctx: android.content.Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val selected = remember { mutableListOf<Long>().toMutableStateList() }
    var reportFor by remember { mutableStateOf<Long?>(null) }
    var showExplain by remember { mutableStateOf(false) }
    var confirmDeleteWrong by remember { mutableStateOf(false) }
    val subjectPalette = YxExt.subjectPalette
    // 预建索引，避免每个卡片组合时两次 O(n) 查找
    val subjectById = remember(subjects) { subjects.associateBy { it.id } }

    Column(modifier = modifier.fillMaxSize()) {
        YxLargeTitle(title = "错题本", actions = {
            if (items.isNotEmpty()) {
                YxPill(
                    text = if (buildingQuiz) "组卷中…" else "重练(${items.size.coerceAtMost(vm.retryLimit)})",
                    primary = selected.isEmpty(),
                    enabled = !buildingQuiz,
                    height = 40.dp
                ) {
                    vm.buildRetryQuiz(onOpenQuiz)
                }
            }
            if (selected.isNotEmpty()) {
                YxPill(
                    text = "删除（${selected.size}）",
                    enabled = !explaining,
                    height = 40.dp,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    confirmDeleteWrong = true
                }
                YxPill(
                    text = if (explaining) "精讲中…" else "精讲(${selected.size})",
                    primary = true,
                    enabled = !explaining,
                    height = 40.dp
                ) {
                    val chosen = items.filter { it.wrong.id in selected }
                    showExplain = true
                    vm.explain(chosen) {
                        selected.clear()
                    }
                }
            }
        })
        // 复习提醒条：有待复习错题时显示，点击进入「只看到期」模式
        if (dueCount > 0) {
            Surface(
                color = if (dueOnly) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(YxRadius.Medium),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable {
                        if (!dueOnly) ensureNotifPermission(context)
                        vm.toggleDueOnly()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Alarm,
                        contentDescription = null,
                        tint = if (dueOnly) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (dueOnly) "正在看待复习 · 共 $dueCount 道，点击返回全部"
                        else "今日待复习 $dueCount 道（错后 1 天起），点击查看",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (dueOnly) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        // 科目筛选
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(selected = selectedSubject == null, onClick = { vm.selectSubject(null) }, label = { Text("全部") })
            }
            items(subjects, key = { it.id }) { s ->
                FilterChip(
                    selected = selectedSubject == s.id,
                    onClick = { vm.selectSubject(s.id) },
                    label = { Text(s.name) },
                    leadingIcon = {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(subjectPalette[s.colorIndex.mod(subjectPalette.size)], CircleShape)
                        )
                    }
                )
            }
        }
        explainError?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        if (items.isEmpty()) {
            YxEmptyState(
                icon = Icons.AutoMirrored.Outlined.FactCheck,
                title = "还没有错题",
                hint = "完成一次测验后，错题会自动归档到这里",
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items, key = { it.wrong.id }) { item ->
                    WrongCard(
                        item = item,
                        subjectColor = subjectPalette[
                            (subjectById[item.wrong.subjectId]?.colorIndex ?: 0).mod(subjectPalette.size)
                        ],
                        subjectName = subjectById[item.wrong.subjectId]?.name ?: "",
                        selected = item.wrong.id in selected,
                        onSelect = { if (item.wrong.id in selected) selected.remove(item.wrong.id) else selected.add(item.wrong.id) },
                                onOpen = { reportFor = item.wrong.id },
                        onMastered = { vm.markMastered(item); selected.remove(item.wrong.id) }
                    )
                }
            }
        }
    }

    // AI 精讲：实时流式展示生成过程
    if (showExplain) {
        ExplainStreamDialog(
            title = "AI 精讲",
            progress = explainProgress,
            text = explainStream,
            running = explaining,
            error = explainError,
            onDismiss = { showExplain = false }
        )
    }

    // 批量删除确认：错题记录删除后无法撤销，需要明确确认
    if (confirmDeleteWrong) {
        YxConfirmDialog(
            title = "删除所选错题？",
            text = "只从错题本移除这 ${selected.size} 道题的记录，原题目、试卷与作答记录不受影响。",
            confirmText = "删除",
            danger = true,
            onDismiss = { confirmDeleteWrong = false },
            onConfirm = {
                confirmDeleteWrong = false
                vm.deleteSelected(items.filter { it.wrong.id in selected })
                selected.clear()
            }
        )
    }

    // 精讲报告 / 错题详情：Yx 大圆角弹层，标题 + 滚动正文 + 关闭胶囊
    reportFor?.let { wid ->
        val item = items.firstOrNull { it.wrong.id == wid }
        if (item != null) {
            Dialog(onDismissRequest = { reportFor = null }) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = YxSheetShape,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "错题详情",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(10.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                        ) {
                            item.question?.let { MarkdownBlock(text = it.stem) }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                inlineMd("我的答案：${item.record?.valueJson ?: "未作答"}"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            item.question?.let { q ->
                                Text(
                                    inlineMd("正确答案：${com.ueiol.yixue.domain.grading.GradingEngine.correctAnswerText(q.answerJson, q.type)}"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = YxExt.success
                                )
                            }
                            item.question?.aiReason0(item.record)?.let {
                                Text(inlineMd(it), style = MaterialTheme.typography.labelMedium, color = YxExt.warning)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("AI 精讲", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            val report = item.wrong.explanationReport
                            if (report.isNullOrBlank()) {
                                Text("尚未生成。勾选此题并点击顶部「精讲」按钮。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                MarkdownBlock(text = report)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        YxPill(
                            text = "关闭",
                            height = 40.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) { reportFor = null }
                    }
                }
            }
        }
    }
}

private fun com.ueiol.yixue.data.db.QuestionEntity.aiReason0(record: com.ueiol.yixue.data.db.AnswerRecordEntity?): String? =
    record?.aiReason?.let { "复核意见：$it" }
@Composable
private fun WrongCard(
    item: WrongItem,
    subjectColor: androidx.compose.ui.graphics.Color,
    subjectName: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
    onMastered: () -> Unit
) {
    
    val q = item.question
    YxCard(
        onClick = onOpen,
        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(subjectColor, CircleShape))
                Spacer(Modifier.size(6.dp))
                Text(subjectName, style = MaterialTheme.typography.labelMedium, color = subjectColor)
                item.question?.mistakeTag?.let { tag ->
                    Spacer(Modifier.size(8.dp))
                    Text(tag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(YxRadius.ExtraMini)).padding(horizontal = 6.dp, vertical = 2.dp))
                }
                if (item.record?.state == "AI_REVERSED") {
                    Spacer(Modifier.size(8.dp))
                    Text("AI 改判", style = MaterialTheme.typography.labelSmall, color = YxExt.success)
                }
                Spacer(Modifier.weight(1f))
                Text(formatShortTime(item.wrong.wrongAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                q?.stem ?: "题目已删除",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                YxPill(
                    text = if (selected) "取消选择" else "选择精讲",
                    height = 40.dp,
                    onClick = onSelect
                )
                YxPill(text = "已掌握", height = 40.dp, onClick = onMastered)
            }
        }
    }
}