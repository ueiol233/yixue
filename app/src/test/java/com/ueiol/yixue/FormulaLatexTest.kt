package com.ueiol.yixue

import com.ueiol.yixue.domain.plot.ExpressionParser
import com.ueiol.yixue.ui.common.MarkdownParser
import com.ueiol.yixue.ui.common.MdBlock
import com.ueiol.yixue.ui.common.PlotParser
import com.ueiol.yixue.ui.formula.FormulaLatex
import com.ueiol.yixue.ui.formula.FormulaState
import com.ueiol.yixue.ui.formula.toExpression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormulaLatexTest {

    @Test
    fun fraction() {
        val s = FormulaLatex.parse("\\frac{1}{2}")
        assertNotNull(s)
        assertEquals("\\frac{1}{2}", s!!.toLatex())
    }

    @Test
    fun nestedSqrtAndPower() {
        val s = FormulaLatex.parse("\\sqrt{x^{2}+1}")
        assertNotNull(s)
        assertEquals("\\sqrt{{x}^{2}+1}", s!!.toLatex())
    }

    @Test
    fun subscript() {
        val s = FormulaLatex.parse("a_{n}")
        assertNotNull(s)
        assertEquals("{a}_{n}", s!!.toLatex())
    }

    @Test
    fun unknownCommandFallsBack() {
        assertNull(FormulaLatex.parse("\\Alpha"))
    }

    @Test
    fun mixedExpression() {
        val s = FormulaLatex.parse("\\frac{1}{2} + \\sqrt{3}")
        assertNotNull(s)
        assertEquals("\\frac{1}{2} + \\sqrt{3}", s!!.toLatex())
    }

    @Test
    fun alignedEnvironmentDoesNotFallBack() {
        val s = FormulaLatex.parse("\\begin{aligned}x&=1\\\\y&=2\\end{aligned}")
        assertNotNull(s)
    }

    @Test
    fun singleLineLatexBlockDoesNotSwallowFollowingText() {
        val dd = "${'$'}${'$'}"
        val blocks = MarkdownParser.parse(dd + "x^2" + dd + "\n后面的正文")
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is MdBlock.BlockLatex)
        assertEquals("x^2", (blocks[0] as MdBlock.BlockLatex).latex)
        assertTrue(blocks[1] is MdBlock.Paragraph)
        assertEquals("后面的正文", (blocks[1] as MdBlock.Paragraph).text)
    }

    @Test
    fun standaloneInlineMathBecomesBlock() {
        val d = "${'$'}"
        val blocks = MarkdownParser.parse(d + "x^2" + d)
        assertEquals(1, blocks.size)
        assertEquals("x^2", (blocks[0] as MdBlock.BlockLatex).latex)
    }

    @Test
    fun moneyIsNotTreatedAsMath() {
        val d = "${'$'}"
        val blocks = MarkdownParser.parse(d + "5 和 " + d + "10")
        assertTrue(blocks[0] is MdBlock.Paragraph)
    }

    @Test
    fun tableWithSeparator() {
        val blocks = MarkdownParser.parse("| 项目 | 数值 |\n|---|---|\n| 甲 | 1 |")
        assertEquals(1, blocks.size)
        val t = blocks[0] as MdBlock.Table
        assertEquals(listOf("项目", "数值"), t.header)
        assertEquals(listOf(listOf("甲", "1")), t.rows)
    }

    @Test
    fun tableWithoutSeparatorAndTrailingPipes() {
        val blocks = MarkdownParser.parse("| 项目 | 数值\n| 甲 | 1\n| 乙 | 2")
        assertEquals(1, blocks.size)
        val t = blocks[0] as MdBlock.Table
        assertEquals(2, t.header.size)
        assertEquals(2, t.rows.size)
    }

    @Test
    fun singleDashSeparatorIsAccepted() {
        val blocks = MarkdownParser.parse("| a | b |\n| - | - |\n| 1 | 2 |")
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MdBlock.Table)
    }

    @Test
    fun visualFormulaConvertsToComputableExpression() {
        val state = FormulaState()
        state.insertText("x")
        state.insertPow()
        state.insertText("2")
        val expr = state.toExpression()
        assertEquals("(x)^(2)", expr)
        // 转出来的表达式必须能被绘图解析器直接编译
        val fn = ExpressionParser.compile(expr)
        assertEquals(9.0, fn(3.0), 1e-9)
    }

    /** 用户按键序列 y = x → xʸ → 2：只有末字符 x 抬为底数，而不是整个 y=x。 */
    @Test
    fun powLiftsOnlyLastChar() {
        val s = FormulaState()
        s.insertText("y")
        s.insertText("=")
        s.insertText("x")
        s.insertPow()
        s.insertText("2")
        assertEquals("y={x}^{2}", s.toLatex())
        // 函数图场景：去掉 y= 后必须可编译求值
        val fn = ExpressionParser.compile(s.toExpression().removePrefix("y="))
        assertEquals(4.0, fn(2.0), 1e-9)
    }

    /** 括号组整体抬为底数：(x+1) → xʸ → 2 = (x+1)²。 */
    @Test
    fun powLiftsBalancedBracketGroup() {
        val s = FormulaState()
        s.insertText("(x+1)")
        s.insertPow()
        s.insertText("2")
        assertEquals("{(x+1)}^{2}", s.toLatex())
        val fn = ExpressionParser.compile(s.toExpression())
        assertEquals(9.0, fn(2.0), 1e-9)
    }

    /** 函数名整体抬为底数：sin → xʸ → 2 = sin²（FPow 模板恒为 {底}^{指}）。 */
    @Test
    fun powLiftsFunctionName() {
        val s = FormulaState()
        s.insertText("sin")
        s.insertPow()
        s.insertText("2")
        assertEquals("{sin}^{2}", s.toLatex())
    }

    /** 在指数槽内继续按 xʸ：作用于指数槽的最后一个单元，得 x^{2^3}。 */
    @Test
    fun powInsideExponentNests() {
        val s = FormulaState()
        s.insertText("x")
        s.insertPow()
        s.insertText("2")
        s.insertPow()
        s.insertText("3")
        assertEquals("{x}^{{2}^{3}}", s.toLatex())
    }

    /** 结构节点整体抬为底数：回到根槽位后再按 xʸ，得 (x²)³。 */
    @Test
    fun powLiftsWholeStructureNode() {
        val s = FormulaState()
        s.insertText("x")
        s.insertPow()
        s.insertText("2")
        s.focus(0)
        s.insertPow()
        s.insertText("3")
        assertEquals("{{x}^{2}}^{3}", s.toLatex())
    }

    /** 下标与幂同规则：an → xₙ → 1 = a 下标 n。 */
    @Test
    fun subLiftsOnlyLastChar() {
        val s = FormulaState()
        s.insertText("an")
        s.insertSub()
        s.insertText("1")
        assertEquals("a{n}_{1}", s.toLatex())
    }

    /** 运算符不作底数：y= 后按 xʸ，底数留空占位。 */
    @Test
    fun operatorIsNotLiftedAsBase() {
        val s = FormulaState()
        s.insertText("y=")
        s.insertPow()
        assertEquals("y={}^{}", s.toLatex())
    }

    /** MathQuill charsThatBreakOutOfSupSub：指数里输入 + 跳出，得 x²+1 而非 x^{2+1}。 */
    @Test
    fun plusBreaksOutOfExponent() {
        val s = FormulaState()
        s.insertText("x")
        s.insertPow()
        s.insertText("2")
        s.insertText("+")
        s.insertText("1")
        assertEquals("{x}^{2}+1", s.toLatex())
    }

    /** 误按 xʸ 的撤销：第一次退格从空指数回根槽（结构保留），第二次删结构并退回底数。 */
    @Test
    fun backspaceRemovesEmptyStructure() {
        val s = FormulaState()
        s.insertText("y=x")
        s.insertPow()
        s.backspace() // 指数为空但底数有 x → 结构保留，光标回根槽位
        assertEquals("y={x}^{}", s.toLatex())
        s.backspace() // 删整个幂结构，底数 x 退回
        assertEquals("y=x", s.toLatex())
    }

    /** 空底数 + 空指数的结构一次退格即整体消失。 */
    @Test
    fun backspaceRemovesFullyEmptyStructure() {
        val s = FormulaState()
        s.insertText("y=")
        s.insertPow()
        s.backspace()
        assertEquals("y=", s.toLatex())
    }

    /** 删除结构时纯文本底数退回原位：x² 连按三次退格回到 x。 */
    @Test
    fun backspaceInlinesTextBase() {
        val s = FormulaState()
        s.insertText("x")
        s.insertPow()
        s.insertText("2")
        s.backspace() // 删指数里的 2
        s.backspace() // 空指数 → 回根槽，结构非空（底数有 x）保留
        s.backspace() // 删整个幂结构，底数 x 退回
        assertEquals("x", s.toLatex())
    }

    /** ◀▶ 按渲染顺序在槽位间移动：指数 → 底数 → 根槽位，根槽位处停住。 */
    @Test
    fun moveFocusNavigatesRenderOrder() {
        val s = FormulaState()
        s.insertText("x")
        s.insertPow()
        val exp = s.focused
        s.moveFocus(-1)
        val base = s.focused
        s.moveFocus(-1)
        assertEquals(0, s.focused)
        s.moveFocus(-1)
        assertEquals(0, s.focused)
        s.moveFocus(1)
        assertEquals(base, s.focused)
        s.moveFocus(1)
        assertEquals(exp, s.focused)
    }

    @Test
    fun plotParserStripsLeadingYEquals() {
        val spec = PlotParser.parseLines(listOf("y=x^2", "y = x^2", "f(x)=x^2"))
        assertNotNull(spec)
        assertEquals(listOf("x^2", "x^2", "x^2"), spec!!.expressions)
        // 去除 y= 之后表达式应当可以编译（此前空白图的根因）
        val fn = ExpressionParser.compile(spec.expressions[0])
        assertEquals(4.0, fn(2.0), 1e-9)
    }

    @Test
    fun plotParserKeepsCommaInsideFunctionAndReadsRange() {
        val spec = PlotParser.parseLines(listOf("y=max(x,0), x=-3..3"))
        assertNotNull(spec)
        assertEquals(listOf("max(x,0)"), spec!!.expressions)
        assertEquals(-3.0, spec.xMin, 1e-9)
        assertEquals(3.0, spec.xMax, 1e-9)
    }

    @Test
    fun bracketDelimitersAreNormalized() {
        val blocks = MarkdownParser.parse("\\[\\frac{1}{2}\\]")
        assertEquals(1, blocks.size)
        assertEquals("\\frac{1}{2}", (blocks[0] as MdBlock.BlockLatex).latex)

        val inline = MarkdownParser.parse("行内 \\(x^2\\) 结束")
        assertEquals(1, inline.size)
        assertEquals("行内 \$x^2\$ 结束", (inline[0] as MdBlock.Paragraph).text)
    }
}
