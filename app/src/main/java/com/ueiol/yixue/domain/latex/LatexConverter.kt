package com.ueiol.yixue.domain.latex

/**
 * LaTeX 公式转 Unicode 文本（离线渲染降级方案，规划书 9 节预案）。
 * 覆盖中学数学常用记号；后续可平滑替换为 KaTeX/JLatexMath 渲染。
 */
object LatexConverter {

    private val SYMBOLS = mapOf(
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ",
        "epsilon" to "ε", "theta" to "θ", "lambda" to "λ", "mu" to "μ",
        "pi" to "π", "rho" to "ρ", "sigma" to "σ", "phi" to "φ", "omega" to "ω",
        "Delta" to "Δ", "Sigma" to "Σ", "Omega" to "Ω", "Theta" to "Θ",
        "infty" to "∞", "pm" to "±", "mp" to "∓", "times" to "×", "div" to "÷",
        "cdot" to "·", "leq" to "≤", "le" to "≤", "geq" to "≥", "ge" to "≥",
        "neq" to "≠", "ne" to "≠", "approx" to "≈", "equiv" to "≡",
        "in" to "∈", "notin" to "∉", "subset" to "⊂", "subseteq" to "⊆",
        "cup" to "∪", "cap" to "∩", "emptyset" to "∅",
        "to" to "→", "rightarrow" to "→", "Rightarrow" to "⇒", "leftarrow" to "←",
        "Leftarrow" to "⇐", "leftrightarrow" to "↔", "Leftrightarrow" to "⇔", "iff" to "⇔", "implies" to "⇒",
        "longrightarrow" to "⟶", "Longrightarrow" to "⟹", "longleftarrow" to "⟵",
        "Longleftarrow" to "⟸", "Longleftrightarrow" to "⟺", "mapsto" to "↦",
        "uparrow" to "↑", "downarrow" to "↓",
        "ldots" to "…", "cdots" to "⋯", "dots" to "…",
        "sum" to "∑", "prod" to "∏", "int" to "∫", "iint" to "∬", "oint" to "∮",
        "partial" to "∂", "nabla" to "∇", "angle" to "∠", "perp" to "⊥",
        "parallel" to "∥", "degree" to "°", "circ" to "∘", "prime" to "′",
        "forall" to "∀", "exists" to "∃", "because" to "∵", "therefore" to "∴",
        "sqrt" to "√", "log" to "log", "ln" to "ln", "sin" to "sin", "cos" to "cos",
        "tan" to "tan", "cot" to "cot", "sec" to "sec", "csc" to "csc",
        "max" to "max", "min" to "min", "lim" to "lim"
    )

    private val SUPERS = mapOf(
        '0' to "⁰", '1' to "¹", '2' to "²", '3' to "³", '4' to "⁴",
        '5' to "⁵", '6' to "⁶", '7' to "⁷", '8' to "⁸", '9' to "⁹",
        '+' to "⁺", '-' to "⁻", 'n' to "ⁿ", 'i' to "ⁱ",
        'a' to "ᵃ", 'b' to "ᵇ", 'c' to "ᶜ", 'd' to "ᵈ", 'e' to "ᵉ",
        'f' to "ᶠ", 'g' to "ᵌ", 'h' to "ʰ", 'k' to "ᵏ", 'l' to "ˡ",
        'm' to "ᵐ", 'o' to "ᵒ", 'p' to "ᵖ", 'r' to "ʳ", 's' to "ˢ",
        't' to "ᵗ", 'u' to "ᵘ", 'v' to "ᵛ", 'w' to "ʷ", 'x' to "ˣ", 'y' to "ʸ"
    )

    private val SUBS = mapOf(
        '0' to "₀", '1' to "₁", '2' to "₂", '3' to "₃", '4' to "₄",
        '5' to "₅", '6' to "₆", '7' to "₇", '8' to "₈", '9' to "₉",
        '+' to "₊", '-' to "₋", 'n' to "ₙ", 'i' to "ᵢ",
        'a' to "ₐ", 'e' to "ₑ", 'o' to "ₒ", 'x' to "ₓ", 'k' to "ₖ", 'm' to "ₘ"
    )

    fun toUnicode(latex: String): String {
        val p = Parser(latex)
        return p.parseGroup(false)
    }

    private class Parser(val s: String) {
        var i = 0

        fun parseGroup(inBrace: Boolean): String {
            val sb = StringBuilder()
            while (i < s.length) {
                val ch = s[i]
                when {
                    ch == '}' -> { if (inBrace) { i++; return sb.toString() } else { i++ } }
                    ch == '{' -> { i++; sb.append(parseGroup(true)) }
                    ch == '\\' -> { i++; sb.append(readCommand()) }
                    ch == '^' -> { i++; sb.append(script(readArg(), SUPERS)) }
                    ch == '_' -> { i++; sb.append(script(readArg(), SUBS)) }
                    ch == '&' -> { i++; sb.append("  ") }
                    else -> { i++; sb.append(ch) }
                }
            }
            return sb.toString()
        }

        private fun readCommand(): String {
            if (i >= s.length) return ""
            // 反斜杠换行 \\
            if (s[i] == '\\') { i++; return "\n" }
            val sb = StringBuilder()
            while (i < s.length && s[i].isLetter()) { sb.append(s[i]); i++ }
            val name = sb.toString()
            if (name.isEmpty()) {
                // \{ \} \% 等转义符号
                if (i < s.length) { val c = s[i]; i++; return c.toString() }
                return ""
            }
            when (name) {
                "frac", "dfrac", "tfrac" -> {
                    val a = readArg(); val b = readArg()
                    return "(${a})/(${b})"
                }
                "sqrt" -> {
                    var index = ""
                    if (i < s.length && s[i] == '[') {
                        i++
                        val dsb = StringBuilder()
                        while (i < s.length && s[i] != ']') { dsb.append(s[i]); i++ }
                        i++
                        index = LatexConverter.toUnicode(dsb.toString())
                    }
                    val body = readArg()
                    return if (index.isEmpty()) "√(${body})" else "${index}√(${body})"
                }
                "text", "mathrm", "mathbf", "mathit", "operatorname" -> {
                    return readArg()
                }
                "left", "right", "big", "Big", "bigl", "bigr" -> {
                    // 吃掉后面的定界符
                    if (i < s.length && s[i] != '\\') i++
                    return ""
                }
                "overline" -> return readArg() + "̄"
                "hat" -> return readArg() + "̂"
                "vec" -> return readArg() + "⃗"
                else -> return SYMBOLS[name] ?: name
            }
        }

        private fun readArg(): String {
            if (i >= s.length) return ""
            val ch = s[i]
            return when {
                ch == '{' -> { i++; parseGroup(true) }
                ch == '\\' -> { i++; readCommand() }
                else -> { i++; ch.toString() }
            }
        }

        private fun script(arg: String, map: Map<Char, String>): String {
            if (arg.length == 1) return map[arg[0]] ?: arg
            return arg.map { map[it] ?: it }.joinToString("")
        }
    }
}