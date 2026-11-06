package com.ueiol.yixue.data.backup

import android.content.Context
import android.net.Uri
import com.ueiol.yixue.data.AppContainer
import com.ueiol.yixue.data.db.AnswerRecordEntity
import com.ueiol.yixue.data.db.MessageEntity
import com.ueiol.yixue.data.db.NoteEntity
import com.ueiol.yixue.data.db.QuestionEntity
import com.ueiol.yixue.data.db.QuizEntity
import com.ueiol.yixue.data.db.SessionEntity
import com.ueiol.yixue.data.db.SubjectEntity
import com.ueiol.yixue.data.db.TextbookChunkEntity
import com.ueiol.yixue.data.db.TextbookEntity
import com.ueiol.yixue.data.db.WrongQuestionEntity
import com.ueiol.yixue.data.settings.ProviderConfig
import com.ueiol.yixue.domain.model.AppJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 备份文件内的 AI 接口配置：**不包含 API 密钥**（密钥在加密存储中，出于安全考虑不落明文备份文件），
 * 恢复后需到「AI 服务设置」重新填写密钥。
 */
@Serializable
data class BackupProvider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val models: List<String> = emptyList(),
    val selectedModel: String = ""
)

@Serializable
data class BackupSettings(
    val temperature: Double,
    val maxTokens: Int,
    val providers: List<BackupProvider> = emptyList(),
    val activeProviderId: String = ""
)

/** 备份文件内容：全部 Room 数据 + AI 接口配置（无密钥）。 */
@Serializable
data class BackupData(
    val schemaVersion: Int = 1,
    val exportedAt: Long,
    val subjects: List<SubjectEntity> = emptyList(),
    val sessions: List<SessionEntity> = emptyList(),
    val messages: List<MessageEntity> = emptyList(),
    val quizzes: List<QuizEntity> = emptyList(),
    val questions: List<QuestionEntity> = emptyList(),
    val answerRecords: List<AnswerRecordEntity> = emptyList(),
    val wrongQuestions: List<WrongQuestionEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val textbooks: List<TextbookEntity> = emptyList(),
    val textbookChunks: List<TextbookChunkEntity> = emptyList(),
    val settings: BackupSettings? = null
)

/** 一次导入/导出的统计信息，用于界面反馈。 */
data class BackupStats(
    val subjects: Int,
    val sessions: Int,
    val messages: Int,
    val quizzes: Int,
    val notes: Int,
    val textbooks: Int,
    val pictures: Int
) {
    override fun toString(): String =
        "科目 $subjects · 会话 $sessions · 消息 $messages · 试卷 $quizzes · 笔记 $notes · 教材 $textbooks · 图片 $pictures"
}

/**
 * 数据备份：ZIP = backup.json（全部 Room 表 + 接口配置）+ pictures/（笔记图片）。
 * 导出用 SAF（ACTION_CREATE_DOCUMENT），导入覆盖式恢复（先清库再重灌，保持原 ID）。
 */
class BackupManager(
    private val app: Context,
    private val container: AppContainer
) {

    suspend fun exportTo(uri: Uri): Result<BackupStats> = runCatching {
        val settings = container.settings.current
        val data = BackupData(
            schemaVersion = BACKUP_SCHEMA_VERSION,
            exportedAt = System.currentTimeMillis(),
            subjects = container.subjectDao.getAll(),
            sessions = container.sessionDao.getAll(),
            messages = container.messageDao.getAll(),
            quizzes = container.quizDao.getAll(),
            questions = container.questionDao.getAll(),
            answerRecords = container.answerRecordDao.getAll(),
            wrongQuestions = container.wrongQuestionDao.getAll(),
            notes = container.noteDao.getAll(),
            textbooks = container.textbookDao.getAll(),
            textbookChunks = container.textbookDao.getAllChunks(),
            settings = BackupSettings(
                temperature = settings.temperature,
                maxTokens = settings.maxTokens,
                providers = settings.providers.map {
                    BackupProvider(it.id, it.name, it.baseUrl, it.models, it.selectedModel)
                },
                activeProviderId = settings.activeProviderId
            )
        )
        val json = AppJson.encodeToString(data)
        val picturesDir = File(app.filesDir, PICTURES_DIR)
        val pictureFiles = picturesDir.listFiles()?.filter { it.isFile }.orEmpty()

        app.contentResolver.openOutputStream(uri)?.use { raw ->
            ZipOutputStream(raw.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(ENTRY_DATA))
                zip.write(json.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                pictureFiles.forEach { file ->
                    zip.putNextEntry(ZipEntry("$PICTURES_DIR/${file.name}"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } ?: error("无法写入所选位置")
        BackupStats(
            subjects = data.subjects.size,
            sessions = data.sessions.size,
            messages = data.messages.size,
            quizzes = data.quizzes.size,
            notes = data.notes.size,
            textbooks = data.textbooks.size,
            pictures = pictureFiles.size
        )
    }

    suspend fun importFrom(uri: Uri): Result<BackupStats> = runCatching {
        var json: String? = null
        var pictures = 0
        val picturesDir = File(app.filesDir, PICTURES_DIR).apply { mkdirs() }
        app.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when {
                        entry.name == ENTRY_DATA -> json = zip.readBytes().toString(Charsets.UTF_8)
                        entry.name.startsWith("$PICTURES_DIR/") && !entry.isDirectory -> {
                            // 只取文件名部分，防路径穿越
                            val target = File(picturesDir, File(entry.name).name)
                            target.outputStream().use { zip.copyTo(it) }
                            pictures++
                        }
                    }
                    zip.closeEntry()
                }
            }
        } ?: error("无法读取所选文件")
        val data = json?.let {
            runCatching { AppJson.decodeFromString<BackupData>(it) }.getOrElse { e ->
                error("备份文件格式无法识别：${e.message}")
            }
        } ?: error("备份文件缺少数据（未找到 $ENTRY_DATA）")
        if (data.schemaVersion > BACKUP_SCHEMA_VERSION) {
            error("备份文件版本较新（v${data.schemaVersion}），请先升级应用")
        }

        // 单事务：先清全部业务表（外键安全顺序），再按依赖顺序重灌，保留原 ID
        container.dbTransaction {
            container.messageDao.clearAll()
            container.answerRecordDao.clearAll()
            container.questionDao.clearAll()
            container.wrongQuestionDao.clearAll()
            container.quizDao.clearAll()
            container.noteDao.clearAll()
            container.textbookDao.clearChunks()
            container.textbookDao.clearAll()
            container.sessionDao.clearAll()
            container.subjectDao.clearAll()

            data.subjects.forEach { container.subjectDao.insert(it) }
            data.sessions.forEach { container.sessionDao.insert(it) }
            data.textbooks.forEach { container.textbookDao.insert(it) }
            if (data.textbookChunks.isNotEmpty()) {
                container.textbookDao.insertChunks(data.textbookChunks)
            }
            data.notes.forEach { container.noteDao.insert(it) }
            data.quizzes.forEach { container.quizDao.insert(it) }
            if (data.questions.isNotEmpty()) {
                container.questionDao.insertAll(data.questions)
            }
            data.answerRecords.forEach { container.answerRecordDao.insert(it) }
            data.wrongQuestions.forEach { container.wrongQuestionDao.insert(it) }
            data.messages.forEach { container.messageDao.insert(it) }
        }

        // 接口配置恢复（密钥除外，需用户重填）
        data.settings?.let { s ->
            s.providers.forEach { p ->
                container.settings.upsertProvider(
                    ProviderConfig(id = p.id, name = p.name, baseUrl = p.baseUrl, models = p.models, selectedModel = p.selectedModel)
                )
            }
            if (s.providers.isNotEmpty()) {
                val active = s.providers.firstOrNull { it.id == s.activeProviderId } ?: s.providers.first()
                container.settings.setActiveProvider(active.id)
            }
            container.settings.updateGenerationParams(s.temperature, s.maxTokens)
        }

        BackupStats(
            subjects = data.subjects.size,
            sessions = data.sessions.size,
            messages = data.messages.size,
            quizzes = data.quizzes.size,
            notes = data.notes.size,
            textbooks = data.textbooks.size,
            pictures = pictures
        )
    }

    companion object {
        const val BACKUP_SCHEMA_VERSION = 1
        const val ENTRY_DATA = "backup.json"
        const val PICTURES_DIR = "pictures"
        const val MIME_ZIP = "application/zip"

        fun suggestedFileName(): String {
            val ts = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
            return "yixue-backup-$ts.zip"
        }
    }
}
