package com.ueiol.yixue

import com.ueiol.yixue.domain.plot.ExpressionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExpressionParserTest {

    private fun eval(expr: String, x: Double = 1.0): Double = ExpressionParser.compile(expr)(x)

    @Test fun basic_arithmetic() {
        assertEquals(7.0, eval("2+3*2-1"), 1e-9)
        assertEquals(8.0, eval("2^3"), 1e-9)
        assertEquals(0.5, eval("1/2"), 1e-9)
        assertEquals(-5.0, eval("-2*3+1"), 1e-9)
    }

    @Test fun implicit_multiplication() {
        assertEquals(6.0, eval("2x", x = 3.0), 1e-9)
        assertEquals(6.0, eval("2(x)", x = 3.0), 1e-9)
        assertEquals(12.0, eval("2x(x+1)", x = 2.0), 1e-9)
        assertEquals(4.0, eval("x^2", x = 2.0), 1e-9)
        assertEquals(16.0, eval("2x^3", x = 2.0), 1e-9)
    }

    @Test fun functions() {
        assertEquals(0.0, eval("sin(0)"), 1e-9)
        assertEquals(1.0, eval("cos(0)"), 1e-9)
        assertEquals(2.0, eval("sqrt(4)"), 1e-9)
        assertEquals(1.0, eval("ln(e)"), 1e-9)
        assertEquals(2.0, eval("log(100)"), 1e-9)
        assertEquals(3.0, eval("abs(-3)"), 1e-9)
        assertEquals(5.0, eval("min(5,9)"), 1e-9)
        assertEquals(9.0, eval("max(5,9)"), 1e-9)
        assertEquals(3.141592653589793, eval("pi"), 1e-9)
    }

    @Test fun right_assoc_power_and_unary() {
        assertEquals(256.0, eval("2^2^3"), 1e-9)
        assertEquals(0.25, eval("2^-2"), 1e-9)
        assertEquals(-4.0, eval("-2^2"), 1e-9)
        assertEquals(4.0, eval("(-2)^2"), 1e-9)
    }

    @Test fun evaluated_repeatedly() {
        val f = ExpressionParser.compile("x^2+1")
        assertEquals(2.0, f(1.0), 1e-9)
        assertEquals(5.0, f(2.0), 1e-9)
        assertEquals(26.0, f(-5.0), 1e-9)
    }

    @Test fun invalid_expressions_throw() {
        assertThrows(Exception::class.java) { ExpressionParser.compile("2+") }
        assertThrows(Exception::class.java) { ExpressionParser.compile("(1+2") }
        assertThrows(Exception::class.java) { ExpressionParser.compile("foo(2)") }
        assertThrows(Exception::class.java) { ExpressionParser.compile("") }
    }

    @Test fun chinese_operators_normalized() {
        assertEquals(6.0, eval("2×3"), 1e-9)
        assertEquals(2.0, eval("6÷3"), 1e-9)
    }
}