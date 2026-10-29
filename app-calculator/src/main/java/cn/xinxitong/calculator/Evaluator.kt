package cn.xinxitong.calculator

import java.math.BigDecimal
import java.math.RoundingMode

/** 计算结果：成功带格式化后的数字文本，失败带中文错误原因。 */
sealed class EvalResult {
    data class Success(val value: Double, val formatted: String) : EvalResult()
    data class Failure(val message: String) : EvalResult()
}

private class ParseException(message: String) : Exception(message)

/**
 * 真实的表达式求值器（递归下降，优先级正确）：
 *
 *   表达式 := 项 (('+'|'-') 项)*
 *   项     := 因子 (('*'|'/'|'×'|'÷') 因子)*
 *   因子   := 一元表达式 ('%' 后缀)*
 *   一元   := ('+'|'-') 一元 | 基本
 *   基本   := 数字 | '(' 表达式 ')'
 *
 * 百分号是后缀运算符，含义是“除以 100”（如 50% = 0.5，200+10% = 200.1）。
 * 支持显示符号 × ÷ −（键盘显示用），内部统一换算。纯函数，可单元测试。
 */
fun evaluateExpression(rawInput: String): EvalResult {
    val input = rawInput
        .replace('×', '*')
        .replace('÷', '/')
        .replace('−', '-')
        .replace(" ", "")
    if (input.isBlank()) return EvalResult.Failure("请输入表达式")
    return try {
        val parser = Parser(input)
        val value = parser.parseExpression()
        parser.requireEnd()
        if (value.isNaN() || value.isInfinite()) {
            EvalResult.Failure("结果超出范围")
        } else {
            EvalResult.Success(value, formatResult(value))
        }
    } catch (e: ParseException) {
        EvalResult.Failure(e.message ?: "表达式有误")
    }
}

/** 数字结果格式化：整数不带小数点；小数保留最多 10 位并去掉末尾 0。纯函数。 */
fun formatResult(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return "错误"
    val rounded = BigDecimal(value).setScale(10, RoundingMode.HALF_UP).stripTrailingZeros()
    return rounded.toPlainString()
}

private class Parser(private val input: String) {
    private var pos = 0

    private fun peek(): Char? = if (pos < input.length) input[pos] else null

    private fun consume(): Char? = peek()?.also { pos++ }

    private fun expect(ch: Char) {
        if (peek() != ch) throw ParseException("表达式有误")
        pos++
    }

    fun requireEnd() {
        if (pos != input.length) throw ParseException("表达式有误")
    }

    fun parseExpression(): Double {
        var value = parseTerm()
        while (true) {
            when (peek()) {
                '+' -> { consume(); value += parseTerm() }
                '-' -> { consume(); value -= parseTerm() }
                else -> return value
            }
        }
    }

    private fun parseTerm(): Double {
        var value = parseFactor()
        while (true) {
            when (peek()) {
                '*' -> { consume(); value *= parseFactor() }
                '/' -> {
                    consume()
                    val divisor = parseFactor()
                    if (divisor == 0.0) throw ParseException("除数不能为零")
                    value /= divisor
                }
                else -> return value
            }
        }
    }

    private fun parseFactor(): Double {
        var value = parseUnary()
        while (peek() == '%') {
            consume()
            value /= 100.0
        }
        return value
    }

    private fun parseUnary(): Double {
        return when (peek()) {
            '+' -> { consume(); parseUnary() }
            '-' -> { consume(); -parseUnary() }
            else -> parsePrimary()
        }
    }

    private fun parsePrimary(): Double {
        val ch = peek() ?: throw ParseException("表达式有误")
        if (ch == '(') {
            consume()
            val value = parseExpression()
            expect(')')
            return value
        }
        if (ch.isDigit() || ch == '.') return parseNumber()
        throw ParseException("表达式有误")
    }

    private fun parseNumber(): Double {
        val start = pos
        var dotCount = 0
        while (true) {
            val ch = peek() ?: break
            if (ch.isDigit()) {
                consume()
            } else if (ch == '.') {
                dotCount++
                if (dotCount > 1) throw ParseException("表达式有误")
                consume()
            } else {
                break
            }
        }
        val text = input.substring(start, pos)
        if (text.isEmpty() || text == ".") throw ParseException("表达式有误")
        return text.toDoubleOrNull() ?: throw ParseException("表达式有误")
    }
}

/**
 * 键盘按键追加逻辑：把按键并入当前表达式，避免出现非法的连续运算符、
 * 同一数字内多个小数点、以及空表达式时以运算符开头（负号除外）。
 * 纯函数，可单元测试。
 */
fun appendKey(expression: String, key: String): String {
    val normalizedKey = when (key) {
        "×" -> "*"
        "÷" -> "/"
        else -> key
    }
    if (expression.isEmpty()) {
        return when (normalizedKey) {
            "+", "*", "/", "%", ")", "." -> expression
            "-" -> "-"
            else -> normalizedKey
        }
    }
    val last = expression.last()
    val operators = setOf('+', '-', '*', '/', '%')
    return when {
        normalizedKey in setOf("+", "-", "*", "/") -> {
            if (last in operators || last == '(') {
                // 连续运算符：替换末尾运算符；但允许 "(-" 形式的负号。
                if (normalizedKey == "-" && (last == '*' || last == '/' || last == '(')) {
                    expression + normalizedKey
                } else if (last == '(' && normalizedKey != "-") {
                    expression
                } else {
                    expression.dropLast(1) + normalizedKey
                }
            } else {
                expression + normalizedKey
            }
        }
        normalizedKey == "." -> {
            // 当前数字段已有小数点则忽略。
            val currentNumber = expression.takeLastWhile { it.isDigit() || it == '.' }
            if (currentNumber.contains('.')) expression else expression + "."
        }
        normalizedKey == "(" -> {
            if (last.isDigit() || last == ')' || last == '%') expression + "*" + "(" else expression + "("
        }
        normalizedKey == ")" -> {
            val open = expression.count { it == '(' }
            val close = expression.count { it == ')' }
            if (open > close && (last.isDigit() || last == ')' || last == '%')) expression + ")" else expression
        }
        normalizedKey == "%" -> {
            if (last.isDigit() || last == ')') expression + "%" else expression
        }
        else -> expression + normalizedKey
    }
}

/** 退格：删掉最后一个字符。纯函数。 */
fun backspace(expression: String): String = expression.dropLast(1)

/** 判断表达式是否“看起来完整”（可求值且末尾不是运算符/左括号）。纯函数。 */
fun isCompleteExpression(expression: String): Boolean {
    if (expression.isBlank()) return false
    val last = expression.last()
    if (last == '+' || last == '-' || last == '*' || last == '/' || last == '(' || last == '.') return false
    val open = expression.count { it == '(' }
    val close = expression.count { it == ')' }
    if (open != close) return false
    return evaluateExpression(expression) is EvalResult.Success
}
