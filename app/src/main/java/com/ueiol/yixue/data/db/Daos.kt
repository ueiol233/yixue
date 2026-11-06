package com.ueiol.yixue.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** LIKE 查询的用户输入转义：\ % _ 前加反斜杠，配合 SQL 的 ESCAPE '\' 使用。 */
fun escapeLike(q: String): String =
    q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY `order` ASC, id ASC")
    fun observeAll(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects WHERE id = :id")
    suspend fun getById(id: Long): SubjectEntity?

    @Query("SELECT * FROM subjects WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): SubjectEntity?

    @Query("SELECT * FROM subjects")
    suspend fun getAll(): List<SubjectEntity>

    @Query("DELETE FROM subjects")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subject: SubjectEntity): Long

    @Update
    suspend fun update(subject: SubjectEntity)

    @Delete
    suspend fun delete(subject: SubjectEntity)

    @Query("SELECT COUNT(*) FROM subjects")
    suspend fun count(): Int
}

@Dao
interface SessionDao {
    // 置顶优先，其余按更新时间倒序
    @Query("SELECT * FROM sessions ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    /** 只列有消息的会话：空会话不保存、不展示。 */
    @Query(
        """
        SELECT * FROM sessions s
        WHERE EXISTS (SELECT 1 FROM messages m WHERE m.sessionId = s.id)
        ORDER BY s.pinned DESC, s.updatedAt DESC
        """
    )
    fun observeAllNonEmpty(): Flow<List<SessionEntity>>

    /** 清理历史遗留的空会话。 */
    @Query("DELETE FROM sessions WHERE NOT EXISTS (SELECT 1 FROM messages m WHERE m.sessionId = sessions.id)")
    suspend fun deleteEmpty(): Int

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions")
    suspend fun getAll(): List<SessionEntity>

    @Query("DELETE FROM sessions")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Query("UPDATE sessions SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("UPDATE sessions SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Delete
    suspend fun delete(session: SessionEntity)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt ASC, id ASC")
    fun observeBySession(sessionId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages")
    suspend fun getAll(): List<MessageEntity>

    @Query("DELETE FROM messages")
    suspend fun clearAll()

    /** 全局搜索：消息正文 LIKE，联会话取标题。 */
    @Query(
        """
        SELECT m.id AS id, m.sessionId AS sessionId, s.title AS sessionTitle,
            m.role AS role, m.content AS content, m.createdAt AS createdAt
        FROM messages m JOIN sessions s ON s.id = m.sessionId
        WHERE m.content LIKE '%' || :q || '%' ESCAPE '\'
        ORDER BY m.createdAt DESC
        LIMIT :limit
        """
    )
    suspend fun search(q: String, limit: Int = 20): List<MessageHit>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Delete
    suspend fun delete(message: MessageEntity)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: Long)
}

@Dao
interface QuizDao {
    @Query("SELECT * FROM quizzes WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    fun observeBySession(sessionId: Long): Flow<List<QuizEntity>>

    @Query("SELECT * FROM quizzes WHERE id = :id")
    fun observeById(id: Long): Flow<QuizEntity?>

    @Query("SELECT * FROM quizzes WHERE id = :id")
    suspend fun getById(id: Long): QuizEntity?

    @Query("SELECT * FROM quizzes")
    suspend fun getAll(): List<QuizEntity>

    @Query("DELETE FROM quizzes")
    suspend fun clearAll()

    /** 测验记录：每卷的题量、已答数、答对数。 */
    @Query(
        """
        SELECT q.id AS id, q.title AS title, q.status AS status, q.createdAt AS createdAt,
            (SELECT COUNT(*) FROM questions qs WHERE qs.quizId = q.id) AS total,
            (SELECT COUNT(*) FROM questions qs
                JOIN answer_records r ON r.questionId = qs.id
                WHERE qs.quizId = q.id AND r.valueJson IS NOT NULL AND r.valueJson <> '') AS answered,
            (SELECT COUNT(*) FROM questions qs
                JOIN answer_records r ON r.questionId = qs.id
                WHERE qs.quizId = q.id AND r.state IN ('LOCAL_OK', 'AI_REVERSED')) AS correct
        FROM quizzes q
        WHERE q.status <> 'GENERATING'
        ORDER BY q.createdAt DESC
        """
    )
    fun observeSummaries(): Flow<List<QuizSummary>>

    @Query("UPDATE quizzes SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("SELECT COUNT(*) FROM quizzes WHERE status = 'GRADED'")
    suspend fun gradedCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(quiz: QuizEntity): Long

    @Delete
    suspend fun delete(quiz: QuizEntity)
}

@Dao
interface QuestionDao {
    @Query("SELECT * FROM questions WHERE quizId = :quizId ORDER BY `order` ASC")
    fun observeByQuiz(quizId: Long): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun getById(id: Long): QuestionEntity?

    @Query("SELECT * FROM questions WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<QuestionEntity>

    @Query("SELECT * FROM questions")
    suspend fun getAll(): List<QuestionEntity>

    @Query("DELETE FROM questions")
    suspend fun clearAll()

    /** 全局搜索：题干 LIKE，排除生成中的卷子。 */
    @Query(
        """
        SELECT qs.id AS id, qs.quizId AS quizId, qs.stem AS stem
        FROM questions qs JOIN quizzes q ON q.id = qs.quizId
        WHERE qs.stem LIKE '%' || :q || '%' ESCAPE '\' AND q.status <> 'GENERATING'
        ORDER BY qs.id DESC
        LIMIT :limit
        """
    )
    suspend fun searchStems(q: String, limit: Int = 20): List<QuestionHit>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(questions: List<QuestionEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(question: QuestionEntity): Long

    @Update
    suspend fun update(question: QuestionEntity)

    @Delete
    suspend fun delete(question: QuestionEntity)

    @Query("DELETE FROM questions WHERE quizId = :quizId")
    suspend fun deleteByQuiz(quizId: Long)
}

@Dao
interface AnswerRecordDao {
    @Query("SELECT * FROM answer_records WHERE questionId = :questionId")
    suspend fun getByQuestionId(questionId: Long): AnswerRecordEntity?

    @Query("SELECT * FROM answer_records")
    suspend fun getAll(): List<AnswerRecordEntity>

    @Query("DELETE FROM answer_records")
    suspend fun clearAll()

    /** 已交卷测验中的累计答题数（排除未作答）。 */
    @Query(
        """
        SELECT COUNT(*) FROM answer_records r
        JOIN questions qs ON qs.id = r.questionId
        JOIN quizzes q ON q.id = qs.quizId
        WHERE q.status = 'GRADED' AND r.valueJson IS NOT NULL AND r.valueJson <> ''
        """
    )
    suspend fun answeredInGraded(): Int

    /** 已交卷测验中的累计答对数（本地判对或 AI 复核改判对）。 */
    @Query(
        """
        SELECT COUNT(*) FROM answer_records r
        JOIN questions qs ON qs.id = r.questionId
        JOIN quizzes q ON q.id = qs.quizId
        WHERE q.status = 'GRADED' AND r.valueJson IS NOT NULL AND r.valueJson <> ''
            AND r.state IN ('LOCAL_OK', 'AI_REVERSED')
        """
    )
    suspend fun correctInGraded(): Int

    /** 近 N 天每日答题量（按本地日聚合，仅已交卷）。 */
    @Query(
        """
        SELECT date(r.updatedAt / 1000, 'unixepoch', 'localtime') AS day, COUNT(*) AS cnt
        FROM answer_records r
        JOIN questions qs ON qs.id = r.questionId
        JOIN quizzes q ON q.id = qs.quizId
        WHERE q.status = 'GRADED' AND r.valueJson IS NOT NULL AND r.valueJson <> ''
            AND r.updatedAt >= :sinceMs
        GROUP BY day ORDER BY day ASC
        """
    )
    suspend fun dailyAnswered(sinceMs: Long): List<DayCount>

    @Query("SELECT * FROM answer_records WHERE questionId IN (:questionIds)")
    suspend fun getByQuestionIds(questionIds: List<Long>): List<AnswerRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: AnswerRecordEntity): Long

    @Update
    suspend fun update(record: AnswerRecordEntity)

    @Query("DELETE FROM answer_records WHERE questionId IN (:questionIds)")
    suspend fun deleteByQuestionIds(questionIds: List<Long>)
}

@Dao
interface WrongQuestionDao {
    @Query("SELECT * FROM wrong_questions WHERE mastered = 0 ORDER BY wrongAt DESC")
    fun observeActive(): Flow<List<WrongQuestionEntity>>

    /** 到期未复习数：错后满 [cutoffMs] 且未掌握。 */
    @Query("SELECT COUNT(*) FROM wrong_questions WHERE mastered = 0 AND wrongAt <= :cutoffMs")
    suspend fun dueCount(cutoffMs: Long): Int

    @Query("SELECT * FROM wrong_questions WHERE subjectId = :subjectId AND mastered = 0 ORDER BY wrongAt DESC")
    fun observeActiveBySubject(subjectId: Long): Flow<List<WrongQuestionEntity>>

    @Query("SELECT * FROM wrong_questions WHERE id = :id")
    suspend fun getById(id: Long): WrongQuestionEntity?

    @Query("SELECT * FROM wrong_questions")
    suspend fun getAll(): List<WrongQuestionEntity>

    @Query("DELETE FROM wrong_questions")
    suspend fun clearAll()

    /** 未掌握错题按科目分布（学习统计用）。 */
    @Query(
        """
        SELECT s.name AS name, s.colorIndex AS colorIndex, COUNT(*) AS cnt
        FROM wrong_questions w JOIN subjects s ON s.id = w.subjectId
        WHERE w.mastered = 0
        GROUP BY w.subjectId ORDER BY cnt DESC
        """
    )
    suspend fun activeCountBySubject(): List<SubjectWrongCount>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(wrongQuestion: WrongQuestionEntity): Long

    @Query("UPDATE wrong_questions SET mastered = :mastered WHERE id = :id")
    suspend fun setMastered(id: Long, mastered: Boolean)

    @Query("UPDATE wrong_questions SET explanationReport = :report WHERE id = :id")
    suspend fun updateExplanationReport(id: Long, report: String)

    @Query("DELETE FROM wrong_questions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Delete
    suspend fun delete(wrongQuestion: WrongQuestionEntity)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE subjectId = :subjectId ORDER BY updatedAt DESC")
    fun observeBySubject(subjectId: Long): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE title = :title LIMIT 1")
    suspend fun getByTitle(title: String): NoteEntity?

    @Query("SELECT * FROM notes")
    suspend fun getAll(): List<NoteEntity>

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun count(): Int

    @Query("DELETE FROM notes")
    suspend fun clearAll()

    /** 全局搜索：标题 / 正文 LIKE。 */
    @Query(
        """
        SELECT * FROM notes
        WHERE title LIKE '%' || :q || '%' ESCAPE '\' OR contentMarkdown LIKE '%' || :q || '%' ESCAPE '\'
        ORDER BY updatedAt DESC
        LIMIT :limit
        """
    )
    suspend fun search(q: String, limit: Int = 20): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)
}

@Dao
interface TextbookDao {
    @Query("SELECT * FROM textbooks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TextbookEntity>>

    @Query("SELECT * FROM textbooks WHERE subjectId = :subjectId ORDER BY createdAt ASC")
    suspend fun getBySubject(subjectId: Long): List<TextbookEntity>

    @Query("SELECT * FROM textbooks WHERE id = :id")
    suspend fun getById(id: Long): TextbookEntity?

    @Query("SELECT * FROM textbooks")
    suspend fun getAll(): List<TextbookEntity>

    @Query("DELETE FROM textbooks")
    suspend fun clearAll()

    @Query("SELECT * FROM textbook_chunks")
    suspend fun getAllChunks(): List<TextbookChunkEntity>

    @Query("DELETE FROM textbook_chunks")
    suspend fun clearChunks()

    @Insert
    suspend fun insert(textbook: TextbookEntity): Long

    @Update
    suspend fun update(textbook: TextbookEntity)

    @Delete
    suspend fun delete(textbook: TextbookEntity)

    @Insert
    suspend fun insertChunks(chunks: List<TextbookChunkEntity>)

    @Query("DELETE FROM textbook_chunks WHERE textbookId = :textbookId")
    suspend fun deleteChunks(textbookId: Long)

    /** 关键词全文检索（LIKE），返回该教材中最早出现的前若干块。 */
    @Query(
        """
        SELECT * FROM textbook_chunks
        WHERE textbookId = :textbookId AND content LIKE '%' || :query || '%' ESCAPE '\'
        ORDER BY seq ASC
        LIMIT :limit
        """
    )
    suspend fun searchChunks(textbookId: Long, query: String, limit: Int = 6): List<TextbookChunkEntity>

    /** 全局搜索：跨全部教材的正文 LIKE。 */
    @Query(
        """
        SELECT c.textbookId AS textbookId, t.title AS textbookTitle, t.subjectId AS subjectId,
            c.seq AS seq, c.page AS page, c.content AS content
        FROM textbook_chunks c JOIN textbooks t ON t.id = c.textbookId
        WHERE c.content LIKE '%' || :q || '%' ESCAPE '\'
        ORDER BY t.subjectId ASC, c.seq ASC
        LIMIT :limit
        """
    )
    suspend fun searchChunksGlobal(q: String, limit: Int = 8): List<TextbookHit>
}

/** 全局搜索投影（查询专用，非表）。 */

/** 对话消息命中：联会话取标题。 */
data class MessageHit(
    val id: Long,
    val sessionId: Long,
    val sessionTitle: String,
    val role: String,
    val content: String,
    val createdAt: Long
)

/** 题目题干命中：带 quizId 便于跳转答题页。 */
data class QuestionHit(
    val id: Long,
    val quizId: Long,
    val stem: String
)

/** 教材正文命中：带教材标题与页码。 */
data class TextbookHit(
    val textbookId: Long,
    val textbookTitle: String,
    val subjectId: Long,
    val seq: Int,
    val page: Int,
    val content: String
)

/** 学习统计：每日答题量。 */
data class DayCount(val day: String, val cnt: Int)

/** 学习统计：未掌握错题的科目分布。 */
data class SubjectWrongCount(val name: String, val colorIndex: Int, val cnt: Int)
