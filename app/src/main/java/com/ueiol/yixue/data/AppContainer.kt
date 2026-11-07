package com.ueiol.yixue.data

import android.content.Context
import androidx.room.withTransaction
import com.ueiol.yixue.data.ai.OpenAiClient
import com.ueiol.yixue.data.backup.BackupManager
import com.ueiol.yixue.data.db.YixueDatabase
import com.ueiol.yixue.data.repo.ChatRepository
import com.ueiol.yixue.data.repo.QuizRepository
import com.ueiol.yixue.data.settings.SettingsStore
import com.ueiol.yixue.data.textbook.TextbookImporter
import com.ueiol.yixue.domain.agent.QuizAgent

/**
 * 手写依赖注入容器（规划书：不引入 Hilt/Koin，保持精简）。
 * 进程级单例，由 [com.ueiol.yixue.YixueApp] 持有。
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database: YixueDatabase = YixueDatabase.build(appContext)

    val settings = SettingsStore(appContext)
    val aiClient = OpenAiClient(settings)

    /** 备份管理：导出/恢复全部 Room 数据 + 笔记图片（API 密钥不进备份）。 */
    val backupManager: BackupManager by lazy { BackupManager(appContext, this) }

    /** 在单个 Room 事务中执行挂起块（备份恢复等批量写用）。 */
    suspend fun <R> dbTransaction(block: suspend () -> R): R = database.withTransaction(block)

    val subjectDao get() = database.subjectDao()
    val sessionDao get() = database.sessionDao()
    val messageDao get() = database.messageDao()
    val quizDao get() = database.quizDao()
    val questionDao get() = database.questionDao()
    val answerRecordDao get() = database.answerRecordDao()
    val wrongQuestionDao get() = database.wrongQuestionDao()
    val noteDao get() = database.noteDao()
    val textbookDao get() = database.textbookDao()
    val attachmentDao get() = database.attachmentDao()

    val textbookImporter: TextbookImporter by lazy { TextbookImporter(appContext) }

    /** 对话文档库：上传/解析/搜索/读取/会话绑定。 */
    val attachmentStore: com.ueiol.yixue.data.attachment.AttachmentStore by lazy {
        com.ueiol.yixue.data.attachment.AttachmentStore(appContext, database.attachmentDao())
    }

    val chatRepository: ChatRepository by lazy {
        ChatRepository(sessionDao, messageDao) { block ->
            database.withTransaction { block() }
        }
    }

    val quizRepository: QuizRepository by lazy {
        QuizRepository(quizDao, questionDao, answerRecordDao) { block ->
            database.withTransaction { block() }
        }
    }

    val quizAgent: QuizAgent by lazy { QuizAgent(aiClient) }
    val reviewAgent: com.ueiol.yixue.domain.agent.ReviewAgent by lazy {
        com.ueiol.yixue.domain.agent.ReviewAgent(aiClient)
    }
}
