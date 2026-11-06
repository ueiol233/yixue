package com.ueiol.yixue.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ueiol.yixue.domain.latex.LatexConverter
import com.ueiol.yixue.ui.formula.FormulaCanvas
import com.ueiol.yixue.ui.formula.FormulaLatex
import com.ueiol.yixue.ui.plot.PlotViewerActivity
import com.ueiol.yixue.ui.theme.YxExt
import com.ueiol.yixue.ui.theme.YxRadius
import kotlinx.coroutines.delay

/** Markdown 块级结构。 */
sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Code(val lang: String?, val content: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
    data class ListItems(val ordered: Boolean, val items: List<String>) : MdBlock
    data class BlockLatex(val latex: String) : MdBlock
    data class NoteImage(val path: String) : MdBlock
    object Hr : MdBlock
}

object MarkdownParser {

    private val IMAGE_LINE = Regex("^\\s*!\\[[^\\]]*\\]\\((.+)\\)\\s*${'$'}")

    /** 整段只有一个公式：$$...$$ 或 $...$。 */
    private val SINGLE_MATH = Regex("^\\$\\$([^$]+)\\$\\$$|^\\$([^$]+)\\$$")

    fun parse(src: String): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        // 统一公式定界符：\[...\] → $$...$$，\(...\) → $...$
        val normalized = src.replace("\r\n", "\n")
            .replace("\\[", "$$")
            .replace("\\]", "$$")
            .replace("\\(", "$")
            .replace("\\)", "$")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
        val lines = normalized.split('\n')
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.trimStart().startsWith("```") -> {
                    val lang = line.trimStart().removePrefix("```").trim().ifEmpty { null }
                    val body = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                        body.appendLine(lines[i]); i++
                    }
                    i++ // 跳过结束围栏
                    blocks.add(MdBlock.Code(lang, body.toString().trimEnd('\n')))
                }
                line.trimStart().startsWith("$$") -> {
                    val rest = line.trimStart().removePrefix("$$")
                    if (rest.trimEnd().endsWith("$$")) {
                        // 单行 $$...$$，不能吞掉后面的正文
                        val inner = rest.trimEnd().removeSuffix("$$").trim()
                        if (inner.isNotEmpty()) blocks.add(MdBlock.BlockLatex(inner))
                        i++
                    } else {
                        val body = StringBuilder(rest)
                        i++
                        while (i < lines.size) {
                            val l = lines[i]
                            if (l.trimEnd().endsWith("$$")) {
                                body.append("\n").append(l.trimEnd().removeSuffix("$$"))
                                i++
                                break
                            }
                            body.append("\n").append(l)
                            i++
                        }
                        val inner = body.toString().trim()
                        if (inner.isNotEmpty()) blocks.add(MdBlock.BlockLatex(inner))
                    }
                }
                line.startsWith("#") -> {
                    val level = line.takeWhile { it == '#' }.length.coerceIn(1, 6)
                    blocks.add(MdBlock.Heading(level, line.dropWhile { it == '#' }.trim()))
                    i++
                }
                Regex("^\\s*(-{3,}|\\*{3,})\\s*${'$'}").matches(line) -> { blocks.add(MdBlock.Hr); i++ }
                line.trimStart().startsWith(">") -> {
                    val body = StringBuilder()
                    while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                        body.appendLine(lines[i].trimStart().removePrefix(">").trim()); i++
                    }
                    blocks.add(MdBlock.Quote(body.toString().trim()))
                }
                isTableRow(line) && i + 1 < lines.size && isSeparatorRow(lines[i + 1]) -> {
                    val header = splitTableRow(line)
                    i += 2
                    val rows = mutableListOf<List<String>>()
                    while (i < lines.size && isTableRow(lines[i])) {
                        rows.add(splitTableRow(lines[i])); i++
                    }
                    blocks.add(MdBlock.Table(header, rows))
                }
                // 没写分隔行（|---|）的表格：连续多行 "| ... |" 且列数一致
                line.trimStart().startsWith("|") && i + 1 < lines.size &&
                    lines[i + 1].trimStart().startsWith("|") &&
                    !isSeparatorRow(line) && !isSeparatorRow(lines[i + 1]) &&
                    splitTableRow(line).size == splitTableRow(lines[i + 1]).size -> {
                    val header = splitTableRow(line)
                    val colCount = header.size
                    i++
                    val rows = mutableListOf<List<String>>()
                    while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                        val cells = splitTableRow(lines[i])
                        if (cells.all { it.isBlank() }) break
                        rows.add(if (cells.size == colCount) cells else List(colCount) { cells.getOrElse(it) { "" } })
                        i++
                    }
                    blocks.add(MdBlock.Table(header, rows))
                }
                Regex("^\\s*([-*+]|\\d+[.)])\\s+").containsMatchIn(line) -> {
                    val ordered = Regex("^\\s*\\d+[.)]\\s+").containsMatchIn(line)
                    val items = mutableListOf<String>()
                    while (i < lines.size) {
                        val m = Regex("^\\s*([-*+]|\\d+[.)])\\s+(.*)${'$'}").find(lines[i])
                        if (m != null) { items.add(m.groupValues[2]); i++ }
                        else if (items.isNotEmpty() && lines[i].startsWith("  ") && lines[i].trim().isNotEmpty()) {
                            items[items.size - 1] += " " + lines[i].trim(); i++
                        } else break
                    }
                    blocks.add(MdBlock.ListItems(ordered, items))
                }
                line.isBlank() -> i++
                IMAGE_LINE.matches(line) -> {
                    blocks.add(MdBlock.NoteImage(IMAGE_LINE.find(line)!!.groupValues[1]))
                    i++
                }
                else -> {
                    val body = StringBuilder(line)
                    i++
                    while (i < lines.size && lines[i].isNotBlank() && !looksLikeBlockStart(lines[i])) {
                        body.append('\n').append(lines[i]); i++
                    }
                    val text = body.toString()
                    // 整行只有一个公式时按块级公式渲染（AI 常用 $...$ 单独成行）
                    val single = SINGLE_MATH.find(text.trim())
                    val doubleDelimited = single?.groupValues?.getOrNull(1).orEmpty()
                    val singleDelimited = single?.groupValues?.getOrNull(2).orEmpty()
                    val latex = when {
                        doubleDelimited.isNotBlank() -> doubleDelimited
                        // 单个 $ 可能只是货币符号，需要看起来像公式才转换
                        singleDelimited.isNotBlank() && looksLikeMath(singleDelimited) -> singleDelimited
                        else -> null
                    }
                    if (latex != null) {
                        blocks.add(MdBlock.BlockLatex(latex))
                    } else {
                        blocks.add(MdBlock.Paragraph(text))
                    }
                }
            }
        }
        return blocks
    }

    private fun looksLikeBlockStart(line: String): Boolean =
        line.trimStart().startsWith("```") || line.trimStart().startsWith("$$") ||
            line.startsWith("#") || looksLikeTableLine(line) || IMAGE_LINE.matches(line) ||
            Regex("^\\s*([-*+]|\\d+[.)])\\s+").containsMatchIn(line) ||
            line.trimStart().startsWith(">")

    /** 粗略判断 $...$ 里到底是不是公式，避免把 "$5 和 $10" 当公式。 */
    private fun looksLikeMath(s: String): Boolean =
        s.any { it == '\\' || it == '^' || it == '_' || it == '=' || it == '{' || it == '}' } ||
            Regex("[0-9]\\s*[+\\-*/]\\s*[0-9]").containsMatchIn(s)

    /** 宽松识别：允许缺少首尾竖线（模型经常这样写）。 */
    private fun isTableRow(line: String): Boolean {
        val t = line.trim()
        if (t.count { it == '|' } < 1) return false
        val cells = splitTableRow(t)
        return cells.size >= 2 && cells.any { it.isNotBlank() }
    }

    /** 像表格的一行：以 | 开头，或以 | 结尾且至少两列。 */
    private fun looksLikeTableLine(line: String): Boolean {
        val t = line.trim()
        return t.startsWith("|") || (t.endsWith("|") && t.count { it == '|' } >= 2)
    }

    private fun isSeparatorRow(line: String): Boolean {
        val t = line.trim()
        if (t.count { it == '|' } < 1) return false
        val cells = splitTableRow(t)
        return cells.size >= 2 && cells.all { Regex("^:?-+:?${'$'}").matches(it.trim()) }
    }

    private fun splitTableRow(line: String): List<String> =
        line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }
}
/**
 * Markdown 渲染组件（含 GFM 表格、代码块、plot 图、LaTeX 混排）。
 * [onLatexClick] 非空时，块级公式可点击（笔记里用于再次编辑）。
 */
@Composable
fun MarkdownBlock(
    text: String,
    modifier: Modifier = Modifier,
    onLatexClick: ((String) -> Unit)? = null,
    // 非空时 plot 卡片显示编辑按钮，参数为该块的原始表达式内容
    onPlotEdit: ((String) -> Unit)? = null
) {
    val blocks = remember(text) { MarkdownParser.parse(text) }
    Column(modifier = modifier) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    inlineMd(block.text),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineMedium
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
                is MdBlock.Paragraph -> Text(
                    inlineMd(block.text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
                is MdBlock.Quote -> Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(YxRadius.Mini),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text(inlineMd(block.text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(10.dp))
                }
                is MdBlock.ListItems -> Column(modifier = Modifier.padding(vertical = 2.dp)) {
                    block.items.forEachIndexed { idx, raw ->
                        // 任务列表：- [ ] / - [x]
                        val task = raw.startsWith("[ ] ") || raw.startsWith("[x] ") || raw.startsWith("[X] ")
                        val item = when {
                            raw.startsWith("[ ] ") -> raw.removePrefix("[ ] ")
                            raw.startsWith("[x] ") -> raw.removePrefix("[x] ")
                            raw.startsWith("[X] ") -> raw.removePrefix("[X] ")
                            else -> raw
                        }
                        val marker = when {
                            raw.startsWith("[ ] ") -> "☐ "
                            task -> "☑ "
                            block.ordered -> "${idx + 1}. "
                            else -> "• "
                        }
                        Row(modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)) {
                            Text(marker, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            Text(inlineMd(item), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                is MdBlock.Code -> CodeBlock(block, onPlotEdit)
                is MdBlock.Table -> MarkdownTable(block)
                is MdBlock.NoteImage -> coil.compose.AsyncImage(
                    model = java.io.File(block.path),
                    contentDescription = "笔记图片",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                )
                is MdBlock.BlockLatex -> {
                    val visual = remember(block.latex) { FormulaLatex.parse(block.latex) }
                    val base = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    val clickable = if (onLatexClick != null) {
                        base.clickable { onLatexClick(block.latex) }
                    } else {
                        base
                    }
                    if (visual != null) {
                        FormulaCanvas(
                            state = visual,
                            editable = false,
                            fontSize = 20.sp,
                            modifier = clickable.horizontalScroll(rememberScrollState())
                        )
                    } else {
                        LatexText(block.latex, MaterialTheme.typography.bodyLarge, clickable)
                    }
                }
                MdBlock.Hr -> HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun CodeBlock(block: MdBlock.Code, onPlotEdit: ((String) -> Unit)? = null) {
    when (block.lang?.lowercase()) {
        "plot" -> {
            val spec = remember(block.content) { PlotParser.parseLines(block.content.lines()) }
            val context = LocalContext.current
            if (spec != null) {
                // 复制为 plot 代码块源码，粘贴到笔记即可直接渲染出图
                val plotMd = remember(block.content) { "```plot\n${block.content}\n```" }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = RoundedCornerShape(YxRadius.Regular),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        // 只拦截点击：上下滑动仍然交给外层列表
                        .clickable { context.startActivity(PlotViewerActivity.intent(context, spec)) }
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = spec.expressions.joinToString("；"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "点击放大",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (onPlotEdit != null) {
                                IconButton(onClick = { onPlotEdit(block.content) }, modifier = Modifier.size(36.dp)) {
                                    Icon(
                                        Icons.Outlined.Edit,
                                        contentDescription = "编辑函数图",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            CopyIconButton(plotMd, modifier = Modifier.padding(start = 2.dp))
                        }
                        Box(Modifier.height(220.dp)) { FunctionPlot(spec, Modifier.fillMaxSize()) }
                    }
                }
            }
        }
        "latex", "tex", "math", "katex" -> {
            // 很多模型会把公式包在 latex 代码块里，这里按公式渲染而不是显示源码
            val visual = remember(block.content) { FormulaLatex.parse(block.content) }
            if (visual != null) {
                FormulaCanvas(
                    state = visual,
                    editable = false,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .horizontalScroll(rememberScrollState())
                )
            } else {
                LatexText(
                    block.content,
                    MaterialTheme.typography.bodyLarge,
                    Modifier.fillMaxWidth().padding(vertical = 6.dp)
                )
            }
        }
        else -> {
            SelectionContainer {
                Text(
                    block.content,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(YxRadius.Mini))
                        .padding(10.dp)
                        .horizontalScroll(rememberScrollState())
                )
            }
        }
    }
}

@Composable
private fun MarkdownTable(block: MdBlock.Table) {
    // 表头和数据行列数可能不一致，按最大值渲染，缺的补空，避免丢列/错位
    val colCount = maxOf(block.header.size, block.rows.maxOfOrNull { it.size } ?: 0)
    val natural = remember(block, colCount) { tableColumnWidths(block, colCount) }
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    // 复制为 Markdown 管道表源码，粘贴到笔记即可直接渲染
    val tableMd = remember(block, colCount) {
        buildString {
            append("| ")
            append(block.header.joinToString(" | "))
            append(" |\n|")
            append(List(block.header.size) { " --- " }.joinToString("|"))
            append("|\n")
            block.rows.forEach { r ->
                append("| ")
                append(List(colCount) { c -> r.getOrElse(c) { "" } }.joinToString(" | "))
                append(" |\n")
            }
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(YxRadius.Mini),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Box {
            BoxWithConstraints {
            // 列宽按内容估算后统一缩放：窄表铺满宽度，宽表横向滚动，所有行列宽一致
            val total = natural.fold(0f) { acc, w -> acc + w }.coerceAtLeast(1f)
            val available = maxWidth.value
            val scale = if (total < available) available / total else 1f
            val cellWidths = natural.map { (it * scale).dp }
            val tableWidth = cellWidths.fold(0.dp) { acc, w -> acc + w }
            Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                TableRow(
                    cellWidths = cellWidths,
                    dividerColor = dividerColor,
                    background = MaterialTheme.colorScheme.primaryContainer
                ) {
                    for (c in 0 until colCount) {
                        Text(
                            inlineMd(block.header.getOrElse(c) { "" }),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .width(cellWidths[c.coerceAtMost(cellWidths.lastIndex)])
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
                block.rows.forEach { row ->
                    HorizontalDivider(color = dividerColor, modifier = Modifier.width(tableWidth))
                    TableRow(cellWidths = cellWidths, dividerColor = dividerColor) {
                        for (c in 0 until colCount) {
                            Text(
                                inlineMd(row.getOrElse(c) { "" }),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .width(cellWidths[c.coerceAtMost(cellWidths.lastIndex)])
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
            }
            // 右上角悬浮复制按钮
            CopyIconButton(
                tableMd,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            )
        }
    }
}

/**
 * 表格行：按统一的列宽绘制竖向分隔线，保证每一行的竖线严格落在同一位置。
 */
@Composable
private fun TableRow(
    cellWidths: List<Dp>,
    dividerColor: Color,
    background: Color? = null,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .then(if (background != null) Modifier.background(background) else Modifier)
            .drawBehind {
                val stroke = 1.dp.toPx()
                var x = 0f
                cellWidths.dropLast(1).forEach { w ->
                    x += w.toPx()
                    drawLine(
                        color = dividerColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = stroke
                    )
                }
            },
        content = content
    )
}

/** 按内容估算每列宽度（含内边距），用于对齐所有行。 */
private fun tableColumnWidths(block: MdBlock.Table, colCount: Int): List<Float> =
    (0 until colCount).map { c ->
        val cells = buildList {
            add(block.header.getOrElse(c) { "" })
            block.rows.forEach { add(it.getOrElse(c) { "" }) }
        }
        val widest = cells.maxOfOrNull { estimateWidth(it) } ?: 72f
        widest.coerceIn(64f, 240f)
    }

private fun estimateWidth(cell: String): Float {
    val plain = cell
        .replace(Regex("\\$\\$[^$]*\\$\\$"), "0000")
        .replace(Regex("\\$[^$]*\\$"), "0000")
        .replace(Regex("\\*\\*|__|~~|`|\\*"), "")
    var w = 20f
    plain.forEach { ch -> w += if (ch.code > 0x2E80) 14f else 7.5f }
    return w
}
/**
 * 复制按钮：把 [text] 写入系统剪贴板，短暂显示成功态。
 * 用于表格 / plot 函数图的「复制后粘贴进笔记」场景。
 */
@Composable
fun CopyIconButton(text: String, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    IconButton(onClick = {
        clipboard.setText(AnnotatedString(text))
        copied = true
    }, modifier = modifier.size(36.dp)) {
        Icon(
            imageVector = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
            contentDescription = if (copied) "已复制" else "复制",
            tint = if (copied) YxExt.success else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

/** 行内 Markdown：粗体/斜体/删除线/链接/行内代码/行内 LaTeX（转 Unicode 内联显示）。 */
@Composable
fun inlineMd(text: String): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHigh
    return buildInlineMd(text, linkColor, codeBg)
}

/** 非组合版本：供粗体/斜体/删除线分支递归解析嵌套在样式里的行内公式。 */
private fun buildInlineMd(text: String, linkColor: Color, codeBg: Color): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        val s = text
        while (i < s.length) {
            val rest = s.substring(i)
            when {
                rest.startsWith("$$") -> {
                    val end = s.indexOf("$$", i + 2)
                    if (end > i + 2) { append(LatexConverter.toUnicode(s.substring(i + 2, end))); i = end + 2 }
                    else { append(s[i]); i++ }
                }
                s[i] == '$' -> {
                    val end = s.indexOf('$', i + 1)
                    if (end > i + 1) { append(LatexConverter.toUnicode(s.substring(i + 1, end))); i = end + 1 }
                    else { append(s[i]); i++ }
                }
                rest.startsWith("**") -> {
                    val end = s.indexOf("**", i + 2)
                    if (end > i + 2) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(buildInlineMd(s.substring(i + 2, end), linkColor, codeBg)); pop(); i = end + 2
                    } else { append(s[i]); i++ }
                }
                rest.startsWith("__") -> {
                    val end = s.indexOf("__", i + 2)
                    if (end > i + 2) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(buildInlineMd(s.substring(i + 2, end), linkColor, codeBg)); pop(); i = end + 2
                    } else { append(s[i]); i++ }
                }
                rest.startsWith("~~") -> {
                    val end = s.indexOf("~~", i + 2)
                    if (end > i + 2) {
                        pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                        append(buildInlineMd(s.substring(i + 2, end), linkColor, codeBg)); pop(); i = end + 2
                    } else { append(s[i]); i++ }
                }
                s[i] == '[' -> {
                    // [文字](链接) → 只显示文字，避免整段 URL 干扰阅读
                    val close = s.indexOf(']', i + 1)
                    if (close > i && close + 1 < s.length && s[close + 1] == '(') {
                        val urlEnd = s.indexOf(')', close + 2)
                        if (urlEnd > close) {
                            pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                            append(s.substring(i + 1, close))
                            pop()
                            i = urlEnd + 1
                        } else { append(s[i]); i++ }
                    } else { append(s[i]); i++ }
                }
                s[i] == '`' -> {
                    val end = s.indexOf('`', i + 1)
                    if (end > i + 1) {
                        pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg))
                        append(s.substring(i + 1, end)); pop(); i = end + 1
                    } else { append(s[i]); i++ }
                }
                s[i] == '\\' && i + 1 < s.length && s[i + 1].isLetter() -> {
                    // 没写 $ 的裸 LaTeX（如 \frac{1}{2}）也转成可读公式
                    val end = consumeLatex(s, i)
                    if (end > i + 1) {
                        append(LatexConverter.toUnicode(s.substring(i, end)))
                        i = end
                    } else { append(s[i]); i++ }
                }
                s[i] == '*' && i + 1 < s.length && s[i + 1] != ' ' -> {
                    val end = s.indexOf('*', i + 1)
                    if (end > i + 1) {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(buildInlineMd(s.substring(i + 1, end), linkColor, codeBg)); pop(); i = end + 1
                    } else { append(s[i]); i++ }
                }
                s[i] == '_' && i + 1 < s.length && s[i + 1] != ' ' &&
                    (i == 0 || !s[i - 1].isLetterOrDigit()) -> {
                    val end = s.indexOf('_', i + 1)
                    if (end > i + 1) {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(buildInlineMd(s.substring(i + 1, end), linkColor, codeBg)); pop(); i = end + 1
                    } else { append(s[i]); i++ }
                }
                else -> { append(s[i]); i++ }
            }
        }
    }
}

/** 从 [start]（反斜杠）起吃一个 LaTeX 命令及其花括号参数/上下标，返回结束下标。 */
private fun consumeLatex(s: String, start: Int): Int {
    var j = start + 1
    while (j < s.length && s[j].isLetter()) j++
    var guard = 0
    while (j < s.length && guard++ < 200) {
        when (s[j]) {
            '{' -> {
                var depth = 0
                var k = j
                while (k < s.length) {
                    if (s[k] == '{') depth++
                    else if (s[k] == '}') {
                        depth--
                        if (depth == 0) { k++; break }
                    }
                    k++
                }
                if (k <= j) return j
                j = k
            }
            '[' -> {
                val close = s.indexOf(']', j)
                if (close < 0) return j
                j = close + 1
            }
            '^', '_' -> {
                j++
                if (j < s.length && s[j] != '{') j++
            }
            else -> return j
        }
    }
    return j
}