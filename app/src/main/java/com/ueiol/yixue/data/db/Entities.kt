package com.ueiol.yixue.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * 数据模型对照规划书第 5 节。
 * JSON 类字段（options/attachments 等）v0 先存原始 JSON 字符串，
 * 序列化模型在 domain/model 建立后再接入。
 */

@Entity(tableName = "subjects")
@kotlinx.serialization.Serializable
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    // 科目色索引，对应 YxExt 七色，存储 0-6
    val colorIndex: Int = 0,
    val order: Int = 0,
    val autoCreated: Boolean = false
)

@Entity(tableName = "sessions")
@kotlinx.serialization.Serializable
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subjectId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false
)

@Entity(
    tableName = "messages",
    indices = [Index("sessionId")]
)
@kotlinx.serialization.Serializable
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    // user / assistant / system
    val role: String,
    val content: String,
    // 附件 JSON：图片路径 / plotSpec 等
    val attachmentsJson: String? = null,
    // 关联的 Quiz（AI 出题消息）
    val quizId: Long? = null,
    val createdAt: Long
)

@Entity(
    tableName = "quizzes",
    indices = [Index("sessionId")]
)
@kotlinx.serialization.Serializable
data class QuizEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val subjectId: Long? = null,
    val title: String,
    // GENERATING / DOING / GRADED
    val status: String,
    val createdAt: Long
)

/** 测验列表汇总（查询投影，非表）。 */
data class QuizSummary(
    val id: Long,
    val title: String,
    val status: String,
    val createdAt: Long,
    val total: Int,
    val answered: Int,
    val correct: Int
)

@Entity(
    tableName = "questions",
    indices = [Index("quizId")]
)
@kotlinx.serialization.Serializable
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val quizId: Long,
    val order: Int,
    // SINGLE_CHOICE / MULTIPLE_CHOICE / FILL_BLANK（预留 JUDGE / SOLUTION）
    val type: String,
    // Markdown + LaTeX
    val stem: String,
    // 选项 JSON 数组
    val optionsJson: String? = null,
    // 函数图像 plotSpec JSON
    val plotSpecJson: String? = null,
    // 选择题：字母集合；填空题：可接受答案 JSON 数组
    val answerJson: String,
    // 供 AI 复核与精讲的参考解答（Markdown + LaTeX）
    val referenceAnswer: String? = null,
    val explanation: String? = null,
    // 错因标签：概念不清/计算失误/审题错误/方法不会
    val mistakeTag: String? = null
)

@Entity(
    tableName = "answer_records",
    indices = [Index("questionId")]
)
@kotlinx.serialization.Serializable
data class AnswerRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: Long,
    // 学生作答内容 JSON
    val valueJson: String? = null,
    // UNANSWERED / LOCAL_OK / LOCAL_WRONG / AI_REVERSED / AI_WRONG
    val state: String,
    // AI 复核理由
    val aiReason: String? = null,
    val updatedAt: Long
)

@Entity(
    tableName = "wrong_questions",
    indices = [Index("questionId"), Index("subjectId")]
)
@kotlinx.serialization.Serializable
data class WrongQuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: Long,
    val subjectId: Long,
    val wrongAt: Long,
    val mastered: Boolean = false,
    // AI 精讲内容存档（Markdown）
    val explanationReport: String? = null,
    val sessionId: Long? = null
)

@Entity(
    tableName = "notes",
    indices = [Index("subjectId")]
)
@kotlinx.serialization.Serializable
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subjectId: Long? = null,
    val contentMarkdown: String,
    // 附件 JSON：图片路径 / plotSpec / latex 块
    val attachmentsJson: String? = null,
    val updatedAt: Long
)

/** 科目教材：导入的 PDF / Word 文档元信息。 */
@Entity(
    tableName = "textbooks",
    indices = [Index("subjectId")]
)
@kotlinx.serialization.Serializable
data class TextbookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    // 展示名（默认取文件名去扩展名）
    val title: String,
    val fileName: String,
    // 勾选项：该教材与用户在校使用的教材是否完全一致（提示词据此调整 AI 口径）
    val isExactMatch: Boolean = false,
    // 分块数 / 总字数（导入后统计，用于列表展示）
    val chunkCount: Int = 0,
    val charCount: Int = 0,
    val createdAt: Long
)

/** 教材全文按段落聚合的分块（约 600 字/块），供 AI 关键词检索。 */
@Entity(
    tableName = "textbook_chunks",
    indices = [Index("textbookId")]
)
@kotlinx.serialization.Serializable
data class TextbookChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val textbookId: Long,
    // 块顺序
    val seq: Int,
    // 来源页码（docx 无页概念，记 0）
    val page: Int,
    val content: String
)
