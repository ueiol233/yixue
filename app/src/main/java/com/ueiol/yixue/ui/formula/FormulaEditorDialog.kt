package com.ueiol.yixue.ui.formula

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ueiol.yixue.ui.theme.YxSheetShape

/**
 * 可视化公式弹窗：确认后交回 LaTeX，由调用方决定包 `$...$` 还是 `$$...$$`。
 *
 * 布局要点：
 * 1. `usePlatformDefaultWidth = false` 时窗口是整屏，内容必须自己居中，否则会贴在屏幕顶部；
 * 2. 打开时收起输入法（调用方同时 clearFocus），避免窗口被输入法顶起；
 * 3. 键盘区可滚动，但「清空 / 取消 / 插入」操作栏固定在弹窗底部，任何尺寸下都可见。
 */
@Composable
fun FormulaEditorDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    title: String = "可视化公式",
    initialLatex: String? = null,
    output: FormulaOutput = FormulaOutput.LATEX,
    modifier: Modifier = Modifier
) {
    // 编辑已有公式时把 LaTeX 反向解析成结构（不支持的命令会退回空公式）
    val state = remember(initialLatex) {
        initialLatex?.let { FormulaLatex.parse(it) } ?: FormulaState()
    }
    val confirmLabel = when {
        initialLatex != null -> "保存"
        output == FormulaOutput.EXPRESSION -> "插入表达式"
        else -> "插入"
    }
    val buildOutput: () -> String = {
        if (output == FormulaOutput.EXPRESSION) state.toExpression() else state.toLatex()
    }
    val scroll = rememberScrollState()
    val keyboard = LocalSoftwareKeyboardController.current

    // 弹窗里没有文本输入，收起输入法，避免窗口被顶起
    LaunchedEffect(Unit) { keyboard?.hide() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = modifier.fillMaxWidth(),
                shape = YxSheetShape,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f).padding(start = 6.dp)
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "关闭",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(scroll)
                    ) {
                        FormulaEditor(
                            state = state,
                            showActions = false,
                            confirmLabel = confirmLabel,
                            showLatex = output == FormulaOutput.LATEX
                        )
                    }
                    FormulaEditorActions(
                        state = state,
                        modifier = Modifier.padding(top = 6.dp),
                        confirmLabel = confirmLabel,
                        onCancel = onDismiss,
                        onConfirm = {
                            val value = buildOutput()
                            if (value.isNotBlank()) onConfirm(value)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}
