package com.ueiol.yixue.domain.grading

import com.ueiol.yixue.domain.model.AppJson
import com.ueiol.yixue.domain.plot.ExpressionException
import com.ueiol.yixue.domain.plot.ExpressionParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * 本地判分引擎（规划书 3.3）：只做高置信判定，低置信交 AI 复核。
 */
object GradingEngine {

    enum class Verdict { CORRECT, WRONG, UNCERTAIN }

    @Serializable
    data class FillAnswer(val answer: String, val accepted: List<String> = emptyList())

    @Serializable
    data class ChoiceAnswer(val letters: List<String>)

    fun encodeChoice(letters: List<String>): String =
        AppJson.encodeToString(com.ueiol.yixue.domain.model.StringListSerializer, letters.map { it.uppercase() })

    fun encodeFill(answer: String, accepted: List<String>): String {
        val all = buildList {
            add(answer)
            addAll(accepted.filter { it.isNotBlank() })
        }.distinct()
        return AppJson.encodeToString(FillAnswer.serializer(), FillAnswer(answer, all))
    }

    fun decodeAnswer(answerJson: String, isChoice: Boolean): Pair<List<String>, List<String>> {
        // 返回 (选择题字母列表, 填空可接受答案列表)
        if (isChoice) {
            return try {
                AppJson.decodeFromString(com.ueiol.yixue.domain.model.StringListSerializer, answerJson)
            } catch (t: Throwable) {
                listOf(answerJson.trim().uppercase())
            } to emptyList()
        }
        val fill = runCatching { AppJson.decodeFromString(FillAnswer.serializer(), answerJson) }.getOrNull()
        return emptyList<String>() to (fill?.accepted ?: listOf(answerJson))
    }

    /** 统一入口：人类可读的正确答案文本（选择题字母串 / 填空首个可接受答案）。 */
    fun correctAnswerText(answerJson: String, type: String): String {
        val (letters, accepted) = decodeAnswer(answerJson, type != "FILL_BLANK")
        return if (letters.isNotEmpty()) letters.joinToString(" ") else (accepted.firstOrNull() ?: "")
    }

    fun grade(type: String, answerJson: String, studentValue: String?): Verdict {
        if (studentValue.isNullOrBlank()) return Verdict.WRONG
        return if (type == "FILL_BLANK") gradeFill(answerJson, studentValue)
        else gradeChoice(answerJson, studentValue)
    }

    private fun gradeChoice(answerJson: String, student: String): Verdict {
        val correct = decodeAnswer(answerJson, true).first.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.sorted()
        val studentLetters = student.uppercase().filter { it in 'A'..'D' }.toSet().sorted()
        return if (correct.isEmpty()) Verdict.UNCERTAIN
        else if (studentLetters == correct) Verdict.CORRECT else Verdict.WRONG
    }

    private fun gradeFill(answerJson: String, studentRaw: String): Verdict {
        val accepted = decodeAnswer(answerJson, false).second
        // 先把 LaTeX 转成 Unicode，再归一化，保证键盘输入的 \frac{} 等模板可比
        val student = normalize(com.ueiol.yixue.domain.latex.LatexConverter.toUnicode(studentRaw))
        if (student.isEmpty()) return Verdict.WRONG
        for (ans in accepted) {
            val a = normalize(com.ueiol.yixue.domain.latex.LatexConverter.toUnicode(ans))
            if (a.isEmpty()) continue
            if (a == student) return Verdict.CORRECT
            // 数值/表达式等价：两个都可用解析器求值时在样本点比较
            if (numericEquivalent(a, student)) return Verdict.CORRECT
        }
        // 学生答案能算出一个数、标准答案也是数但不等 → 高置信判错
        val stdVal = constantValue(com.ueiol.yixue.domain.latex.LatexConverter.toUnicode(accepted.firstOrNull() ?: ""))
        val stuVal = constantValue(com.ueiol.yixue.domain.latex.LatexConverter.toUnicode(studentRaw))
        if (stdVal != null && stuVal != null) return Verdict.WRONG
        return Verdict.UNCERTAIN
    }

    private fun normalize(s: String): String {
        var t = s.trim()
        val sb = StringBuilder()
        t.forEach { c ->
            sb.append(
                when (c) {
                    '（' -> '('; '）' -> ')'; '，' -> ','; '＋' -> '+'; '－' -> '-'
                    '：' -> ':'; '。' -> '.'; ' ' -> ' '
                    else -> c
                }
            )
        }
        return sb.toString().replace(" ", "").lowercase()
    }

    private fun constantValue(expr: String): Double? = try {
        val f = ExpressionParser.compile(expr)
        // 含变量 x 的表达式不算常量
        val a = f(1.0); val b = f(2.0)
        if (a == b) a else null
    } catch (t: Throwable) {
        null
    }

    private fun numericEquivalent(std: String, student: String): Boolean {
        val f1 = try { ExpressionParser.compile(std) } catch (e: ExpressionException) { return false }
        val f2 = try { ExpressionParser.compile(student) } catch (e: ExpressionException) { return false }
        val samples = listOf(0.5, 1.0, 2.0, -1.5, 3.7)
        return samples.all { k ->
            val a = runCatching { f1(k) }.getOrNull()
            val b = runCatching { f2(k) }.getOrNull()
            a != null && b != null && (a.isNaN() && b.isNaN() || Math.abs(a - b) < 1e-6 * (1 + Math.abs(a)))
        }
    }
}