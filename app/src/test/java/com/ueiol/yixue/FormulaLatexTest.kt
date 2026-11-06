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
