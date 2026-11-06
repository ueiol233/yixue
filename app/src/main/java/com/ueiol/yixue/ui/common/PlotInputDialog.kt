package com.ueiol.yixue.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ueiol.yixue.ui.formula.FormulaEditorDialog
import com.ueiol.yixue.ui.formula.FormulaOutput
import com.ueiol.yixue.ui.theme.YxRadius
import com.ueiol.yixue.ui.theme.YxSheetShape

/**
 * 函数图插入 / 编辑弹窗（聊天 / 笔记本通用）：
 * 手输表达式（下方实时预览，写错一眼可见），或用公式键盘拼分数/根号/幂，
 * 确认后以 ```plot 代码块 Markdown 交回，由调用方决定去处。
 * [initialExpr] 非空时为编辑模式，预填原表达式。
 */
@Composable
fun PlotInputDialog(
    onDismiss: () -> Unit,
    onInsert: (String) -> Unit,
    initialExpr: String = ""
) {
    var expr by remember { mutableStateOf(initialExpr) }
    var showKeyboard by remember { mutableStateOf(false) }
    // 实时解析预览：解析成功直接画出来，写错立刻能看见
    val previewSpec = remember(expr) {
        if (expr.isBlank()) null else PlotParser.parseLines(expr.lines())
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = YxSheetShape,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    if (initialExpr.isBlank()) "插入函数图" else "编辑函数图",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = expr,
                    onValueChange = { expr = it },
                    placeholder = { Text("如 y=x^2, x=-5..5（可多行，每行一条）") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (expr.isNotBlank()) {
                    if (previewSpec != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            shape = RoundedCornerShape(YxRadius.Mini),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(6.dp)) {
                                FunctionPlot(
                                    spec = previewSpec,
                                    modifier = Modifier.fillMaxWidth().height(170.dp)
                                )
                            }
                        }
                    } else {
                        Text(
                            "暂时无法解析，检查一下表达式（如 y=x^2）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                YxActionChip(icon = Icons.Outlined.Functions, text = "公式键盘输入") {
                    showKeyboard = true
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    YxPill(
                        text = if (initialExpr.isBlank()) "插入" else "完成",
                        primary = true,
                        enabled = expr.isNotBlank() && previewSpec != null,
                        height = 40.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        val md = "```plot\n$expr\n```"
                        onDismiss()
                        onInsert(md)
                    }
                    YxPill(text = "取消", height = 40.dp, modifier = Modifier.weight(1f)) { onDismiss() }
                }
            }
        }
    }

    if (showKeyboard) {
        FormulaEditorDialog(
            title = "用公式键盘输入函数",
            output = FormulaOutput.EXPRESSION,
            onDismiss = { showKeyboard = false },
            onConfirm = { expression ->
                // 保留已输入的范围参数（如 x=-5..5），只替换表达式部分
                val range = expr.substringAfter(',', "").trim()
                expr = if (range.isEmpty()) expression else "$expression, $range"
            }
        )
    }
}
