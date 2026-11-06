package com.ueiol.yixue.ui.formula

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue

/** 可视化公式的输出形式：LaTeX 文本，或可直接计算的表达式（函数图用）。 */
enum class FormulaOutput { LATEX, EXPRESSION }

/** 幂 / 下标底数的提取粒度：这些函数名整体作为底数（如 sin²）。 */
private val SCRIPT_UNIT_FUNCTIONS = listOf("sin", "cos", "tan", "cot", "sec", "csc", "ln", "log")

/**
 * 从 [text] 尾部取出「上一个输入单元」作为幂 / 下标的底数，返回 <剩余文本, 底数单元>。
 * 优先级：函数名整体 > 平衡括号组（(x+1)）> 单个字符。
 */
private fun splitTailUnit(text: String): Pair<String, String> {
    if (text.isEmpty()) return "" to ""
    for (fn in SCRIPT_UNIT_FUNCTIONS) {
        if (text.endsWith(fn)) return text.dropLast(fn.length) to fn
    }
    val last = text.last()
    if (last == ')' || last == ']') {
        val open = if (last == ')') '(' else '['
        var depth = 0
        for (i in text.length - 1 downTo 0) {
            when (text[i]) {
                last -> depth++
                open -> {
                    depth--
                    if (depth == 0) return text.substring(0, i) to text.substring(i)
                }
            }
        }
    }
    return text.dropLast(1) to last.toString()
}

/** 公式结构节点（可视化编辑用）。 */
sealed interface FNode

data class FText(val text: String) : FNode

/** 分数：分子槽 / 分母槽 */
data class FDiv(val numSlot: Int, val denSlot: Int) : FNode

/** 幂：底数槽 / 指数槽 */
data class FPow(val baseSlot: Int, val expSlot: Int) : FNode

/** 下标：底数槽 / 下标槽 */
data class FSub(val baseSlot: Int, val subSlot: Int) : FNode

/** 根号：根号内槽 */
data class FSqrt(val bodySlot: Int) : FNode

/**
 * 可视化公式状态机：
 * 以「槽位(slot)」组织，结构可嵌套；输入永远进入当前聚焦槽位。
 */
class FormulaState {

    private var nextId = 1
    val root: Int = 0

    val slots = mutableStateMapOf<Int, MutableList<FNode>>()

    /** 槽位 → 所属父槽位，用于退格回到上一层。 */
    private val slotParent = mutableMapOf<Int, Int>()

    var focused by mutableIntStateOf(0)
        private set

    init {
        slots[0] = mutableStateListOf()
    }

    fun nodes(slot: Int): MutableList<FNode> = slots.getOrPut(slot) { mutableStateListOf() }

    fun focus(slot: Int) { focused = slot }

    fun insertText(s: String) {
        if (s.isEmpty()) return
        val list = nodes(focused)
        val last = list.lastOrNull()
        if (last is FText) list[list.size - 1] = FText(last.text + s)
        else list.add(FText(s))
    }

    fun backspace() {
        val list = nodes(focused)
        if (list.isEmpty()) {
            val parent = slotParent[focused]
            if (parent != null) focused = parent
            return
        }
        val last = list.last()
        when (last) {
            is FText -> {
                if (last.text.length <= 1) list.removeAt(list.size - 1)
                else list[list.size - 1] = FText(last.text.dropLast(1))
            }
            else -> list.removeAt(list.size - 1)
        }
    }

    /** 插入分数线结构，聚焦分子。 */
    fun insertDiv() {
        val num = nextId++
        val den = nextId++
        slots[num] = mutableStateListOf()
        slots[den] = mutableStateListOf()
        slotParent[num] = focused
        slotParent[den] = focused
        nodes(focused).add(FDiv(num, den))
        focused = num
    }

    /** 插入幂：把上一个输入单元（单字符 / 括号组 / 函数名 / 结构节点）抬为底数，聚焦指数。 */
    fun insertPow() = insertScript { base, exp -> FPow(base, exp) }

    /** 插入下标：底数提取规则与幂一致。 */
    fun insertSub() = insertScript { base, sub -> FSub(base, sub) }

    private fun insertScript(create: (Int, Int) -> FNode) {
        val base = nextId++
        val script = nextId++
        slots[base] = mutableStateListOf()
        slots[script] = mutableStateListOf()
        slotParent[base] = focused
        slotParent[script] = focused
        val list = nodes(focused)
        when (val last = list.lastOrNull()) {
            is FText -> {
                // 只抬最后一个输入单元（如 y=x 只抬 x），整个文本节点抬走会变成 {y=x}^{2}
                val (rest, unit) = splitTailUnit(last.text)
                if (unit.isNotEmpty()) slots[base]!!.add(FText(unit))
                if (rest.isEmpty()) list.removeAt(list.size - 1)
                else list[list.size - 1] = FText(rest)
            }
            null -> {
                // 空槽：底数留空，聚焦指数后可直接输入
            }
            else -> {
                // 前一个是结构节点（已有的幂 / 分数 / 根号），整体抬为底数
                list.removeAt(list.size - 1)
                slots[base]!!.add(last)
            }
        }
        list.add(create(base, script))
        focused = script
    }

    /** 插入根号，聚焦根号内。 */
    fun insertSqrt() {
        val body = nextId++
        slots[body] = mutableStateListOf()
        slotParent[body] = focused
        nodes(focused).add(FSqrt(body))
        focused = body
    }

    fun clear() {
        nextId = 1
        slots.clear()
        slotParent.clear()
        slots[0] = mutableStateListOf()
        focused = 0
    }

    fun isEmpty(): Boolean = slots.values.all { it.isEmpty() }

    /** 导出 LaTeX（用于存储与判分）。 */
    fun toLatex(): String = slotLatex(root)

    private fun slotLatex(slot: Int): String = buildString {
        nodes(slot).forEach { n ->
            when (n) {
                is FText -> append(latexSafe(n.text))
                is FDiv -> append("\\frac{${slotLatex(n.numSlot)}}{${slotLatex(n.denSlot)}}")
                is FPow -> append("{${slotLatex(n.baseSlot)}}^{${slotLatex(n.expSlot)}}")
                is FSub -> append("{${slotLatex(n.baseSlot)}}_{${slotLatex(n.subSlot)}}")
                is FSqrt -> append("\\sqrt{${slotLatex(n.bodySlot)}}")
            }
        }
    }

    /** 键盘输入的 Unicode 运算符转成 LaTeX，保证导出可被解析。 */
    private fun latexSafe(s: String): String = s
        .replace("×", "\\times ")
        .replace("÷", "\\div ")
        .replace("·", "\\cdot ")
        .replace("−", "-")
        .replace("π", "\\pi ")
        .replace("≤", "\\leq ")
        .replace("≥", "\\geq ")
        .replace("≠", "\\neq ")
        .replace("≈", "\\approx ")
        .replace("±", "\\pm ")
        .replace("∞", "\\infty ")
        .replace("√", "\\surd ")
}

/**
 * 把可视化结构转成可直接计算的表达式（如 x^2、(x)/(2)、sqrt(x+1)），
 * 供函数图这类需要求值的场景使用。
 */
fun FormulaState.toExpression(): String = buildExpression(this, root)

private fun buildExpression(state: FormulaState, slot: Int): String =
    buildString {
        state.nodes(slot).forEach { node -> append(nodeExpression(state, node)) }
    }

private fun nodeExpression(state: FormulaState, node: FNode): String = when (node) {
    is FText -> expressionSafe(node.text)
    is FDiv -> "(${buildExpression(state, node.numSlot)})/(${buildExpression(state, node.denSlot)})"
    is FPow -> "(${buildExpression(state, node.baseSlot)})^(${buildExpression(state, node.expSlot)})"
    // 下标在单变量函数里没有对应写法，退化为底数本身
    is FSub -> buildExpression(state, node.baseSlot)
    is FSqrt -> "sqrt(${buildExpression(state, node.bodySlot)})"
}

private fun expressionSafe(s: String): String = s
    .replace("×", "*")
    .replace("÷", "/")
    .replace("·", "*")
    .replace("−", "-")
    .replace("π", "pi")
    .replace("²", "^2")
    .replace("³", "^3")