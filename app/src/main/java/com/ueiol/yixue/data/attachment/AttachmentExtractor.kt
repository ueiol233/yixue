package com.ueiol.yixue.data.attachment

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * 对话文档库解析器：把上传的文档抽成带分页单位的纯文本。
 * 分页单位：PDF=页、PPT=幻灯片、Excel=工作表、Word=内容块（约 1200 字）。
 * - PDF：pdfbox-android（与教材导入共用）
 * - docx/pptx/xlsx：zip + XML 正则抽取（OOXML 三件套本质都是 zip+XML，零大依赖）
 * - 老二进制格式 .doc/.ppt/.xls 不支持，明确报错
 * 输入用 InputStream（不依赖 Context），可单测。
 */
class AttachmentExtractor {

    data class Extracted(
        val type: String,
        val units: List<Unit>
    )

    /** 一个分页单位：label 如「第 2 页」「幻灯片 3」「工作表：期中」「内容块 4」，text 为纯文本。 */
    data class Unit(val label: String, val text: String)

    class UnsupportedFormatException(message: String) : Exception(message)

    companion object {
        /** 「+」面板文件选择器用的 mime 过滤。 */
        val SUPPORTED_MIME = arrayOf(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
        private const val DOCX_BLOCK_LEN = 1200
    }

    suspend fun extract(fileName: String, input: InputStream): Extracted = withContext(Dispatchers.IO) {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val units: List<Unit> = when (ext) {
            "pdf" -> extractPdf(input)
            "docx" -> extractDocx(input)
            "pptx" -> extractPptx(input)
            "xlsx" -> extractXlsx(input)
            "doc" -> throw UnsupportedFormatException("老版 .doc 格式暂不支持，请另存为 .docx 后重试")
            "ppt" -> throw UnsupportedFormatException("老版 .ppt 格式暂不支持，请另存为 .pptx 后重试")
            "xls" -> throw UnsupportedFormatException("老版 .xls 格式暂不支持，请另存为 .xlsx 后重试")
            else -> throw UnsupportedFormatException("不支持的文件类型（.$ext），支持 Word / PPT / Excel / PDF")
        }
        if (units.all { it.text.isBlank() }) {
            throw UnsupportedFormatException("未能从文档中提取到文字（可能是扫描版 PDF，需要带文字层的文件）")
        }
        Extracted(type = ext.uppercase(), units = units.filter { it.text.isNotBlank() })
    }

    /** 文件名推断类型标记；不支持返回 null。 */
    fun typeOf(fileName: String): String? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return if (ext in setOf("pdf", "docx", "pptx", "xlsx")) ext.uppercase() else null
    }

    // ---------- PDF ----------

    private suspend fun extractPdf(input: InputStream): List<Unit> = withContext(Dispatchers.IO) {
        input.use { stream ->
            PDDocument.load(stream).use { doc ->
                // 一次提取全文，以换页符分页（避免逐页 getText 的 O(页数²) 重解析）
                val all = PDFTextStripper().getText(doc)
                all.split('\u000C').mapIndexed { i, pageText ->
                    Unit("第 ${i + 1} 页", pageText.trim())
                }
            }
        }
    }

    // ---------- OOXML 通用 ----------

    /** 读 zip 内单个 entry。 */
    private fun readZipEntry(input: InputStream, name: String): String? {
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == name) {
                    val out = ByteArrayBuilder()
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = zip.read(buf)
                        if (n < 0) break
                        out.append(buf, n)
                    }
                    return String(out.backing, 0, out.size, Charsets.UTF_8)
                }
                entry = zip.nextEntry
            }
        }
        return null
    }

    /** 列出 zip 中匹配前缀的 entry 名（保持 zip 顺序）。 */
    private fun listZipEntries(input: InputStream, prefix: String): List<String> {
        val names = mutableListOf<String>()
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name.startsWith(prefix)) names += entry.name
                entry = zip.nextEntry
            }
        }
        return names
    }

    /** 读 zip 内多个 entry：名 → 内容。 */
    private fun readZipEntries(input: InputStream, names: Collection<String>): Map<String, String> {
        val wanted = names.toSet()
        val out = mutableMapOf<String, String>()
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name in wanted) {
                    val buf = ByteArray(8192)
                    val ba = ByteArrayBuilder()
                    while (true) {
                        val n = zip.read(buf)
                        if (n < 0) break
                        ba.append(buf, n)
                    }
                    out[entry.name] = String(ba.backing, 0, ba.size, Charsets.UTF_8)
                }
                entry = zip.nextEntry
            }
        }
        return out
    }

    /** XML 文本节点通用抽取：取标签内文本。</w:p> 类段落标签转换行由调用方指定。 */
    private fun xmlText(xml: String, textTagPattern: String, paragraphBreakTag: Regex? = null): String {
        val sb = StringBuilder(xml.length / 2)
        val regex = Regex(textTagPattern, RegexOption.DOT_MATCHES_ALL)
        for (m in regex.findAll(xml)) {
            if (m.groups[1] != null) sb.append(unescapeXml(m.groupValues[1]))
        }
        paragraphBreakTag?.let { sb.replace(it, "\n") }
        return sb.toString()
    }

    private fun unescapeXml(s: String): String = s
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: "" }
        .replace("&amp;", "&")

    // ---------- docx ----------

    private suspend fun extractDocx(input: InputStream): List<Unit> = withContext(Dispatchers.IO) {
        val bytes = input.readBytes()
        val documentXml = readZipEntry(ByteArrayInputStream(bytes), "word/document.xml")
            ?: return@withContext emptyList()
        // <w:t> 文本保留，</w:p> 段落换行
        val full = buildString {
            val regex = Regex("<w:t[^>]*>(.*?)</w:t>|</w:p>", RegexOption.DOT_MATCHES_ALL)
            for (m in regex.findAll(documentXml)) {
                if (m.groups[1] != null) append(unescapeXml(m.groupValues[1])) else append('\n')
            }
        }
        // 无页概念 → 按段落聚合成约 1200 字的内容块
        chunkify(full.split('\n')).mapIndexed { i, block -> Unit("内容块 ${i + 1}", block) }
    }

    private fun chunkify(lines: List<String>): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (sb.isNotEmpty() && sb.length + line.length > DOCX_BLOCK_LEN) {
                out += sb.toString().trim()
                sb.clear()
            }
            sb.append(line).append('\n')
        }
        if (sb.isNotBlank()) out += sb.toString().trim()
        return out
    }

    // ---------- pptx ----------

    private suspend fun extractPptx(input: InputStream): List<Unit> = withContext(Dispatchers.IO) {
        val bytes = input.readBytes()
        // 幻灯片文件名 slideN.xml，按数字自然排序
        val slideNames = listZipEntries(ByteArrayInputStream(bytes), "ppt/slides/slide")
            .filter { it.endsWith(".xml") }
            .sortedBy { it.substringAfterLast("slide", "").substringBeforeLast('.').toIntOrNull() ?: Int.MAX_VALUE }
        if (slideNames.isEmpty()) return@withContext emptyList()
        val contents = readZipEntries(ByteArrayInputStream(bytes), slideNames)
        slideNames.mapIndexed { i, name ->
            val xml = contents[name] ?: ""
            val text = xmlText(xml, "<a:t>(.*?)</a:t>")
                .lineSequence()
                .filter { it.isNotBlank() }
                .joinToString("\n")
            Unit("幻灯片 ${i + 1}", text)
        }
    }

    // ---------- xlsx ----------

    private suspend fun extractXlsx(input: InputStream): List<Unit> = withContext(Dispatchers.IO) {
        val bytes = input.readBytes()
        val sharedStrings = readZipEntry(ByteArrayInputStream(bytes), "xl/sharedStrings.xml")
            ?.let { xml -> Regex("<si>(.*?)</si>", RegexOption.DOT_MATCHES_ALL)
                .findAll(xml)
                .map { m -> xmlText(m.groupValues[1], "<t[^>]*>(.*?)</t>") }
                .toList() }
            ?: emptyList()

        // workbook.xml 给出工作表名（按出现顺序即工作表顺序），rel 映射到 worksheets 文件
        val workbookXml = readZipEntry(ByteArrayInputStream(bytes), "xl/workbook.xml") ?: ""
        val relsXml = readZipEntry(ByteArrayInputStream(bytes), "xl/_rels/workbook.xml.rels") ?: ""
        val relTarget = Regex("<Relationship[^>]*Id=\"([^\"]+)\"[^>]*Target=\"([^\"]+)\"", RegexOption.DOT_MATCHES_ALL)
            .findAll(relsXml)
            .associate { m -> m.groupValues[1] to m.groupValues[2] }
        val sheets = Regex("<sheet[^>]*>", RegexOption.DOT_MATCHES_ALL).findAll(workbookXml).toList()
            .map { m ->
                val tag = m.value
                val name = Regex("name=\"([^\"]*)\"").find(tag)?.groupValues?.get(1) ?: "工作表"
                val rid = Regex("r:id=\"([^\"]*)\"").find(tag)?.groupValues?.get(1) ?: ""
                name to relTarget[rid]
            }

        if (sheets.isEmpty()) return@withContext emptyList()
        val sheetFiles = sheets.mapNotNull { it.second?.removePrefix("/xl/")?.let { t -> "xl/$t" } }
        val contents = readZipEntries(ByteArrayInputStream(bytes), sheetFiles.toSet())

        sheets.mapIndexed { i, (name, target) ->
            val path = target?.removePrefix("/xl/")?.let { "xl/$it" }
            val xml = path?.let { contents[it] } ?: ""
            Unit(name.ifBlank { "工作表 ${i + 1}" }, sheetXmlToText(xml, sharedStrings))
        }
    }

    /** 工作表 → 每行一条 TSV 文本；单元格行内以制表符分隔。 */
    private fun sheetXmlToText(xml: String, sharedStrings: List<String>): String {
        val rowRegex = Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
        val cellRegex = Regex("<c\\b[^>]*>(.*?)</c>|<c\\b[^>]*/>", RegexOption.DOT_MATCHES_ALL)
        val sb = StringBuilder()
        for (rowM in rowRegex.findAll(xml)) {
            val cells = mutableListOf<String>()
            for (cellM in cellRegex.findAll(rowM.groupValues[1])) {
                val tag = cellM.value
                val isShared = "t=\"s\"" in tag
                val isInline = "t=\"inlineStr\"" in tag
                val value = when {
                    isShared -> Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL)
                        .find(tag)?.groupValues?.get(1)
                        ?.trim()?.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: ""
                    isInline -> Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
                        .find(tag)?.groupValues?.get(1)?.let { unescapeXml(it) } ?: ""
                    else -> Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL)
                        .find(tag)?.groupValues?.get(1)?.let { unescapeXml(it) } ?: ""
                }
                cells += value
            }
            val line = cells.dropLastWhile { it.isEmpty() }.joinToString("\t")
            if (line.isNotBlank()) sb.append(line).append('\n')
        }
        return sb.toString()
    }

    /** 简单可扩容字节数组（避免 zip 复制到 ByteArrayOutputStream 的来回）。 */
    private class ByteArrayBuilder {
        var backing = ByteArray(16 * 1024)
        var size = 0
            private set

        fun append(buf: ByteArray, n: Int) {
            if (size + n > backing.size) {
                backing = backing.copyOf((size + n).coerceAtLeast(backing.size * 2))
            }
            System.arraycopy(buf, 0, backing, size, n)
            size += n
        }
    }
}
