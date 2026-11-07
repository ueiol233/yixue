package com.ueiol.yixue

import com.ueiol.yixue.data.attachment.AttachmentExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 对话文档库抽取器单测：在内存里构造最小 OOXML（zip+XML）样例验证抽取。
 * PDF 依赖 pdfbox 生成器，不在此覆盖（真机手测）。
 */
class AttachmentExtractorTest {

    private val extractor = AttachmentExtractor()

    /** 内存里打一个 zip。 */
    private fun zipOf(entries: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    // ---------- 类型识别 ----------

    @Test
    fun typeOfRecognizesSupportedExtensions() {
        assertEquals("DOCX", extractor.typeOf("讲义.docx"))
        assertEquals("PPTX", extractor.typeOf("课件.PPTX"))
        assertEquals("XLSX", extractor.typeOf("成绩.xlsx"))
        assertEquals("PDF", extractor.typeOf("教材.pdf"))
        assertEquals(null, extractor.typeOf("老文档.doc"))
        assertEquals(null, extractor.typeOf("无名"))
    }

    // ---------- docx ----------

    @Test
    fun extractDocxSplitsIntoContentBlocks() = runBlocking {
        val paragraph = "<w:p><w:r><w:t>二次函数的图象是抛物线</w:t></w:r></w:p>"
        // 生成 >1200 字的内容，验证分块
        val many = (1..40).joinToString("") { paragraph.replace("二次函数的图象是抛物线", "第${'$'}i 句测试内容 ABCDEFGHIJKLMNOPQRSTUVWXYZ 0123456789") }
        val xml = "<w:document><w:body>${many}<w:p><w:r><w:t>结尾段</w:t></w:r></w:p></w:body></w:document>"
        val bytes = zipOf(mapOf("word/document.xml" to xml))

        val result = extractor.extract("notes.docx", ByteArrayInputStream(bytes))
        assertEquals("DOCX", result.type)
        assertTrue("内容块数应 > 1，实际 ${result.units.size}", result.units.size > 1)
        assertEquals("内容块 1", result.units.first().label)
        assertTrue(result.units.first().text.contains("第"))
        assertTrue(result.units.last().text.contains("结尾段"))
    }

    @Test
    fun extractDocxUnescapesXmlEntities() = runBlocking {
        val xml = "<w:document><w:body><w:p><w:r><w:t>a &lt; b &amp; c</w:t></w:r></w:p></w:body></w:document>"
        val bytes = zipOf(mapOf("word/document.xml" to xml))
        val result = extractor.extract("a.docx", ByteArrayInputStream(bytes))
        assertEquals(1, result.units.size)
        assertEquals("a < b & c", result.units[0].text.trim())
    }

    // ---------- pptx ----------

    @Test
    fun extractPptxReadsSlidesInNumericOrder() = runBlocking {
        val xml = { text: String -> "<p:sld><p:txBody><a:p><a:t>$text</a:t></a:p></p:txBody></p:sld>" }
        // slide10 在字符串排序里会排在 slide2 前，验证数字自然排序
        val bytes = zipOf(
            mapOf(
                "ppt/slides/slide1.xml" to xml("第一页"),
                "ppt/slides/slide2.xml" to xml("第二页"),
                "ppt/slides/slide10.xml" to xml("第十页")
            )
        )
        val result = extractor.extract("deck.pptx", ByteArrayInputStream(bytes))
        assertEquals(3, result.units.size)
        assertEquals("幻灯片 1", result.units[0].label)
        assertEquals("第一页", result.units[0].text)
        assertEquals("幻灯片 2", result.units[1].label)
        assertEquals("第二页", result.units[1].text)
        assertEquals("幻灯片 3", result.units[2].label)
        assertEquals("第十页", result.units[2].text)
    }

    // ---------- xlsx ----------

    @Test
    fun extractXlsxUsesSheetNamesAndSharedStrings() = runBlocking {
        val shared = "<sst><si><t>姓名</t></si><si><t>分数</t></si></sst>"
        val workbook =
            "<workbook><sheets>" +
                "<sheet name=\"期中\" sheetId=\"1\" r:id=\"rId1\"/>" +
                "<sheet name=\"期末\" sheetId=\"2\" r:id=\"rId2\"/>" +
                "</sheets></workbook>"
        val rels =
            "<Relationships>" +
                "<Relationship Id=\"rId1\" Target=\"worksheets/sheet1.xml\"/>" +
                "<Relationship Id=\"rId2\" Target=\"worksheets/sheet2.xml\"/>" +
                "</Relationships>"
        val sheet1 =
            "<worksheet><sheetData>" +
                "<row><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c></row>" +
                "<row><c r=\"A2\" t=\"inlineStr\"><is><t>小明</t></is></c><c r=\"B2\"><v>95</v></c></row>" +
                "</sheetData></worksheet>"
        val sheet2 = "<worksheet><sheetData><row><c r=\"A1\"><v>98.5</v></c></row></sheetData></worksheet>"

        val bytes = zipOf(
            mapOf(
                "xl/sharedStrings.xml" to shared,
                "xl/workbook.xml" to workbook,
                "xl/_rels/workbook.xml.rels" to rels,
                "xl/worksheets/sheet1.xml" to sheet1,
                "xl/worksheets/sheet2.xml" to sheet2
            )
        )
        val result = extractor.extract("scores.xlsx", ByteArrayInputStream(bytes))
        assertEquals(2, result.units.size)
        assertEquals("期中", result.units[0].label)
        assertTrue(result.units[0].text.contains("姓名\t分数"))
        assertTrue(result.units[0].text.contains("小明\t95"))
        assertEquals("期末", result.units[1].label)
        assertTrue(result.units[1].text.contains("98.5"))
    }

    // ---------- 拒绝与空文档 ----------

    @Test
    fun rejectOldBinaryFormats() = runBlocking {
        val e = runCatching { extractor.extract("old.doc", ByteArrayInputStream(ByteArray(0))) }
            .exceptionOrNull()
        assertTrue(e is AttachmentExtractor.UnsupportedFormatException)
        assertTrue(e!!.message!!.contains(".doc"))
    }

    @Test
    fun rejectBlankDocument() = runBlocking {
        val bytes = zipOf(mapOf("word/document.xml" to "<w:document><w:body></w:body></w:document>"))
        val e = runCatching { extractor.extract("empty.docx", ByteArrayInputStream(bytes)) }
            .exceptionOrNull()
        assertTrue(e is AttachmentExtractor.UnsupportedFormatException)
    }
}
