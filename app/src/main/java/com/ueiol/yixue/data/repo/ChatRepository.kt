package com.ueiol.yixue.data.repo

import com.ueiol.yixue.data.db.MessageDao
import com.ueiol.yixue.data.db.MessageEntity
import com.ueiol.yixue.data.db.SessionDao
import com.ueiol.yixue.data.db.SessionEntity
import com.ueiol.yixue.domain.model.AppJson
import com.ueiol.yixue.domain.model.StringListSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ChatRepository(
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
    // Room 事务运行器（AppContainer 注入），保证多步写库的原子性
    private val runInTransaction: suspend (suspend () -> Unit) -> Unit
) {

    fun observeSessions(): Flow<List<SessionEntity>> = sessionDao.observeAllNonEmpty()

    /** 清理没有消息的空会话。 */
    suspend fun deleteEmptySessions() = sessionDao.deleteEmpty()

    fun observeMessages(sessionId: Long): Flow<List<MessageEntity>> =
        messageDao.observeBySession(sessionId)

    suspend fun createSession(title: String = "新对话"): Long {
        val now = System.currentTimeMillis()
        return sessionDao.insert(SessionEntity(title = title, createdAt = now, updatedAt = now))
    }

    suspend fun renameSession(id: Long, title: String) = sessionDao.rename(id, title)

    suspend fun setPinned(id: Long, pinned: Boolean) = sessionDao.setPinned(id, pinned)

    suspend fun deleteSession(session: SessionEntity) {
        runInTransaction {
            messageDao.deleteBySession(session.id)
            sessionDao.delete(session)
        }
    }

    suspend fun historyMessages(sessionId: Long): List<MessageEntity> =
        messageDao.observeBySession(sessionId).first()

    suspend fun addUserMessage(
        sessionId: Long,
        text: String,
        images: List<String> = emptyList(),
        // 文档引用（"doc:{id}|{name}|{type}" 格式），随消息存档供聊天气泡徽标展示
        docRefs: List<String> = emptyList()
    ) {
        val all = images + docRefs
        messageDao.insert(
            MessageEntity(
                sessionId = sessionId, role = "user", content = text,
                attachmentsJson = if (all.isEmpty()) null else AppJson.encodeToString(StringListSerializer, all),
                createdAt = System.currentTimeMillis()
            )
        )
        touchSession(sessionId)
        maybeRenameFromText(sessionId, text)
    }

    suspend fun addAssistantMessage(sessionId: Long, content: String, quizId: Long? = null) {
        messageDao.insert(
            MessageEntity(
                sessionId = sessionId, role = "assistant", content = content,
                quizId = quizId,
                createdAt = System.currentTimeMillis()
            )
        )
        touchSession(sessionId)
    }

    /** 首条消息自动生成会话标题。 */
    suspend fun maybeRenameFromText(sessionId: Long, text: String) {
        val session = sessionDao.getById(sessionId) ?: return
        if (session.title == "新对话" || session.title.isBlank()) {
            sessionDao.rename(sessionId, text.take(16).ifBlank { "新对话" })
        }
    }

    /** 消息附带的图片（data URL 列表；过滤掉 doc: 前缀的文档引用）。 */
    fun attachmentsOf(msg: MessageEntity): List<String> =
        msg.attachmentsJson?.let {
            runCatching { AppJson.decodeFromString(StringListSerializer, it) }.getOrDefault(emptyList())
        }?.filterNot { it.startsWith("doc:") } ?: emptyList()

    /** 消息引用的文档（"doc:{id}|{name}|{type}"，聊天气泡徽标用）。 */
    fun docRefsOf(msg: MessageEntity): List<Triple<String, String, String>> =
        msg.attachmentsJson?.let {
            runCatching { AppJson.decodeFromString(StringListSerializer, it) }.getOrDefault(emptyList())
        }?.filter { it.startsWith("doc:") }?.map { entry ->
            val parts = entry.removePrefix("doc:").split('|')
            Triple(parts.getOrElse(0) { "" }, parts.getOrElse(1) { "文档" }, parts.getOrElse(2) { "" })
        } ?: emptyList()

    suspend fun touchSession(sessionId: Long) {
        val s = sessionDao.getById(sessionId) ?: return
        sessionDao.update(s.copy(updatedAt = System.currentTimeMillis()))
    }
}