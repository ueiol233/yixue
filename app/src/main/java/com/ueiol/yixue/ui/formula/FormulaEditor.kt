package com.ueiol.yixue.ui.formula

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ueiol.yixue.ui.common.YxPill
import com.ueiol.yixue.ui.theme.YxRadius

/**
 * 可视化公式渲染：分数（横线上下）、根号（带上划线）、幂（右上角小字），
 * 与纸面书写一致的排版；槽位可点击切换输入位置。
 */
@Composable
fun FormulaCanvas(
    state: FormulaState,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 22.sp,
    color: Color = MaterialTheme.colorScheme.onSurface,
    editable: Boolean = true
) {
    SlotView(state, state.root, fontSize, color, editable, modifier)
}

@Composable
private fun SlotView(
    state: FormulaState,
    slot: Int,
    fontSize: TextUnit,
    color: Color,
    editable: Boolean,
    modifier: Modifier = Modifier
) {
    val focused = editable && state.focused == slot
    Box(
        modifier = modifier
            .then(if (editable) Modifier.clickable { state.focus(slot) } else Modifier)
            .padding(horizontal = 1.dp, vertical = 1.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 空槽显示虚线占位符（MathQuill 风格），空底数/空指数一眼可见、可点击聚焦
            if (editable && state.nodes(slot).isEmpty()) EmptySlotMark(fontSize, color)
            state.nodes(slot).forEach { node ->
                when (node) {
                    is FText -> Text(
                        text = node.text,
                        fontSize = fontSize,
                        fontFamily = FontFamily.Serif,
                        color = color
                    )
                    is FDiv -> FractionView(state, node, fontSize, color, editable)
                    is FPow -> PowerView(state, node, fontSize, color, editable)
                    is FSub -> SubscriptView(state, node, fontSize, color, editable)
                    is FSqrt -> SqrtView(state, node, fontSize, color, editable)
                }
            }
            if (focused) CursorMark(fontSize, color)
        }
    }
}

@Composable
private fun EmptySlotMark(fontSize: TextUnit, color: Color) {
    Box(
        modifier = Modifier
            .padding(horizontal = 1.dp)
            .size(width = (fontSize.value * 0.42f).dp, height = (fontSize.value * 0.68f).dp)
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
    )
}

@Composable
private fun CursorMark(fontSize: TextUnit, color: Color) {
    val transition = rememberInfiniteTransition(label = "formula-cursor")
    val alpha = transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "formula-cursor-alpha"
    ).value
    Box(
        modifier = Modifier
            .padding(start = 1.dp)
            .width(2.dp)
            .height((fontSize.value * 0.9f).dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f * alpha))
    )
}

@Composable
private fun FractionView(
    state: FormulaState,
    node: FDiv,
    fontSize: TextUnit,
    color: Color,
    editable: Boolean
) {
    Column(
        modifier = Modifier.width(IntrinsicSize.Max).padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SlotView(state, node.numSlot, fontSize * 0.92f, color, editable)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .background(color)
        )
        SlotView(state, node.denSlot, fontSize * 0.92f, color, editable)
    }
}

@Composable
private fun PowerView(
    state: FormulaState,
    node: FPow,
    fontSize: TextUnit,
    color: Color,
    editable: Boolean
) {
    Row(verticalAlignment = Alignment.Bottom) {
        SlotView(state, node.baseSlot, fontSize, color, editable)
        Box(modifier = Modifier.offset(y = (-(fontSize.value * 0.42f)).dp)) {
            SlotView(state, node.expSlot, fontSize * 0.62f, color, editable)
        }
    }
}

@Composable
private fun SubscriptView(
    state: FormulaState,
    node: FSub,
    fontSize: TextUnit,
    color: Color,
    editable: Boolean
) {
    Row(verticalAlignment = Alignment.Top) {
        SlotView(state, node.baseSlot, fontSize, color, editable)
        Box(modifier = Modifier.offset(y = (fontSize.value * 0.40f).dp)) {
            SlotView(state, node.subSlot, fontSize * 0.62f, color, editable)
        }
    }
}

@Composable
private fun SqrtView(
    state: FormulaState,
    node: FSqrt,
    fontSize: TextUnit,
    color: Color,
    editable: Boolean
) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text("√", fontSize = fontSize * 1.1f, fontFamily = FontFamily.Serif, color = color)
        Box(
            modifier = Modifier
                .padding(top = (fontSize.value * 0.28f).dp)
                .drawOverline(color)
        ) {
            SlotView(state, node.bodySlot, fontSize * 0.92f, color, editable)
        }
    }
}

private fun Modifier.drawOverline(color: Color): Modifier = this.drawBehind {
    drawLine(
        color = color,
        start = Offset(0f, 1.dp.toPx()),
        end = Offset(size.width, 1.dp.toPx()),
        strokeWidth = 1.5.dp.toPx()
    )
}

/* ========================= 可视化公式编辑器 ========================= */

private val KEY_TABS = listOf("数字", "字母", "符号")

/** 三页字符键盘，每页 4 行，键帽尺寸一致不跳动。 */
private val KEY_PAGES: List<List<List<String>>> = listOf(
    listOf(
        listOf("1", "2", "3", "4", "5", "6", "7"),
        listOf("8", "9", "0", ".", "+", "−", "="),
        listOf("×", "÷", "(", ")", "[", "]", "π"),
        listOf("%", "°", ",", ":", ";", "√", "⌫")
    ),
    listOf(
        listOf("x", "y", "z", "a", "b", "n", "m"),
        listOf("p", "q", "r", "s", "t", "u", "v"),
        listOf("k", "w", "h", "d", "e", "f", "g"),
        listOf("sin", "cos", "tan", "ln", "log", "π", "⌫")
    ),
    listOf(
        listOf("≤", "≥", "≠", "≈", "±", "∞", "∈"),
        listOf("∑", "∏", "∫", "∠", "∥", "°", "→"),
        listOf("α", "β", "θ", "λ", "μ", "ω", "Δ"),
        listOf("∵", "∴", "⇒", "↔", "↑", "·", "⌫")
    )
)

/**
 * 可视化公式编辑器：预览区（点槽位切换输入位置）+ 结构键 + 三页字符键盘 + 底部操作栏。
 * [showActions] 为 false 时只渲染编辑区，由调用方（如全屏弹窗）自己放置固定操作栏。
 */
@Composable
fun FormulaEditor(
    state: FormulaState,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 26.sp,
    showActions: Boolean = true,
    confirmLabel: String = "插入",
    showLatex: Boolean = true,
    onDone: ((latex: String) -> Unit)? = null,
    onCancel: (() -> Unit)? = null
) {
    var tab by remember { mutableIntStateOf(0) }
    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = RoundedCornerShape(YxRadius.Regular)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 76.dp, max = 150.dp)
                    .horizontalScroll(rememberScrollState())
                    .clickable { state.focus(state.root) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                FormulaCanvas(state = state, fontSize = fontSize)
            }
        }
        Text(
            text = when {
                !showLatex -> if (state.isEmpty()) "点结构键或下方字符键开始输入" else "内容会转成可直接计算的表达式"
                state.isEmpty() -> "空公式 · 点结构键或下方字符键开始输入"
                else -> "LaTeX: " + state.toLatex()
            },
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 5.dp, bottom = 6.dp)
        )

        // 结构键
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            EditorTool("a/b", Modifier.weight(1f)) { state.insertDiv() }
            EditorTool("√", Modifier.weight(1f)) { state.insertSqrt() }
            EditorTool("xʸ", Modifier.weight(1f)) { state.insertPow() }
            EditorTool("xₙ", Modifier.weight(1f)) { state.insertSub() }
            EditorTool("( )", Modifier.weight(1f)) { state.insertText("()") }
            EditorTool("⌫", Modifier.weight(1f)) { state.backspace() }
        }
        // 槽位导航：◀▶ 按渲染顺序在底数/指数/分子/分母等槽位间移动光标
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            EditorTool("◀", Modifier.weight(1f)) { state.moveFocus(-1) }
            EditorTool("▶", Modifier.weight(1f)) { state.moveFocus(1) }
        }

        // 键盘分页
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            KEY_TABS.forEachIndexed { idx, label ->
                val selected = idx == tab
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                            RoundedCornerShape(YxRadius.Mini)
                        )
                        .clickable { tab = idx }
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                )
            }
        }

        KEY_PAGES[tab].forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { key ->
                    EditorKey(key, Modifier.weight(1f)) { onEditorKey(state, key) }
                }
            }
        }

        if (showActions) {
            FormulaEditorActions(
                state = state,
                modifier = Modifier.padding(top = 2.dp),
                confirmLabel = confirmLabel,
                onCancel = onCancel,
                onConfirm = onDone
            )
        }
    }
}

private fun onEditorKey(state: FormulaState, key: String) {
    when (key) {
        "⌫" -> state.backspace()
        "√" -> state.insertSqrt()
        "−" -> state.insertText("-")
        else -> state.insertText(key)
    }
}

/** 底部操作栏：清空 / 取消 / 插入，始终固定在可视区。 */
@Composable
fun FormulaEditorActions(
    state: FormulaState,
    modifier: Modifier = Modifier,
    confirmLabel: String = "插入",
    onCancel: (() -> Unit)? = null,
    onConfirm: ((latex: String) -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        YxPill(text = "清空", primary = false, height = 36.dp) { state.clear() }
        Spacer(Modifier.weight(1f))
        if (onCancel != null) {
            YxPill(
                text = "取消",
                primary = false,
                height = 40.dp,
                modifier = Modifier.padding(end = 8.dp)
            ) { onCancel() }
        }
        if (onConfirm != null) {
            YxPill(text = confirmLabel, primary = true, height = 40.dp) {
                onConfirm(state.toLatex())
            }
        }
    }
}

@Composable
private fun EditorTool(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(40.dp).clickable { onClick() },
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(YxRadius.Mini)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontFamily = FontFamily.Serif,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EditorKey(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(40.dp).clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(YxRadius.Mini)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = if (label.length > 1) 13.sp else 16.sp,
                fontFamily = FontFamily.Serif,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

