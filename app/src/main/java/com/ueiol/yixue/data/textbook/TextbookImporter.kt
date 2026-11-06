package com.ueiol.yixue.data.textbook

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * 教材导入解析器：
 * - PDF：pdfbox-android 逐页提取文本（带页码）
 * - .docx：zip + XML 直接解析（零额外依赖），提取 word/document.xml 中的文本
 * - .doc（老二进制格式）不支持，明确报错
 * 解析结果按段落聚合成约 600 字的分块，供 AI 关键词检索。
 */
class TextbookImporter(private val context: Context) {

    data class ParsedTextbook(
        val fileName: String,
        val title: String,
        val chunks: List<Chunk>
    )

    data class Chunk(val seq: Int, val page: Int, val content: String)

    class UnsupportedFormatException(message: String) : Exception(message)

    /** 支持的文件类型（传给系统文件选择器）。 */
    companion object {
        val SUPPORTED_MIME = arrayOf(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        )
        private const val TARGET_CHUNK_LEN = 600
    }

    suspend fun parse(uri: Uri): ParsedTextbook = withContext(Dispatchers.IO) {
        val fileName = queryDisplayName(uri)
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val pages: List<Pair<Int, String>> = when (ext) {
            "pdf" -> parsePdf(uri)
            "docx" -> parseDocx(uri)
            "doc" -> throw UnsupportedFormatException("老版 .doc 格式暂不支持，请另存为 .docx 或 PDF 后重新导入")
            else -> throw UnsupportedFormatException("不支持的文件类型（.$ext），请导入 PDF 或 Word（.docx）文档")
        }
        val fullText = pages.joinToString("") { it.second }
        if (fullText.isBlank()) throw UnsupportedFormatException("未能从文档中提取到文字（可能是扫描版 PDF，需要带文字层的文件）")
        val chunks = chunkify(pages)
        ParsedTextbook(
            fileName = fileName,
            title = fileName.substringBeforeLast('.').trim().ifEmpty { fileName },
            chunks = chunks
        )
    }

    private suspend fun parsePdf(uri: Uri): List<Pair<Int, String>> = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { doc ->
                // 一次 getText 提取全文（PDFTextStripper 以 \f 分页），
                // 替代逐页 setStartPage/getText 的写法——那种每页都重新解析整份文档，复杂度 O(页数²)
                val all = PDFTextStripper().getText(doc)
                all.split('\u000C').mapIndexed { i, pageText -> (i + 1) to pageText }
            }
        } ?: emptyList()
    }

    private suspend fun parseDocx(uri: Uri): List<Pair<Int, String>> = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@withContext emptyList()
        val documentXml = extractDocxDocumentXml(bytes)
            ?: return@withContext emptyList()
        listOf(1 to docxXmlToText(documentXml))
    }

    private fun extractDocxDocumentXml(bytes: ByteArray): String? {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val out = ByteArrayOutputStream()
                    zip.copyTo(out)
                    return out.toString("UTF-8")
                }
                entry = zip.nextEntry
            }
        }
        return null
    }

    /** 把 document.xml 转成纯文本：<w:t> 内容保留，</w:p> 转换行，其余标签与属性丢弃。 */
    private fun docxXmlToText(xml: String): String {
        val sb = StringBuilder(xml.length / 2)
        val regex = Regex("<w:t[^>]*>(.*?)</w:t>|</w:p>", RegexOption.DOT_MATCHES_ALL)
        for (m in regex.findAll(xml)) {
            if (m.groups[1] != null) sb.append(unescapeXml(m.groupValues[1]))
            else sb.append('\n')
        }
        return sb.toString()
    }

    private fun unescapeXml(s: String): String = s
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: "" }
        .replace("&amp;", "&")

    /**
     * 按段落聚合成约 [TARGET_CHUNK_LEN] 字的分块；保留起始页码供 AI 引用。
     */
    private fun chunkify(pages: List<Pair<Int, String>>): List<Chunk> {
        val out = mutableListOf<Chunk>()
        val sb = StringBuilder()
        var startPage = 0
        fun flush() {
            val text = sb.toString().trim()
            if (text.isNotEmpty()) out += Chunk(out.size, startPage, text)
            sb.clear()
        }
        for ((page, text) in pages) {
            for (rawLine in text.split('\n')) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue
                if (sb.isNotEmpty() && sb.length + line.length > TARGET_CHUNK_LEN) flush()
                if (sb.isEmpty()) startPage = page
                sb.append(line).append('\n')
            }
        }
        flush()
        return out
    }

    private fun queryDisplayName(uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                cursor.getString(idx)?.let { return it }
            }
        }
        return uri.lastPathSegment ?: "教材文档"
    }
}
