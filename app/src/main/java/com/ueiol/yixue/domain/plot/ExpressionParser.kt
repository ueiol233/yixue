package com.ueiol.yixue.domain.plot

class ExpressionException(message: String) : IllegalArgumentException(message)

/**
 * 数学表达式解析器（递归下降）。
 * 支持：+ - * / ^、括号、隐式乘法（2x、2(x+1)）、函数 sin/cos/tan/asin/acos/atan/
 * sinh/cosh/tanh/ln/log/log2/sqrt/abs/exp/floor/ceil/min/max/pow、常量 pi/e、变量 x。
 * compile 一次求值多次，用于绘图与数值判分。
 */
object ExpressionParser {

    fun compile(expression: String): (Double) -> Double {
        val tokens = try {
            tokenize(expression)
        } catch (e: ExpressionException) {
            throw e
        }
        val parser = Parser(tokens)
        val node = parser.parseExpression()
        if (!parser.atEnd()) throw ExpressionException("意外字符: ${parser.peekText()}")
        return { x -> node.eval(x) }
    }

    private enum class T { NUM, IDENT, OP, LP, RP, COMMA }

    private data class Tok(val type: T, val text: String)

    private val FUNCS = setOf(
        "sin", "cos", "tan", "asin", "acos", "atan",
        "sinh", "cosh", "tanh", "ln", "log", "log2",
        "sqrt", "abs", "exp", "floor", "ceil", "min", "max", "pow"
    )

    private fun tokenize(src: String): List<Tok> {
        val out = mutableListOf<Tok>()
        var i = 0
        val s = src.lowercase().replace("×", "*").replace("÷", "/").replace("−", "-")
        while (i < s.length) {
            val ch = s[i]
            when {
                ch.isWhitespace() -> i++
                ch.isDigit() || (ch == '.' && i + 1 < s.length && s[i + 1].isDigit()) -> {
                    var j = i
                    while (j < s.length && (s[j].isDigit() || s[j] == '.')) j++
                    out.add(Tok(T.NUM, s.substring(i, j)))
                    i = j
                }
                ch.isLetter() -> {
                    var j = i
                    while (j < s.length && s[j].isLetter()) j++
                    val word = s.substring(i, j)
                    when {
                        word in FUNCS -> { out.add(Tok(T.IDENT, word)); i = j }
                        word == "pi" -> { out.add(Tok(T.NUM, Math.PI.toString())); i = j }
                        word == "π" -> { out.add(Tok(T.NUM, Math.PI.toString())); i = j }
                        word == "e" -> { out.add(Tok(T.NUM, Math.E.toString())); i = j }
                        word == "x" -> { out.add(Tok(T.IDENT, "x")); i = j }
                        else -> throw ExpressionException("未知标识符: $word")
                    }
                }
                ch in "+-*/^" -> { out.add(Tok(T.OP, ch.toString())); i++ }
                ch == '(' -> { out.add(Tok(T.LP, "(")); i++ }
                ch == ')' -> { out.add(Tok(T.RP, ")")); i++ }
                ch == ',' -> { out.add(Tok(T.COMMA, ",")); i++ }
                else -> throw ExpressionException("无法识别的字符: $ch")
            }
        }
        return out
    }

    private class Parser(val toks: List<Tok>) {
        var pos = 0
        fun atEnd() = pos >= toks.size
        fun peek(): Tok? = toks.getOrNull(pos)
        fun peekText(): String = peek()?.text ?: "结尾"
        fun next(): Tok = toks[pos++]

        fun parseExpression(): Node {
            var left = parseTerm()
            while (peek()?.type == T.OP && (peek()!!.text == "+" || peek()!!.text == "-")) {
                val op = next().text
                val right = parseTerm()
                left = if (op == "+") Add(left, right) else Sub(left, right)
            }
            return left
        }

        fun parseTerm(): Node {
            var left = parseUnary()
            while (true) {
                val p = peek()
                if (p?.type == T.OP && (p.text == "*" || p.text == "/")) {
                    next()
                    val right = parseUnary()
                    left = if (p.text == "*") Mul(left, right) else Div(left, right)
                } else if (p?.type == T.NUM || p?.type == T.IDENT || p?.type == T.LP) {
                    // 隐式乘法：2x、2(x+1)、x(x+1)
                    val right = parseUnary()
                    left = Mul(left, right)
                } else break
            }
            return left
        }

        fun parseUnary(): Node {
            val p = peek()
            if (p?.type == T.OP && p.text == "-") { next(); return Neg(parseUnary()) }
            if (p?.type == T.OP && p.text == "+") { next(); return parseUnary() }
            return parsePower()
        }

        fun parsePower(): Node {
            val base = parsePrimary()
            val p = peek()
            if (p?.type == T.OP && p.text == "^") {
                next()
                return Pow(base, parseUnary())
            }
            return base
        }

        fun parsePrimary(): Node {
            val p = peek() ?: throw ExpressionException("表达式意外结束")
            return when (p.type) {
                T.NUM -> { next(); Const(p.text.toDouble()) }
                T.IDENT -> {
                    next()
                    if (p.text in FUNCS && peek()?.type == T.LP) {
                        next()
                        val args = mutableListOf(parseExpression())
                        while (peek()?.type == T.COMMA) { next(); args.add(parseExpression()) }
                        if (peek()?.type != T.RP) throw ExpressionException("缺少右括号")
                        next()
                        Func(p.text, args)
                    } else Var
                }
                T.LP -> { next(); val e = parseExpression(); if (peek()?.type != T.RP) throw ExpressionException("缺少右括号"); next(); e }
                else -> throw ExpressionException("意外的符号: ${p.text}")
            }
        }
    }

    private interface Node { fun eval(x: Double): Double }
    private data class Const(val v: Double) : Node { override fun eval(x: Double) = v }
    private object Var : Node { override fun eval(x: Double) = x }
    private data class Neg(val a: Node) : Node { override fun eval(x: Double) = -a.eval(x) }
    private data class Add(val a: Node, val b: Node) : Node { override fun eval(x: Double) = a.eval(x) + b.eval(x) }
    private data class Sub(val a: Node, val b: Node) : Node { override fun eval(x: Double) = a.eval(x) - b.eval(x) }
    private data class Mul(val a: Node, val b: Node) : Node { override fun eval(x: Double) = a.eval(x) * b.eval(x) }
    private data class Div(val a: Node, val b: Node) : Node { override fun eval(x: Double) = a.eval(x) / b.eval(x) }
    private data class Pow(val a: Node, val b: Node) : Node { override fun eval(x: Double) = Math.pow(a.eval(x), b.eval(x)) }

    private data class Func(val name: String, val args: List<Node>) : Node {
        override fun eval(x: Double): Double {
            val v = args.map { it.eval(x) }
            return when (name) {
                "sin" -> Math.sin(v[0])
                "cos" -> Math.cos(v[0])
                "tan" -> Math.tan(v[0])
                "asin" -> Math.asin(v[0])
                "acos" -> Math.acos(v[0])
                "atan" -> Math.atan(v[0])
                "sinh" -> Math.sinh(v[0])
                "cosh" -> Math.cosh(v[0])
                "tanh" -> Math.tanh(v[0])
                "ln" -> Math.log(v[0])
                "log" -> Math.log10(v[0])
                "log2" -> Math.log(v[0]) / Math.log(2.0)
                "sqrt" -> Math.sqrt(v[0])
                "abs" -> Math.abs(v[0])
                "exp" -> Math.exp(v[0])
                "floor" -> Math.floor(v[0])
                "ceil" -> Math.ceil(v[0])
                "min" -> minOf(v[0], v[1])
                "max" -> maxOf(v[0], v[1])
                "pow" -> Math.pow(v[0], v[1])
                else -> throw ExpressionException("未知函数: $name")
            }
        }
    }
}