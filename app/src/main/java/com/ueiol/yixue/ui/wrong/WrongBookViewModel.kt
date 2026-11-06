package com.ueiol.yixue.ui.wrong

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.Composable
import com.ueiol.yixue.YixueApp
import com.ueiol.yixue.data.db.AnswerRecordEntity
import com.ueiol.yixue.data.db.QuestionDao
import com.ueiol.yixue.data.db.QuestionEntity
import com.ueiol.yixue.data.db.SubjectDao
import com.ueiol.yixue.data.db.SubjectEntity
import com.ueiol.yixue.data.db.WrongQuestionDao
import com.ueiol.yixue.data.db.WrongQuestionEntity
import com.ueiol.yixue.data.repo.QuizRepository
import com.ueiol.yixue.domain.agent.ReviewAgent
import com.ueiol.yixue.ui.common.formatShortTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WrongItem(
    val wrong: WrongQuestionEntity,
    val question: QuestionEntity?,
    val record: AnswerRecordEntity?
)

class WrongBookViewModel(
    private val subjectDao: SubjectDao,
    private val wrongQuestionDao: WrongQuestionDao,
    private val questionDao: QuestionDao,
    private val recordDao: com.ueiol.yixue.data.db.AnswerRecordDao,
    private val reviewAgent: ReviewAgent,
    private val quizRepository: QuizRepository
) : ViewModel() {

    val subjects: StateFlow<List<SubjectEntity>> = subjectDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedSubjectId = MutableStateFlow<Long?>(null)
    val selectedSubjectId: StateFlow<Long?> = _selectedSubjectId

    // ---------- 复习提醒（到期 = 错后满 1 天且未掌握） ----------

    private val _dueOnly = MutableStateFlow(false)
    val dueOnly: StateFlow<Boolean> = _dueOnly

    fun toggleDueOnly() { _dueOnly.value = !_dueOnly.value }

    /** 全科目范围的待复习数（不受科目筛选影响），驱动「今日待复习」提示条。 */
    val dueCount: StateFlow<Int> = wrongQuestionDao.observeActive()
        .map { list ->
            val now = System.currentTimeMillis()
            list.count { now - it.wrongAt >= DUE_AFTER_MS }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val wrongItems: StateFlow<List<WrongItem>> = combine(_selectedSubjectId, _dueOnly) { sid, due ->
        sid to due
    }
        .flatMapLatest { (sid, due) ->
            if (sid == null) wrongQuestionDao.observeActive()
            else wrongQuestionDao.observeActiveBySubject(sid)
        }
        .map { list ->
            val now = System.currentTimeMillis()
            // 只看到期项（复习模式）
            val items = if (_dueOnly.value) list.filter { now - it.wrongAt >= DUE_AFTER_MS } else list
            // 批量取题面与作答记录，避免逐条 N+1
            val qIds = items.map { it.questionId }.distinct()
            val questions = if (qIds.isEmpty()) emptyMap()
            else questionDao.getByIds(qIds).associateBy { it.id }
            val records = if (qIds.isEmpty()) emptyMap()
            else recordDao.getByQuestionIds(qIds).associateBy { it.questionId }
            items.map { wq ->
                WrongItem(wq, questions[wq.questionId], records[wq.questionId])
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun selectSubject(id: Long?) { _selectedSubjectId.value = id }

    /** 批量删除错题记录（只删记录，原题目与试卷保留）。 */
    fun deleteSelected(items: List<WrongItem>) {
        val ids = items.map { it.wrong.id }
        if (ids.isEmpty()) return
        viewModelScope.launch { wrongQuestionDao.deleteByIds(ids) }
    }

    // ---------- 错题重练 ----------

    private val _buildingQuiz = MutableStateFlow(false)
    val buildingQuiz: StateFlow<Boolean> = _buildingQuiz

    /** 重练卷单次最多题数，避免一卷过长。 */
    val retryLimit = RETRY_LIMIT

    /** 把当前列表里的未掌握错题复制成一张新卷，完成后回调卷 id（跳答题页）。 */
    fun buildRetryQuiz(onReady: (Long) -> Unit) {
        if (_buildingQuiz.value) return
        viewModelScope.launch {
            _buildingQuiz.value = true
            try {
                val list = wrongItems.value.filter { it.question != null }.take(RETRY_LIMIT)
                if (list.isEmpty()) return@launch
                // 卷挂在错题来源的会话下（交卷报告回传对话页用）；无来源则挂空会话
                val sessionId = list.firstNotNullOfOrNull { it.wrong.sessionId } ?: 0L
                val title = "错题重练 · " + formatShortTime(System.currentTimeMillis())
                val quizId = quizRepository.createRetryQuiz(
                    sessionId = sessionId,
                    title = title,
                    questions = list.mapNotNull { it.question }
                )
                onReady(quizId)
            } finally {
                _buildingQuiz.value = false
            }
        }
    }

    fun markMastered(item: WrongItem) {
        viewModelScope.launch { wrongQuestionDao.setMastered(item.wrong.id, true) }
    }

    private val _explaining = MutableStateFlow(false)
    val explaining: StateFlow<Boolean> = _explaining

    private val _explainError = MutableStateFlow<String?>(null)
    val explainError: StateFlow<String?> = _explainError

    /** 精讲流式输出（实时展示 AI 生成过程）。 */
    private val _explainStream = MutableStateFlow("")
    val explainStream: StateFlow<String> = _explainStream

    private val _explainProgress = MutableStateFlow("")
    val explainProgress: StateFlow<String> = _explainProgress

    /** 勾选错题批量 AI 精讲（流式），结果写入错题记录。 */
    fun explain(items: List<WrongItem>, onDone: (List<Long>) -> Unit) {
        if (_explaining.value || items.isEmpty()) return
        viewModelScope.launch {
            _explaining.value = true
            _explainError.value = null
            val done = mutableListOf<Long>()
            try {
                items.forEachIndexed { index, item ->
                    val q = item.question ?: return@forEachIndexed
                    _explainProgress.value = "第 ${index + 1}/${items.size} 题"
                    _explainStream.value = ""
                    val correct = com.ueiol.yixue.domain.grading.GradingEngine
                        .correctAnswerText(q.answerJson, q.type)
                    val report = reviewAgent.explainStream(
                        question = q.stem,
                        studentAnswer = item.record?.valueJson ?: "未作答",
                        correctAnswer = correct,
                        mistakeTag = q.mistakeTag ?: "",
                        onDelta = { delta -> _explainStream.value = _explainStream.value + delta }
                    )
                    wrongQuestionDao.updateExplanationReport(item.wrong.id, report)
                    done.add(item.wrong.id)
                }
            } catch (t: Throwable) {
                _explainError.value = "精讲失败：${t.message?.take(120)}"
            } finally {
                _explaining.value = false
                _explainProgress.value = ""
            }
            if (done.isNotEmpty()) onDone(done)
        }
    }

    companion object {
        const val RETRY_LIMIT = 20

        /** 复习到期间隔：错后 1 天（v1 简化版，无多轮间隔表）。 */
        const val DUE_AFTER_MS = 24L * 60 * 60 * 1000

        @Composable
        fun create(): WrongBookViewModel = viewModel(factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as YixueApp
                val c = app.container
                WrongBookViewModel(c.subjectDao, c.wrongQuestionDao, c.questionDao, c.answerRecordDao, c.reviewAgent, c.quizRepository)
            }
        })
    }
}