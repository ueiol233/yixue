package com.ueiol.yixue.ui.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ueiol.yixue.data.db.AnswerRecordEntity
import com.ueiol.yixue.data.db.QuestionEntity
import com.ueiol.yixue.data.db.QuizEntity
import com.ueiol.yixue.data.repo.QuizRepository
import com.ueiol.yixue.domain.agent.QuizAgent
import com.ueiol.yixue.domain.grading.GradingEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 按题目集 ID 作用域创建 QuizViewModel。 */
@Composable
fun quizViewModel(quizId: Long): QuizViewModel =
    viewModel(
        key = "quiz$quizId",
        factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as com.ueiol.yixue.YixueApp
                QuizViewModel(
                    app.container.quizRepository,
                    app.container.quizAgent,
                    app.container.questionDao,
                    app.container.subjectDao,
                    app.container.wrongQuestionDao,
                    app.container.reviewAgent,
                    quizId
                )
            }
        }
    )

class QuizViewModel(
    private val quizRepo: QuizRepository,
    private val quizAgent: QuizAgent,
    private val questionDao: com.ueiol.yixue.data.db.QuestionDao,
    private val subjectDao: com.ueiol.yixue.data.db.SubjectDao,
    private val wrongQuestionDao: com.ueiol.yixue.data.db.WrongQuestionDao,
    private val reviewAgent: com.ueiol.yixue.domain.agent.ReviewAgent,
    quizId: Long
) : ViewModel() {

    val quiz: StateFlow<QuizEntity?> = quizRepo.observeQuiz(quizId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val questions: StateFlow<List<QuestionEntity>> = quizRepo.observeQuestions(quizId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _records = MutableStateFlow<Map<Long, AnswerRecordEntity>>(emptyMap())
    val records: StateFlow<Map<Long, AnswerRecordEntity>> = _records

    private val _adjusting = MutableStateFlow(false)
    val adjusting: StateFlow<Boolean> = _adjusting

    private val _adjustError = MutableStateFlow<String?>(null)
    val adjustError: StateFlow<String?> = _adjustError

    init {
        viewModelScope.launch {
            questions.collect { list ->
                _records.value = quizRepo.getRecords(list.map { it.id })
            }
        }
    }

    private val _explaining = MutableStateFlow(false)
    val explaining: StateFlow<Boolean> = _explaining

    /** 精讲流式输出（实时展示 AI 生成过程）。 */
    private val _explainStream = MutableStateFlow("")
    val explainStream: StateFlow<String> = _explainStream

    private val _explainProgress = MutableStateFlow("")
    val explainProgress: StateFlow<String> = _explainProgress

    /** 结果页勾选错题 → 归档 + AI 精讲（流式），完成后回调。 */
    fun explainSelected(questionIds: List<Long>, onDone: () -> Unit) {
        if (_explaining.value || questionIds.isEmpty()) return
        viewModelScope.launch {
            _explaining.value = true
            _reviewError.value = null
            try {
                val qs = questions.value
                val recs = _records.value
                val targets = questionIds.mapNotNull { id -> qs.firstOrNull { it.id == id } }
                targets.forEachIndexed { index, q ->
                    _explainProgress.value = "第 ${index + 1}/${targets.size} 题"
                    _explainStream.value = ""
                    val rec = recs[q.id]
                    val correct = com.ueiol.yixue.domain.grading.GradingEngine
                        .correctAnswerText(q.answerJson, q.type)
                    // 确保已归档（用科目名"未分类"，科目以错题本 AI 归档为准）；
                    // 直接用 insert 返回的 rowId，避免全表反查（并发下还可能挂错行）
                    val wqId = wrongQuestionDao.insert(
                        com.ueiol.yixue.data.db.WrongQuestionEntity(
                            questionId = q.id, subjectId = defaultSubjectId(), wrongAt = System.currentTimeMillis()
                        )
                    )
                    val report = reviewAgent.explainStream(
                        question = q.stem,
                        studentAnswer = rec?.valueJson ?: "未作答",
                        correctAnswer = correct,
                        mistakeTag = q.mistakeTag ?: "",
                        onDelta = { delta -> _explainStream.value = _explainStream.value + delta }
                    )
                    wrongQuestionDao.updateExplanationReport(wqId, report)
                }
                onDone()
            } catch (t: Throwable) {
                _reviewError.value = "精讲失败：${t.message?.take(120)}"
            } finally {
                _explaining.value = false
                _explainProgress.value = ""
            }
        }
    }

    private suspend fun defaultSubjectId(): Long {
        val name = "未分类"
        var s = subjectDao.getByName(name)
        if (s == null) {
            subjectDao.insert(com.ueiol.yixue.data.db.SubjectEntity(name = name, autoCreated = true))
            s = subjectDao.getByName(name)
        }
        return s?.id ?: 0L
    }

    fun answerValue(questionId: Long): String? = _records.value[questionId]?.valueJson

    private val _reviewing = MutableStateFlow(false)
    val reviewing: StateFlow<Boolean> = _reviewing

    private val _reviewProgress = MutableStateFlow("")
    val reviewProgress: StateFlow<String> = _reviewProgress

    private val _reviewError = MutableStateFlow<String?>(null)
    val reviewError: StateFlow<String?> = _reviewError

    /**
     * AI 复核 + 自动归档（规划书 3.3/3.5）：
     * 对本地判错/待复核的题目调用 AI；复核后错题按科目写入错题本。
     */
    fun reviewAndArchive() {
        if (_reviewing.value) return
        viewModelScope.launch {
            _reviewing.value = true
            _reviewError.value = null
            try {
                val qs = questions.value
                val recs = _records.value
                val toReview = qs.filter { recs[it.id]?.state == "LOCAL_WRONG" || recs[it.id]?.state == "NEED_REVIEW" }
                var failed = 0
                for ((idx, q) in toReview.withIndex()) {
                    _reviewProgress.value = "AI 复核 ${idx + 1}/${toReview.size}"
                    val rec = recs[q.id]
                    val options = q.optionsJson?.let {
                        runCatching { com.ueiol.yixue.domain.model.AppJson.decodeFromString(com.ueiol.yixue.domain.model.StringListSerializer, it) }
                            .getOrDefault(emptyList())
                    } ?: emptyList()
                    val correctText = com.ueiol.yixue.domain.grading.GradingEngine
                        .correctAnswerText(q.answerJson, q.type)
                    val result = try {
                        reviewAgent.review(q.stem, options, rec?.valueJson ?: "未作答", correctText, q.referenceAnswer ?: "")
                    } catch (t: Throwable) {
                        failed++
                        null
                    }
                    if (result == null) continue
                    val newState = if (result.verdict.equals("CORRECT", ignoreCase = true)) "AI_REVERSED" else "AI_WRONG"
                    quizRepo.updateVerdict(q.id, newState, result.reason)
                    questionDao.update(q.copy(mistakeTag = result.mistakeTag))
                    _records.value = _records.value + (q.id to com.ueiol.yixue.data.db.AnswerRecordEntity(
                        id = rec?.id ?: 0, questionId = q.id, valueJson = rec?.valueJson,
                        state = newState, aiReason = result.reason, updatedAt = System.currentTimeMillis()
                    ))
                    if (newState == "AI_WRONG") {
                        archiveWrong(q, result.subject)
                    }
                }
                if (failed > 0) _reviewError.value = "有 $failed 题复核失败（网络/接口问题），已保留本地判定"
            } finally {
                _reviewing.value = false
                _reviewProgress.value = ""
            }
        }
    }

    private suspend fun archiveWrong(q: com.ueiol.yixue.data.db.QuestionEntity, subjectName: String) {
        val name = subjectName.ifBlank { "未分类" }
        var subject = subjectDao.getByName(name)
        if (subject == null) {
            val count = subjectDao.count()
            subjectDao.insert(
                com.ueiol.yixue.data.db.SubjectEntity(
                    name = name, colorIndex = (count % 7), order = count, autoCreated = true
                )
            )
            subject = subjectDao.getByName(name)
        }
        val sid = subject?.id ?: return
        // 简单去重：同一题只归档一次
        val dup = wrongQuestionDao.observeActiveBySubject(sid).first().any { it.questionId == q.id }
        if (!dup) {
            wrongQuestionDao.insert(
                com.ueiol.yixue.data.db.WrongQuestionEntity(
                    questionId = q.id, subjectId = sid,
                    wrongAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun saveAnswer(questionId: Long, value: String) {
        viewModelScope.launch {
            quizRepo.saveAnswer(questionId, value)
            _records.value = _records.value + (questionId to AnswerRecordEntity(
                id = _records.value[questionId]?.id ?: 0,
                questionId = questionId, valueJson = value,
                state = _records.value[questionId]?.state ?: "UNANSWERED",
                aiReason = _records.value[questionId]?.aiReason,
                updatedAt = System.currentTimeMillis()
            ))
        }
    }

    fun answeredCount(): Int = _records.value.values.count { !it.valueJson.isNullOrBlank() }

    /** 交卷：本地判分（规划书 3.3），并回传一份可直接发给 AI 的答题报告。 */
    fun submit(onDone: (report: String) -> Unit) {
        viewModelScope.launch {
            val quizId = quiz.value?.id ?: return@launch
            val list = questions.value
            val recs = _records.value
            val verdicts = list.map { q ->
                val student = recs[q.id]?.valueJson
                val verdict = GradingEngine.grade(q.type, q.answerJson, student)
                val state = when (verdict) {
                    GradingEngine.Verdict.CORRECT -> "LOCAL_OK"
                    GradingEngine.Verdict.WRONG -> "LOCAL_WRONG"
                    GradingEngine.Verdict.UNCERTAIN -> "NEED_REVIEW"
                }
                quizRepo.updateVerdict(q.id, state, null)
                q to state
            }
            quizRepo.setStatus(quizId, "GRADED")
            // 判分状态要立刻反映到答题卡 / 结果页
            _records.value = quizRepo.getRecords(list.map { it.id })
            onDone(buildReport(verdicts))
        }
    }

    /** 生成交给 AI 讲评的报告：只带错题细节，避免消息过长。 */
    private fun buildReport(verdicts: List<Pair<QuestionEntity, String>>): String {
        val title = quiz.value?.title ?: "练习"
        val correct = verdicts.count { it.second == "LOCAL_OK" }
        val wrong = verdicts.withIndex().filter { it.value.second != "LOCAL_OK" }

        val sb = StringBuilder()
        sb.append("我刚交卷了：《$title》，共 ${verdicts.size} 题，答对 $correct 题。\n")
        if (wrong.isEmpty()) {
            sb.append("这次全对。请帮我总结这套题考的知识点，再给 2 道稍难的巩固题。")
            return sb.toString()
        }
        sb.append("错题/存疑：第 ${wrong.joinToString("、") { "${it.index + 1}" }} 题。\n\n")
        wrong.forEach { item ->
            val idx = item.index
            val q = item.value.first
            val state = item.value.second
            val correctText = GradingEngine.correctAnswerText(q.answerJson, q.type)
            val mine = _records.value[q.id]?.valueJson?.takeIf { it.isNotBlank() } ?: "未作答"
            sb.append("第 ${idx + 1} 题（${q.type}${if (state == "NEED_REVIEW") "，判分待复核" else ""}）\n")
            sb.append("题目：${q.stem.replace('\n', ' ').take(160)}\n")
            sb.append("我的答案：$mine｜正确答案：$correctText\n\n")
        }
        sb.append("请逐题讲清错因和正确思路，再针对这些薄弱点给 1-2 道同类巩固题。")
        return sb.toString()
    }

    /** 已交卷后重新作答：清空作答记录并回到进行中。 */
    fun resetForRedo(onDone: () -> Unit = {}) {
        val quizId = quiz.value?.id ?: return
        viewModelScope.launch {
            quizRepo.resetForRedo(quizId)
            _records.value = emptyMap()
            onDone()
        }
    }

    /** 答题卡内让 AI 调整题目（换题/加题/调难度），保留已作答记录。 */
    fun adjustQuestions(request: String) {
        val quizId = quiz.value?.id ?: return
        if (_adjusting.value) return
        viewModelScope.launch {
            _adjusting.value = true
            _adjustError.value = null
            try {
                val current = currentSpec()
                val adjusted = quizAgent.adjust(current, request)
                quizRepo.replaceQuestions(quizId, adjusted)
                // 题目变了，此前的交卷判定不再适用，回到进行中
                quizRepo.setStatus(quizId, "DOING")
            } catch (t: Throwable) {
                _adjustError.value = t.message ?: "调整失败"
            } finally {
                _adjusting.value = false
            }
        }
    }

    private fun currentSpec(): com.ueiol.yixue.domain.model.QuizSpec {        val qs = questions.value
        return com.ueiol.yixue.domain.model.QuizSpec(
            title = quiz.value?.title ?: "练习",
            subject = "",
            questions = qs.map { q ->
                val (letters, accepted) = GradingEngine.decodeAnswer(q.answerJson, q.type != "FILL_BLANK")
                com.ueiol.yixue.domain.model.QuestionSpec(
                    type = when (q.type) {
                        "SINGLE_CHOICE" -> "single"
                        "MULTIPLE_CHOICE" -> "multiple"
                        else -> "fill"
                    },
                    stem = q.stem,
                    options = q.optionsJson?.let {
                        runCatching {
                            com.ueiol.yixue.domain.model.AppJson.decodeFromString(
                                com.ueiol.yixue.domain.model.StringListSerializer, it
                            )
                        }.getOrDefault(emptyList())
                    } ?: emptyList(),
                    answer = if (letters.isNotEmpty()) letters.joinToString("") else (accepted.firstOrNull() ?: ""),
                    acceptedAnswers = accepted,
                    referenceAnswer = q.referenceAnswer ?: "",
                    explanation = q.explanation ?: "",
                    plotExpressions = q.plotSpecJson?.let {
                        runCatching {
                            com.ueiol.yixue.domain.model.AppJson.decodeFromString(
                                com.ueiol.yixue.domain.model.StringListSerializer, it
                            )
                        }.getOrDefault(emptyList())
                    } ?: emptyList()
                )
            }
        )
    }
}