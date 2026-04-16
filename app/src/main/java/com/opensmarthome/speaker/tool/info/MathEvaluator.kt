package com.opensmarthome.speaker.tool.info

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Safely evaluates arithmetic expressions: + - * / ^ % and parentheses.
 * Supports a small set of functions: sqrt, abs, round, floor, ceil.
 *
 * Uses shunting-yard + RPN evaluation. No reflection, no script engine,
 * no unsafe eval — can't execute arbitrary code.
 */
class MathEvaluator {

    sealed class Result {
        data class Ok(val value: Double) : Result()
        data class ParseError(val reason: String) : Result()
        data class EvalError(val reason: String) : Result()
    }

    fun eval(expr: String): Result {
        val tokens = try {
            tokenize(expr)
        } catch (e: Exception) {
            return Result.ParseError(e.message ?: "tokenize failed")
        }
        val rpn = try {
            toRpn(tokens)
        } catch (e: Exception) {
            return Result.ParseError(e.message ?: "parse failed")
        }
        return try {
            Result.Ok(evaluate(rpn))
        } catch (e: Exception) {
            Result.EvalError(e.message ?: "eval failed")
        }
    }

    private sealed class Token {
        data class Num(val value: Double) : Token()
        data class Op(val sym: Char) : Token()
        data class Func(val name: String) : Token()
        object LParen : Token()
        object RParen : Token()
        object Comma : Token()
    }

    private fun tokenize(expr: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < expr.length) {
            val c = expr[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    val start = i
                    while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) i++
                    tokens.add(Token.Num(expr.substring(start, i).toDouble()))
                }
                c.isLetter() -> {
                    val start = i
                    while (i < expr.length && expr[i].isLetter()) i++
                    tokens.add(Token.Func(expr.substring(start, i).lowercase()))
                }
                c in "+-*/^%" -> {
                    tokens.add(Token.Op(c)); i++
                }
                c == '(' -> {
                    tokens.add(Token.LParen); i++
                }
                c == ')' -> {
                    tokens.add(Token.RParen); i++
                }
                c == ',' -> {
                    tokens.add(Token.Comma); i++
                }
                else -> throw RuntimeException("Unknown char: $c")
            }
        }
        return tokens
    }

    private fun precedence(op: Char): Int = when (op) {
        '+', '-' -> 1
        '*', '/', '%' -> 2
        '^' -> 3
        '_' -> 4 // unary minus — binds tighter than any binary op
        else -> 0
    }

    private fun rightAssociative(op: Char): Boolean = op == '^' || op == '_'

    private fun toRpn(tokens: List<Token>): List<Token> {
        val output = mutableListOf<Token>()
        val stack = ArrayDeque<Token>()

        // Rewrite '-' as unary '_' when it follows nothing, an operator, a '(' , or a ','
        var prev: Token? = null
        for (t in tokens) {
            val effective: Token = if (t is Token.Op && t.sym == '-' &&
                (prev == null || prev is Token.Op || prev is Token.LParen || prev is Token.Comma)
            ) {
                Token.Op('_')
            } else t
            when (effective) {
                is Token.Num -> output.add(effective)
                is Token.Func -> stack.addFirst(effective)
                is Token.Comma -> {
                    while (stack.isNotEmpty() && stack.first() !is Token.LParen) {
                        output.add(stack.removeFirst())
                    }
                }
                is Token.Op -> {
                    while (stack.isNotEmpty()) {
                        val top = stack.first()
                        if (top is Token.Op) {
                            val a = precedence(top.sym)
                            val b = precedence(effective.sym)
                            if (a > b || (a == b && !rightAssociative(effective.sym))) {
                                output.add(stack.removeFirst())
                            } else break
                        } else if (top is Token.Func) {
                            output.add(stack.removeFirst())
                        } else break
                    }
                    stack.addFirst(effective)
                }
                is Token.LParen -> stack.addFirst(effective)
                is Token.RParen -> {
                    while (stack.isNotEmpty() && stack.first() !is Token.LParen) {
                        output.add(stack.removeFirst())
                    }
                    if (stack.isEmpty()) throw RuntimeException("Mismatched paren")
                    stack.removeFirst() // pop '('
                    if (stack.isNotEmpty() && stack.first() is Token.Func) {
                        output.add(stack.removeFirst())
                    }
                }
            }
            prev = effective
        }
        while (stack.isNotEmpty()) {
            val top = stack.removeFirst()
            if (top is Token.LParen || top is Token.RParen) throw RuntimeException("Mismatched paren")
            output.add(top)
        }
        return output
    }

    private fun evaluate(rpn: List<Token>): Double {
        val stack = ArrayDeque<Double>()
        for (t in rpn) {
            when (t) {
                is Token.Num -> stack.addFirst(t.value)
                is Token.Op -> {
                    if (t.sym == '_') {
                        val x = stack.removeFirst()
                        stack.addFirst(-x)
                    } else {
                        val b = stack.removeFirst()
                        val a = stack.removeFirst()
                        val v = when (t.sym) {
                            '+' -> a + b
                            '-' -> a - b
                            '*' -> a * b
                            '/' -> if (b == 0.0) throw RuntimeException("Division by zero") else a / b
                            '%' -> a % b
                            '^' -> a.pow(b)
                            else -> throw RuntimeException("Unknown op: ${t.sym}")
                        }
                        stack.addFirst(v)
                    }
                }
                is Token.Func -> {
                    val arg = stack.removeFirst()
                    val v = when (t.name) {
                        "sqrt" -> sqrt(arg)
                        "abs" -> kotlin.math.abs(arg)
                        "round" -> kotlin.math.round(arg)
                        "floor" -> kotlin.math.floor(arg)
                        "ceil" -> kotlin.math.ceil(arg)
                        else -> throw RuntimeException("Unknown function: ${t.name}")
                    }
                    stack.addFirst(v)
                }
                else -> throw RuntimeException("Unexpected token")
            }
        }
        if (stack.size != 1) throw RuntimeException("Expression did not reduce to single value")
        return stack.first()
    }
}
