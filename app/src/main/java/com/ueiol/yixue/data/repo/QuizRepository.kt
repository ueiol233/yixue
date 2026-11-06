package com.ueiol.yixue.data.repo

import com.ueiol.yixue.data.db.AnswerRecordDao
import com.ueiol.yixue.data.db.AnswerRecordEntity
import com.ueiol.yixue.data.db.QuestionDao
import com.ueiol.yixue.data.db.QuestionEntity
import com.ueiol.yixue.data.db.QuizDao
import com.ueiol.yixue.data.db.QuizEntity
import com.ueiol.yixue.domain.grading.GradingEngine
import com.ueiol.yixue.domain.model.AppJson
import com.ueiol.yixue.domain.model.QuizSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class QuizRepository(
    private val quizDao: QuizDao,
    private val questionDao: QuestionDao,
    private val recordDao: AnswerRecordDao,
    // Room 事务运行器（AppContainer 注入），保证多步写库的原子性
    private val runInTransaction: suspend (suspend () -> Unit) -> Unit
) {
    companion object {
        fun entityType(specType: String) = when (specType) {
            "single" -> "SINGLE_CHOICE"
            "multiple" -> "MULTIPLE_CHOICE"
            else -> "FILL_BLANK"
        }
    }

    fun observeQuiz(id: Long): Flow<QuizEntity?> = quizDao.observeById(id)

    /** 全部测验记录（我的 → 测验记录）。 */
    fun observeSummaries(): Flow<List<com.ueiol.yixue.data.db.QuizSummary>> = quizDao.observeSummaries()

    fun observeQuestions(quizId: Long): Flow<List<QuestionEntity>> = questionDao.observeByQuiz(quizId)

    suspend fun getQuestions(quizId: Long): List<QuestionEntity> = questionDao.observeByQuiz(quizId).first()

    suspend fun getRecords(questionIds: List<Long>): Map<Long, AnswerRecordEntity> =
        recordDao.getByQuestionIds(questionIds).associateBy { it.questionId }

    suspend fun setStatus(quizId: Long, status: String) = quizDao.updateStatus(quizId, status)

    /** 重新作答：清空本卷作答记录，状态回到进行中。 */
    suspend fun resetForRedo(quizId: Long) {
        val ids = getQuestions(quizId).map { it.id }
        if (ids.isNotEmpty()) recordDao.deleteByQuestionIds(ids)
        quizDao.updateStatus(quizId, "DOING")
    }

    suspend fun createFromSpec(sessionId: Long?, spec: QuizSpec): Long {
        var quizId = 0L
        runInTransaction {
            quizId = quizDao.insert(
                QuizEntity(
                    sessionId = sessionId ?: 0,
                    title = spec.title.ifBlank { "练习" },
                    status = "DOING",
                    createdAt = System.currentTimeMillis()
                )
            )
            insertQuestions(quizId, spec)
        }
        return quizId
    }

    /** 调整题目：按题干匹配保留已作答记录（整段一个事务）。 */
    suspend fun replaceQuestions(quizId: Long, spec: QuizSpec) {
        runInTransaction {
            val old = getQuestions(quizId)
            val oldByStem = old.associateBy { it.stem }
            // 一次性取旧作答记录，避免逐条 N+1
            val oldRecords = if (old.isEmpty()) emptyMap<Long, AnswerRecordEntity>()
            else recordDao.getByQuestionIds(old.map { it.id }).associateBy { it.questionId }
            questionDao.deleteByQuiz(quizId)
            insertQuestions(quizId, spec)
            // 恢复作答
            val newQuestions = getQuestions(quizId)
            newQuestions.forEach { q ->
                val oldQ = oldByStem[q.stem] ?: return@forEach
                val rec = oldRecords[oldQ.id] ?: return@forEach
                recordDao.insert(rec.copy(id = 0, questionId = q.id))
            }
        }
    }

    /** 错题重练：把给定题目复制成一张新卷（重置作答状态），返回卷 id。 */
    suspend fun createRetryQuiz(sessionId: Long?, title: String, questions: List<QuestionEntity>): Long {
        var quizId = 0L
        runInTransaction {
            quizId = quizDao.insert(
                QuizEntity(
                    sessionId = sessionId ?: 0,
                    title = title,
                    status = "DOING",
                    createdAt = System.currentTimeMillis()
                )
            )
            questionDao.insertAll(
                questions.mapIndexed { idx, q ->
                    // 复制题面与答案，id 归零落到新卷下
                    q.copy(id = 0, quizId = quizId, order = idx)
                }
            )
        }
        return quizId
    }

    private suspend fun insertQuestions(quizId: Long, spec: QuizSpec) {        val entities = spec.questions.mapIndexed { idx, q ->
            QuestionEntity(
                quizId = quizId,
                order = idx,
                type = entityType(q.type),
                stem = q.stem,
                optionsJson = if (q.isChoice) AppJson.encodeToString(
                    com.ueiol.yixue.domain.model.StringListSerializer,
                    q.options
                ) else null,
                plotSpecJson = if (q.plotExpressions.isEmpty()) null else AppJson.encodeToString(
                    com.ueiol.yixue.domain.model.StringListSerializer,
                    q.plotExpressions
                ),
                answerJson = if (q.isChoice) GradingEngine.encodeChoice(q.answer.map { it.toString() })
                else GradingEngine.encodeFill(q.answer, q.acceptedAnswers),
                referenceAnswer = q.referenceAnswer.ifBlank { null },
                explanation = q.explanation.ifBlank { null }
            )
        }
        questionDao.insertAll(entities)
    }

    /** 保存作答（进度持久化）。 */
    suspend fun saveAnswer(questionId: Long, value: String) {
        val existing = recordDao.getByQuestionId(questionId)
        val rec = existing?.copy(valueJson = value, updatedAt = System.currentTimeMillis())
            ?: AnswerRecordEntity(
                questionId = questionId,
                valueJson = value,
                state = "UNANSWERED",
                updatedAt = System.currentTimeMillis()
            )
        recordDao.insert(rec)
    }

    suspend fun updateVerdict(questionId: Long, state: String, aiReason: String?) {
        val existing = recordDao.getByQuestionId(questionId)
        if (existing != null) {
            recordDao.update(existing.copy(state = state, aiReason = aiReason, updatedAt = System.currentTimeMillis()))
        } else {
            recordDao.insert(
                AnswerRecordEntity(
                    questionId = questionId, valueJson = null, state = state,
                    aiReason = aiReason, updatedAt = System.currentTimeMillis()
                )
            )
        }
    }
}