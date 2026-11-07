package com.ueiol.yixue.data.attachment

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.ueiol.yixue.data.db.AttachmentDao
import com.ueiol.yixue.data.db.AttachmentEntity
import com.ueiol.yixue.data.db.SessionAttachmentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 对话文档库存储：
 * - 上传：拷原件进 files/attachments/{id}/ → 抽全文 extracted.txt + 页索引 index.json → 入库
 * - 读取：AI 工具按 fileId 搜索 / 按页读取，全部走本地文件，零 token 常驻
 * - 删除：删目录 + 删记录；被其他会话绑定时仅解绑
 */
class AttachmentStore(
    private val context: Context,
    private val dao: AttachmentDao,
    private val extractor: AttachmentExtractor = AttachmentExtractor()
) {

    @Serializable
    data class PageIndexEntry(val start: Int, val end: Int, val label: String)

    /** 搜索命中：位置标签 + 前后文片段。 */
    data class SearchHit(val label: String, val snippet: String)

    /** 读取结果：正文 + 位置信息（未读完时给续读位置）。 */
    data class ReadResult(val text: String, val unitIndex: Int?, val unitLabel: String, val totalChars: Int)

    companion object {
        const val STATUS_PROCESSING = "PROCESSING"
        const val STATUS_READY = "READY"
        const val STATUS_FAILED = "FAILED"

        // AI 工具读取的硬上限，控制 token
        const val MAX_READ_CHARS = 4000
        private const val SNIPPET_CONTEXT = 40
        private val json = Json { ignoreUnknownKeys = true }
    }

    private fun dirOf(id: String) = File(File(context.filesDir, "attachments"), id)

    // ---------- 导入与解析 ----------

    /**
     * 导入：拷贝 → 建记录(PROCESSING) → 解析 → READY/FAILED。
     * 返回附件 ID；解析失败不抛（状态记在 FAILED + errorMsg）。
     */
    suspend fun import(uri: Uri, displayName: String?, mime: String): String? = withContext(Dispatchers.IO) {
        val name = displayName ?: queryDisplayName(uri) ?: "文档"
        val type = extractor.typeOf(name) ?: run {
            // 文件名无扩展名时兜底用 mime 推断
            val fromMime = when {
                mime.contains("pdf") -> "PDF"
                mime.contains("wordprocessingml") -> "DOCX"
                mime.contains("presentationml") -> "PPTX"
                mime.contains("spreadsheetml") -> "XLSX"
                else -> null
            }
            fromMime ?: return@withContext null
        }
        val id = newId()
        val dir = dirOf(id)
        dir.mkdirs()
        try {
            val source = File(dir, "source")
            context.contentResolver.openInputStream(uri)?.use { input ->
                source.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null

            dao.insert(
                AttachmentEntity(
                    id = id, name = name, mime = mime, type = type, size = source.length(),
                    unitCount = 0, status = STATUS_PROCESSING, createdAt = System.currentTimeMillis()
                )
            )
            parse(id)
            id
        } catch (t: Throwable) {
            dao.insert(
                AttachmentEntity(
                    id = id, name = name, mime = mime, type = type, size = 0,
                    unitCount = 0, status = STATUS_FAILED,
                    errorMsg = t.message?.take(120) ?: "解析失败", createdAt = System.currentTimeMillis()
                )
            )
            id
        }
    }

    /** 对已导入（或解析失败需重试）的附件执行解析，落 extracted.txt + index.json。 */
    suspend fun parse(id: String) = withContext(Dispatchers.IO) {
        val att = dao.getById(id) ?: return@withContext
        val dir = dirOf(id)
        try {
            val extracted = extractor.extract(att.name, File(dir, "source").inputStream())
            val sb = StringBuilder()
            val index = mutableListOf<PageIndexEntry>()
            for (unit in extracted.units) {
                val start = sb.length
                sb.append(unit.text).append('\n')
                index += PageIndexEntry(start, sb.length, unit.label)
            }
            File(dir, "extracted.txt").writeText(sb.toString())
            File(dir, "index.json").writeText(json.encodeToString(ListSerializer(PageIndexEntry.serializer()), index))
            dao.update(att.copy(unitCount = extracted.units.size, status = STATUS_READY, errorMsg = null))
        } catch (t: Throwable) {
            dao.update(att.copy(status = STATUS_FAILED, errorMsg = t.message?.take(120) ?: "解析失败"))
        }
    }

    // ---------- AI 工具读取 ----------

    suspend fun getById(id: String): AttachmentEntity? = dao.getById(id)

    /** 就绪附件的全文与页索引（无记录/未就绪返回 null）。 */
    private suspend fun readyText(id: String): Pair<AttachmentEntity, Pair<String, List<PageIndexEntry>>>? {
        val att = dao.getById(id) ?: return null
        if (att.status != STATUS_READY) return null
        val dir = dirOf(id)
        val textFile = File(dir, "extracted.txt")
        val indexFile = File(dir, "index.json")
        if (!textFile.exists() || !indexFile.exists()) return null
        val text = textFile.readText()
        val index = runCatching {
            json.decodeFromString(ListSerializer(PageIndexEntry.serializer()), indexFile.readText())
        }.getOrDefault(emptyList())
        return att to (text to index)
    }

    /** 关键词检索：逐处命中，带分页位置标签与前后文。大小写不敏感。 */
    suspend fun search(id: String, keyword: String, maxHits: Int = 10): List<SearchHit> {
        if (keyword.isBlank()) return emptyList()
        val (_, pair) = readyText(id) ?: return emptyList()
        val (text, index) = pair
        val lower = text.lowercase()
        val key = keyword.lowercase()
        val hits = mutableListOf<SearchHit>()
        var from = 0
        while (hits.size < maxHits) {
            val at = lower.indexOf(key, from)
            if (at < 0) break
            val label = labelAt(index, at)
            val s = (at - SNIPPET_CONTEXT).coerceAtLeast(0)
            val e = (at + key.length + SNIPPET_CONTEXT).coerceAtMost(text.length)
            val snippet = (if (s > 0) "…" else "") +
                text.substring(s, e).replace('\n', ' ') +
                (if (e < text.length) "…" else "")
            hits += SearchHit(label, snippet)
            from = at + key.length
        }
        return hits
    }

    /** 按页/块/工作表读取（page 从 1 起）；不传则从 start 字符处读一段。 */
    suspend fun read(id: String, page: Int?, start: Int = 0): ReadResult? {
        val (_, pair) = readyText(id) ?: return null
        val (text, index) = pair
        return if (page != null) {
            val entry = index.getOrNull(page - 1) ?: return null
            val unitText = text.substring(entry.start, entry.end).trim()
            val truncated = unitText.length > MAX_READ_CHARS
            ReadResult(
                text = unitText.take(MAX_READ_CHARS) + if (truncated) "\n…（本${unitTail(entry.label)}过长，已截断）" else "",
                unitIndex = page, unitLabel = entry.label, totalChars = unitText.length
            )
        } else {
            val s = start.coerceIn(0, text.length)
            val e = (s + MAX_READ_CHARS).coerceAtMost(text.length)
            ReadResult(
                text = text.substring(s, e),
                unitIndex = null,
                unitLabel = labelAt(index, s),
                totalChars = text.length
            )
        }
    }

    private fun unitTail(label: String) = when {
        label.contains("幻灯片") -> "张幻灯片"
        label.contains("工作表") -> "个工作表"
        label.contains("内容块") -> "内容块"
        else -> "页"
    }

    private fun labelAt(index: List<PageIndexEntry>, offset: Int): String =
        index.firstOrNull { offset in it.start until it.end }?.label ?: "文档"

    // ---------- 会话绑定 ----------

    /** 会话当前绑定的文档（AI 工具访问范围）。 */
    suspend fun boundTo(sessionId: Long): List<AttachmentEntity> = dao.getBySession(sessionId)

    suspend fun bindToSession(sessionId: Long, attachmentId: String) {
        dao.bind(SessionAttachmentEntity(sessionId, attachmentId, System.currentTimeMillis()))
    }

    /** 解绑：只移除本会话引用，文件保留。 */
    suspend fun unbindFromSession(sessionId: Long, attachmentId: String) {
        dao.unbind(sessionId, attachmentId)
    }

    /**
     * 删除会话的级联清理（用户要求：删会话连文档一起删）：
     * 先解绑全部；文件本体只在该文档不再被任何其他会话绑定时才删除。
     */
    suspend fun onSessionDeleted(sessionId: Long) {
        val bound = dao.getBySession(sessionId)
        dao.unbindAllForSession(sessionId)
        for (att in bound) {
            if (dao.boundSessionCount(att.id) == 0) deleteFileAndRecord(att.id)
        }
    }

    /** 全局删除（文档库管理页）：硬删目录 + 记录 + 全部绑定。 */
    suspend fun deleteCompletely(id: String) {
        dao.unbindAllForAttachment(id)
        deleteFileAndRecord(id)
    }

    private suspend fun deleteFileAndRecord(id: String) {
        dirOf(id).deleteRecursively()
        dao.delete(id)
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (t: Throwable) {
        null
    }
}

/** att_ 前缀随机 ID（同时用作目录名）。 */
fun newId(): String = "att_" + java.util.UUID.randomUUID().toString().replace("-", "").take(16)

/** 附件类型展示标签（列表/气泡用）。 */
fun com.ueiol.yixue.data.db.AttachmentEntity.typeLabel(): String = when (type) {
    "DOCX" -> "Word · $unitCount 个内容块"
    "PPTX" -> "PPT · $unitCount 张幻灯片"
    "XLSX" -> "Excel · $unitCount 个工作表"
    "PDF" -> "PDF · $unitCount 页"
    else -> "$type · $unitCount"
}
