package com.ueiol.yixue.ui.formula

/**
 * LaTeX 子集 → 可视化公式结构（供预览端渲染真实排版）。
 * 支持 \frac、\sqrt、上标 ^、下标 _、括号分组、\text 与常用符号；
 * 遇到不认识的控制序列返回 null，由调用方降级为文本渲染。
 */
object FormulaLatex {

    fun parse(latex: String): FormulaState? {
        if (latex.isBlank()) return null
        val reader = Reader(latex, FormulaState())
        return if (reader.run()) reader.state else null
    }

    private class Reader(private val src: String, val state: FormulaState) {

        private var pos = 0
        private var nextId = 1

        fun run(): Boolean {
            val root = state.nodes(state.root)
            if (!parseInto(root)) return false
            return root.isNotEmpty()
        }

        private fun newSlot(): Int {
            val id = nextId++
            state.nodes(id)
            return id
        }

        private fun skipSpaces() {
            while (pos < src.length && src[pos] == ' ') pos++
        }

        /** 解析到 '}' 或文本结尾，节点写入 out；遇到不支持的命令返回 false。 */
        private fun parseInto(out: MutableList<FNode>): Boolean {
            val text = StringBuilder()
            fun flush() {
                if (text.isEmpty()) return
                out.add(FText(text.toString()))
                text.clear()
            }
            while (pos < src.length) {
                val c = src[pos]
                when {
                    c == '}' -> break
                    c == '\\' -> {
                        flush()
                        if (!parseCommand(out)) return false
                    }
                    c == '^' || c == '_' -> {
                        flush()
                        val sup = c == '^'
                        pos++
                        skipSpaces()
                        val script = mutableListOf<FNode>()
                        if (!readArg(script)) return false
                        attachScript(out, sup, script)
                    }
                    c == '{' -> {
                        flush()
                        pos++
                        if (!parseInto(out)) return false
                        if (pos < src.length && src[pos] == '}') pos++
                    }
                    c == '&' -> {
                        text.append(' ')
                        pos++
                    }
                    else -> {
                        text.append(c)
                        pos++
                    }
                }
            }
            flush()
            return true
        }

        /** 读取一个参数：{...} 组、控制序列或单个字符。 */
        private fun readArg(out: MutableList<FNode>): Boolean {
            if (pos >= src.length) return true
            return when (src[pos]) {
                '{' -> {
                    pos++
                    if (!parseInto(out)) return false
                    if (pos < src.length && src[pos] == '}') pos++
                    true
                }
                '\\' -> parseCommand(out)
                else -> {
                    out.add(FText(src[pos].toString()))
                    pos++
                    true
                }
            }
        }

        /** 上/下标：把前一个节点的最后一个字符抬为底数；没有前节点时用空底数。 */
        private fun attachScript(out: MutableList<FNode>, sup: Boolean, script: List<FNode>) {
            val base = newSlot()
            val baseNodes = state.nodes(base)
            val last = out.lastOrNull()
            when {
                last is FText && last.text.length > 1 -> {
                    out[out.size - 1] = FText(last.text.dropLast(1))
                    baseNodes.add(FText(last.text.takeLast(1)))
                }
                last != null -> {
                    out.removeAt(out.size - 1)
                    baseNodes.add(last)
                }
                else -> baseNodes.add(FText(""))
            }
            val scriptSlot = newSlot()
            state.nodes(scriptSlot).addAll(script)
            out.add(if (sup) FPow(base, scriptSlot) else FSub(base, scriptSlot))
        }

        private fun parseCommand(out: MutableList<FNode>): Boolean {
            pos++ // 反斜杠
            if (pos >= src.length) {
                out.add(FText("\\"))
                return true
            }
            if (!src[pos].isLetter()) {
                val ch = src[pos]
                pos++
                val text = when {
                    ch == '\\' -> "\n" // 行内换行 \\
                    ch == ',' || ch == ';' || ch == ':' || ch == ' ' -> " "
                    else -> ch.toString()
                }
                out.add(FText(text))
                return true
            }
            val start = pos
            while (pos < src.length && src[pos].isLetter()) pos++
            val name = src.substring(start, pos)
            return when (name) {
                "frac", "dfrac", "tfrac", "cfrac" -> {
                    skipSpaces()
                    val num = mutableListOf<FNode>()
                    if (!readArg(num)) return false
                    skipSpaces()
                    val den = mutableListOf<FNode>()
                    if (!readArg(den)) return false
                    val numSlot = newSlot()
                    state.nodes(numSlot).addAll(num)
                    val denSlot = newSlot()
                    state.nodes(denSlot).addAll(den)
                    out.add(FDiv(numSlot, denSlot))
                    true
                }
                "sqrt" -> {
                    skipSpaces()
                    if (pos < src.length && src[pos] == '[') {
                        val close = src.indexOf(']', pos)
                        if (close < 0) return false
                        pos = close + 1
                        skipSpaces()
                    }
                    val body = mutableListOf<FNode>()
                    if (!readArg(body)) return false
                    val bodySlot = newSlot()
                    state.nodes(bodySlot).addAll(body)
                    out.add(FSqrt(bodySlot))
                    true
                }
                "text", "mathrm", "mathbf", "operatorname" -> {
                    val raw = StringBuilder()
                    if (!readRawGroup(raw)) return false
                    if (raw.isNotEmpty()) out.add(FText(raw.toString()))
                    true
                }
                "left", "right", "big", "Big", "bigg", "displaystyle", "limits", "nolimits",
                "quad", "qquad", "!" -> true
                // 环境：aligned / cases / matrix 等只保留内部内容，不渲染括号
                "begin", "end" -> {
                    val ignored = StringBuilder()
                    readRawGroup(ignored)
                    true
                }
                else -> {
                    val sym = SYMBOLS[name] ?: return false
                    out.add(FText(sym))
                    true
                }
            }
        }

        /** \text{...} 原样取花括号内文本。 */
        private fun readRawGroup(out: StringBuilder): Boolean {
            skipSpaces()
            if (pos >= src.length || src[pos] != '{') return false
            pos++
            var depth = 1
            while (pos < src.length) {
                val c = src[pos]
                when {
                    c == '{' -> {
                        depth++
                        out.append(c)
                    }
                    c == '}' -> {
                        depth--
                        if (depth == 0) {
                            pos++
                            return true
                        }
                        out.append(c)
                    }
                    else -> out.append(c)
                }
                pos++
            }
            return false
        }
    }

    private val SYMBOLS: Map<String, String> = mapOf(
        "times" to "×", "div" to "÷", "cdot" to "·", "pm" to "±", "mp" to "∓",
        "le" to "≤", "leq" to "≤", "ge" to "≥", "geq" to "≥", "ne" to "≠", "neq" to "≠",
        "approx" to "≈", "equiv" to "≡", "infty" to "∞", "surd" to "√",
        "pi" to "π", "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ",
        "epsilon" to "ε", "theta" to "θ", "lambda" to "λ", "mu" to "μ", "nu" to "ν",
        "rho" to "ρ", "sigma" to "σ", "tau" to "τ", "phi" to "φ", "omega" to "ω",
        "Delta" to "Δ", "Sigma" to "Σ", "Omega" to "Ω", "Theta" to "Θ",
        "to" to "→", "rightarrow" to "→", "Rightarrow" to "⇒",
        "because" to "∵", "therefore" to "∴", "angle" to "∠", "parallel" to "∥",
        "circ" to "°", "degree" to "°", "sum" to "∑", "prod" to "∏", "int" to "∫", "in" to "∈",
        "sin" to "sin", "cos" to "cos", "tan" to "tan", "cot" to "cot",
        "sec" to "sec", "csc" to "csc", "ln" to "ln", "log" to "log", "lim" to "lim",
        "max" to "max", "min" to "min", "ldots" to "…", "dots" to "…", "cdots" to "…"
    )
}
