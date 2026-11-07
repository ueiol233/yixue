package com.ueiol.yixue.ui.notebook

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.ueiol.yixue.ui.common.YxLargeTitle
import com.ueiol.yixue.ui.common.YxActionChip
import com.ueiol.yixue.ui.common.MarkdownBlock
import com.ueiol.yixue.ui.common.PlotInputDialog
import com.ueiol.yixue.ui.formula.FormulaEditorDialog
import com.ueiol.yixue.ui.common.YxLargeTitle
import com.ueiol.yixue.ui.theme.YxExt
import kotlinx.coroutines.launch

/** 笔记编辑器：Markdown + 图片/函数图/公式/表格插入 + 预览 + 自动保存。 */
@Composable
fun NoteEditorPage(
    vm: NoteEditorViewModel,
    onBack: () -> Unit,
    jumpOffset: Int = -1,
    jumpLen: Int = 0,
    modifier: Modifier = Modifier
) {
    val title by vm.title.collectAsState()
    val content by vm.content.collectAsState()
    val saving by vm.saving.collectAsState()
    val savedTick by vm.savedTick.collectAsState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // 正文输入框用受控 TextFieldValue：光标/选区由用户自由移动，
    // 只在「VM 内容变化」（加载/插入图片公式/搜索跳转）时同步，避免重组把光标拉回末尾
    var fieldValue by remember { mutableStateOf(TextFieldValue("")) }
    val jumpFocusRequester = remember { FocusRequester() }

    LaunchedEffect(content) {
        if (fieldValue.text != content) {
            fieldValue = TextFieldValue(content, selection = TextRange(content.length))
        }
    }

    // 全局搜索跳转：内容加载后选中命中处并聚焦（TextField 会把光标滚入视野）
    LaunchedEffect(content, jumpOffset) {
        if (jumpOffset >= 0 && content.isNotEmpty()) {
            val end = minOf(content.length, jumpOffset + jumpLen.coerceAtLeast(0))
            val start = jumpOffset.coerceIn(0, content.length)
            fieldValue = TextFieldValue(content, selection = TextRange(start, end))
            jumpFocusRequester.requestFocus()
        }
    }

    // 打开已有笔记先看预览，点正文才进入编辑；新建/搜索跳转直接进编辑
    var preview by remember { mutableStateOf(vm.noteId > 0 && jumpOffset < 0) }
    var showFormulaDialog by remember { mutableStateOf(false) }
    // 非空表示正在编辑预览里点中的那条公式
    var editingLatex by remember { mutableStateOf<String?>(null) }
    var showPlot by remember { mutableStateOf(false) }
    // 非空表示正在编辑预览里点中的那块函数图（值为原 plot 块内容）
    var editingPlot by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                vm.importImage(uri)?.let { md -> vm.onContentChange(appendMd(content, md)) }
            }
        }
    }

    // 键盘收起时补手势条，弹出时只补「ime - 手势条」，避免双重空白
    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
    ) {
        YxLargeTitle(
            title = if (title.isBlank()) "新笔记" else title,
            subtitle = when {
                saving -> "保存中…"
                preview -> "预览 · 点击正文进入编辑"
                else -> "已自动保存"
            },
            actions = {
                IconButton(onClick = { preview = !preview }) {
                    Icon(if (preview) Icons.Outlined.Edit else Icons.Outlined.Visibility, contentDescription = "预览", tint = MaterialTheme.colorScheme.onSurface)
                }
                // 返回放在最右，与其它二级页保持一致
                IconButton(onClick = { vm.flush(); onBack() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
        if (!preview) {
            OutlinedTextField(
                value = title,
                onValueChange = { vm.onTitleChange(it) },
                placeholder = { Text("标题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            // 插入工具栏
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                YxActionChip(icon = Icons.Outlined.Image, text = "图片") {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                YxActionChip(icon = Icons.AutoMirrored.Outlined.ShowChart, text = "函数图") { showPlot = true }
                YxActionChip(icon = Icons.Outlined.Functions, text = "公式") {
                    focusManager.clearFocus()
                    showFormulaDialog = true
                }

            }
        }
        if (preview) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    // 点正文进入编辑；公式块自己的点击优先级更高
                    .clickable { preview = false }
                    .padding(16.dp)
            ) {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                MarkdownBlock(
                    text = content,
                    onLatexClick = { latex -> editingLatex = latex },
                    onPlotEdit = { plotContent -> editingPlot = plotContent }
                )
            }
        } else {
            OutlinedTextField(
                value = fieldValue,
                onValueChange = {
                    fieldValue = it
                    vm.onContentChange(it.text)
                },
                placeholder = { Text("Markdown 内容…") },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .focusRequester(jumpFocusRequester)
            )
        }
    }

    if (showFormulaDialog) {
        FormulaEditorDialog(
            onDismiss = { showFormulaDialog = false },
            onConfirm = { latex ->
                vm.onContentChange(appendMd(content, "$$\n" + latex + "\n$$"))
            }
        )
    }

    // 预览里点公式 → 回填到编辑器，保存时替换对应的那段 LaTeX
    editingLatex?.let { old ->
        FormulaEditorDialog(
            title = "编辑公式",
            initialLatex = old,
            onDismiss = { editingLatex = null },
            onConfirm = { latex ->
                vm.onContentChange(replaceFormula(content, old, latex))
            }
        )
    }

    if (showPlot) {
        PlotInputDialog(
            onDismiss = { showPlot = false },
            onInsert = { md -> vm.onContentChange(appendMd(content, md)); showPlot = false }
        )
    }

    // 编辑预览里点中的函数图：预填原表达式，完成后原位替换
    editingPlot?.let { oldExpr ->
        PlotInputDialog(
            initialExpr = oldExpr,
            onDismiss = { editingPlot = null },
            onInsert = { md ->
                vm.onContentChange(replacePlot(content, oldExpr, md))
                editingPlot = null
            }
        )
    }
}
/** 在 Markdown 末尾追加内容（保证独立成行）。 */
private fun appendMd(current: String, add: String): String {
    if (current.isBlank()) return add
    return current.trimEnd('\n') + "\n\n" + add
}

/** 用新的 LaTeX 替换笔记里指定的那条公式（兼容块级与行内两种写法）。 */
private fun replaceFormula(content: String, oldLatex: String, newLatex: String): String {
    val block = "$$\n$oldLatex\n$$"
    if (content.contains(block)) return content.replaceFirst(block, "$$\n$newLatex\n$$")
    val inline = "$$" + oldLatex + "$$"
    if (content.contains(inline)) return content.replaceFirst(inline, "$$" + newLatex + "$$")
    return content.replaceFirst(oldLatex, newLatex)
}

/** 原位替换笔记里的一块函数图；原文写法有出入时按第一个 plot 围栏块兜底替换。 */
private fun replacePlot(content: String, oldExpr: String, newMd: String): String {
    val exact = "```plot\n$oldExpr\n```"
    if (content.contains(exact)) return content.replaceFirst(exact, newMd)
    return Regex("```plot\\s*\\n[\\s\\S]*?```").replaceFirst(content, newMd)
}