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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ueiol.yixue.data.db.QuizSummary
import com.ueiol.yixue.data.repo.QuizRepository
import com.ueiol.yixue.ui.common.YxCard
import com.ueiol.yixue.ui.common.YxEmptyState
import com.ueiol.yixue.ui.common.YxLargeTitle
import com.ueiol.yixue.ui.common.YxProgressBar
import com.ueiol.yixue.ui.common.formatShortTime
import com.ueiol.yixue.ui.theme.YxExt
import com.ueiol.yixue.ui.theme.YxRadius
import com.ueiol.yixue.YixueApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** 测验记录列表状态。 */
class QuizHistoryViewModel(repo: QuizRepository) : ViewModel() {
    val items: StateFlow<List<QuizSummary>> = repo.observeSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        @Composable
        fun create(): QuizHistoryViewModel = viewModel(factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as YixueApp
                QuizHistoryViewModel(app.container.quizRepository)
            }
        })
    }
}

/**
 * 测验记录：从对话里出过的每一套题都可以回到答题页（未交卷继续作答，已交卷可重做或看结果）。
 */
@Composable
fun QuizHistoryPage(
    onBack: () -> Unit,
    onOpenQuiz: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val vm = QuizHistoryViewModel.create()
    val items by vm.items.collectAsState()

    Column(modifier = modifier.fillMaxSize().navigationBarsPadding()) {
        YxLargeTitle(
            title = "测验记录",
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
        if (items.isEmpty()) {
            YxEmptyState(
                icon = Icons.AutoMirrored.Outlined.FactCheck,
                title = "还没有测验",
                hint = "在对话里让 AI 出一套题，答过的卷子会归档到这里",
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    QuizHistoryCard(item, onClick = { onOpenQuiz(item.id) })
                }
            }
        }
    }
}

@Composable
private fun QuizHistoryCard(item: QuizSummary, onClick: () -> Unit) {
    val graded = item.status == "GRADED"
    val progress = if (item.total == 0) 0f else item.answered.toFloat() / item.total
    YxCard(onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (graded) "已批改" else "进行中",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (graded) YxExt.success else MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .background(
                            if (graded) YxExt.successContainer else MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(YxRadius.Mini)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (graded) {
                    "答对 ${item.correct} / ${item.total} · 已答 ${item.answered}"
                } else {
                    "已答 ${item.answered} / ${item.total} 题"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            YxProgressBar(progress)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatShortTime(item.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (graded) "查看 / 重做" else "继续答题",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
