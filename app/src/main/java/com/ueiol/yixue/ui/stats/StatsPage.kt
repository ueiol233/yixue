package com.ueiol.yixue.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ueiol.yixue.YixueApp
import com.ueiol.yixue.data.db.AnswerRecordDao
import com.ueiol.yixue.data.db.DayCount
import com.ueiol.yixue.data.db.QuizDao
import com.ueiol.yixue.data.db.SubjectWrongCount
import com.ueiol.yixue.data.db.WrongQuestionDao
import com.ueiol.yixue.ui.common.YxCard
import com.ueiol.yixue.ui.common.YxEmptyState
import com.ueiol.yixue.ui.common.YxLargeTitle
import com.ueiol.yixue.ui.theme.YxExt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 统计窗口天数。 */
private const val STATS_DAYS = 14

/** 一次加载的学习统计快照。 */
data class StatsData(
    val gradedCount: Int,
    val answered: Int,
    val correct: Int,
    val wrongActive: Int,
    /** 近 14 天每日答题量（key = yyyy-MM-dd），缺的天 UI 补零。 */
    val daily: List<DayCount>,
    val subjectWrong: List<SubjectWrongCount>
)

class StatsViewModel(
    private val quizDao: QuizDao,
    private val answerRecordDao: AnswerRecordDao,
    private val wrongQuestionDao: WrongQuestionDao
) : ViewModel() {

    private val _data = MutableStateFlow<StatsData?>(null)
    val data: StateFlow<StatsData?> = _data

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val sinceMs = System.currentTimeMillis() - (STATS_DAYS - 1) * 86_400_000L
            _data.value = StatsData(
                gradedCount = quizDao.gradedCount(),
                answered = answerRecordDao.answeredInGraded(),
                correct = answerRecordDao.correctInGraded(),
                wrongActive = wrongQuestionDao.observeActive().first().size,
                daily = answerRecordDao.dailyAnswered(sinceMs),
                subjectWrong = wrongQuestionDao.activeCountBySubject()
            )
        }
    }

    companion object {
        @Composable
        fun create(): StatsViewModel = viewModel(factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as YixueApp
                val c = app.container
                StatsViewModel(c.quizDao, c.answerRecordDao, c.wrongQuestionDao)
            }
        })
    }
}

/**
 * 学习统计：总览（交卷/答题/正确率/未掌握错题）+ 近 14 天答题量柱状 + 未掌握错题科目分布。
 * 数据全部来自现有表的聚合查询，无需额外表。
 */
@Composable
fun StatsPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vm = StatsViewModel.create()
    val data by vm.data.collectAsState()

    Column(modifier = modifier.fillMaxSize().navigationBarsPadding()) {
        YxLargeTitle(
            title = "学习统计",
            actions = {
                IconButton(onClick = { vm.refresh() }) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = "刷新",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        )
        when {
            data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> {
                val d = data!!
                if (d.answered == 0 && d.wrongActive == 0) {
                    YxEmptyState(
                        icon = Icons.AutoMirrored.Outlined.FactCheck,
                        title = "还没有学习数据",
                        hint = "在对话里让 AI 出一套题，答题记录会统计在这里",
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item { OverviewCard(d) }
                        item { DailyCard(d) }
                        if (d.subjectWrong.isNotEmpty()) item { SubjectCard(d) }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewCard(d: StatsData) {
    val accuracy = if (d.answered == 0) 0 else (d.correct * 100 / d.answered)
    YxCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth()) {
                StatCell("已交卷", "${d.gradedCount} 卷", Modifier.weight(1f))
                StatCell("累计答题", "${d.answered} 题", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                StatCell("正确率", "$accuracy%", Modifier.weight(1f))
                StatCell("未掌握错题", "${d.wrongActive} 道", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DailyCard(d: StatsData) {
    val keyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val labelFmt = SimpleDateFormat("MM-dd", Locale.getDefault())
    val days = remember(STATS_DAYS) {
        (0 until STATS_DAYS).map { offset ->
            val t = System.currentTimeMillis() - (STATS_DAYS - 1 - offset) * 86_400_000L
            keyFmt.format(Date(t))
        }
    }
    val countByDay = remember(d.daily) { d.daily.associate { it.day to it.cnt } }
    val maxCnt = (countByDay.values.maxOrNull() ?: 0).coerceAtLeast(1)
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh

    YxCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "近 14 天答题量",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))
            Canvas(modifier = Modifier.fillMaxWidth().height(96.dp)) {
                val slot = size.width / STATS_DAYS
                days.forEachIndexed { i, key ->
                    // 底部浅色轨道，让「0 题」的天也有占位
                    drawRoundRect(
                        trackColor,
                        topLeft = Offset(i * slot + slot * 0.18f, size.height - 3f),
                        size = Size(slot * 0.64f, 3f),
                        cornerRadius = CornerRadius(2f, 2f)
                    )
                    val cnt = countByDay[key] ?: 0
                    if (cnt > 0) {
                        val barH = (cnt.toFloat() / maxCnt) * (size.height - 10f)
                        drawRoundRect(
                            barColor,
                            topLeft = Offset(i * slot + slot * 0.18f, size.height - barH),
                            size = Size(slot * 0.64f, barH),
                            cornerRadius = CornerRadius(4f, 4f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    labelFmt.format(keyFmt.parse(days.first())!!),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(
                    labelFmt.format(keyFmt.parse(days.last())!!),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SubjectCard(d: StatsData) {
    val palette = YxExt.subjectPalette
    val maxCnt = d.subjectWrong.maxOf { it.cnt }.coerceAtLeast(1)
    YxCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "未掌握错题 · 科目分布",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            d.subjectWrong.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(palette[s.colorIndex.mod(palette.size)], CircleShape)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        s.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(width = 72.dp, height = 20.dp)
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(10.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(5.dp))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(s.cnt.toFloat() / maxCnt)
                                .height(10.dp)
                                .background(
                                    palette[s.colorIndex.mod(palette.size)],
                                    RoundedCornerShape(5.dp)
                                )
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "${s.cnt}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
