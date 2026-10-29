package cn.xinxitong.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluatorTest {
    private fun value(expression: String): String {
        val result = evaluateExpression(expression)
        check(result is EvalResult.Success) { "expected success for $expression, got $result" }
        return result.formatted
    }

    @Test
    fun precedenceIsCorrect() {
        assertEquals("14", value("2+3*4"))
        assertEquals("20", value("(2+3)*4"))
        assertEquals("2", value("10-4*2"))
        assertEquals("5", value("10/2"))
        assertEquals("1", value("10-3*3"))
    }

    @Test
    fun parenthesesNest() {
        assertEquals("20", value("((2+3))*4"))
        assertEquals("7", value("2*(3+1)-1"))
    }

    @Test
    fun percentDividesBy100() {
        assertEquals("0.5", value("50%"))
        assertEquals("200.1", value("200+10%"))
        assertEquals("100", value("200*50%"))
    }

    @Test
    fun unaryMinusWorks() {
        assertEquals("-2", value("-5+3"))
        assertEquals("-6", value("2*-3"))
        assertEquals("5", value("--5"))
        assertEquals("8", value("5--3"))
    }

    @Test
    fun divisionByZeroIsError() {
        val result = evaluateExpression("1/0")
        check(result is EvalResult.Failure)
        assertEquals("除数不能为零", result.message)
        check(evaluateExpression("1/(2-2)") is EvalResult.Failure)
    }

    @Test
    fun malformedExpressionsAreErrors() {
        assertTrue(evaluateExpression("") is EvalResult.Failure)
        assertTrue(evaluateExpression("2+") is EvalResult.Failure)
        assertTrue(evaluateExpression("(2+3") is EvalResult.Failure)
        assertTrue(evaluateExpression("2..3") is EvalResult.Failure)
        assertTrue(evaluateExpression("2+") is EvalResult.Failure)
        assertTrue(evaluateExpression("abc") is EvalResult.Failure)
        assertTrue(evaluateExpression("2+*3") is EvalResult.Failure)
    }

    @Test
    fun decimalsAndDisplaySymbols() {
        assertEquals("0.3", value("0.1+0.2"))
        assertEquals("14", value("2×3+8"))
        assertEquals("5", value("10÷2"))
        assertEquals("2.5", value("5/2"))
        assertEquals("2", value("4.0"))
    }

    @Test
    fun appendKeyPreventsInvalidInput() {
        assertEquals("-", appendKey("", "-"))
        assertEquals("", appendKey("", "+"))
        assertEquals("2+3", appendKey("2+", "3"))
        assertEquals("2-3", appendKey("2+", "-"))
        assertEquals("2*-3", appendKey("2*", "-"))
        assertEquals("1.5", appendKey("1.5", "."))
        assertEquals("1.5+", appendKey("1.5", "+"))
        assertEquals("2*(3)", appendKey("2", "("))
        assertEquals("(2+3)", appendKey("(2+3", ")"))
    }

    @Test
    fun isCompleteExpressionChecks() {
        assertTrue(isCompleteExpression("2+3"))
        assertFalse(isCompleteExpression("2+"))
        assertFalse(isCompleteExpression("(2+3"))
        assertFalse(isCompleteExpression(""))
        assertFalse(isCompleteExpression("1/0"))
    }

    @Test
    fun historyDedupKeepsNewestAndCaps() {
        val entries = listOf(
            CalculationHistory("1+1", "2", 100),
            CalculationHistory("1+1", "2", 300),
            CalculationHistory("2+2", "4", 200),
        )
        val result = dedupCalculatorHistory(entries)
        assertEquals(listOf("1+1", "2+2"), result.map { it.expression })
        val many = (1..60).map { CalculationHistory("e$it", "r", it.toLong()) }
        assertEquals(50, dedupCalculatorHistory(many).size)
    }
}
