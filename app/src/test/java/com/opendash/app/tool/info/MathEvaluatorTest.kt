package com.opendash.app.tool.info

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class MathEvaluatorTest {

    private val eval = MathEvaluator()

    @Test
    fun `simple addition`() {
        val r = eval.eval("1 + 2") as MathEvaluator.Result.Ok
        assertThat(r.value).isWithin(0.0001).of(3.0)
    }

    @Test
    fun `precedence`() {
        val r = eval.eval("2 + 3 * 4") as MathEvaluator.Result.Ok
        assertThat(r.value).isWithin(0.0001).of(14.0)
    }

    @Test
    fun `parentheses`() {
        val r = eval.eval("(2 + 3) * 4") as MathEvaluator.Result.Ok
        assertThat(r.value).isWithin(0.0001).of(20.0)
    }

    @Test
    fun `exponent right associative`() {
        val r = eval.eval("2 ^ 3 ^ 2") as MathEvaluator.Result.Ok
        assertThat(r.value).isWithin(0.0001).of(512.0)
    }

    @Test
    fun `unary minus`() {
        val r = eval.eval("-5 + 3") as MathEvaluator.Result.Ok
        assertThat(r.value).isWithin(0.0001).of(-2.0)
    }

    @Test
    fun `nested unary`() {
        val r = eval.eval("3 * -(2 + 1)") as MathEvaluator.Result.Ok
        assertThat(r.value).isWithin(0.0001).of(-9.0)
    }

    @Test
    fun `sqrt function`() {
        val r = eval.eval("sqrt(16)") as MathEvaluator.Result.Ok
        assertThat(r.value).isWithin(0.0001).of(4.0)
    }

    @Test
    fun `combined expression with functions`() {
        val r = eval.eval("(5+3) * sqrt(16)") as MathEvaluator.Result.Ok
        assertThat(r.value).isWithin(0.0001).of(32.0)
    }

    @Test
    fun `floor and ceil`() {
        val rf = eval.eval("floor(3.7)") as MathEvaluator.Result.Ok
        val rc = eval.eval("ceil(3.2)") as MathEvaluator.Result.Ok
        assertThat(rf.value).isEqualTo(3.0)
        assertThat(rc.value).isEqualTo(4.0)
    }

    @Test
    fun `division by zero is EvalError`() {
        val r = eval.eval("1/0")
        assertThat(r).isInstanceOf(MathEvaluator.Result.EvalError::class.java)
    }

    @Test
    fun `mismatched paren is ParseError`() {
        val r = eval.eval("(1 + 2")
        assertThat(r).isInstanceOf(MathEvaluator.Result.ParseError::class.java)
    }

    @Test
    fun `unknown function is EvalError`() {
        val r = eval.eval("frobnicate(10)")
        assertThat(r).isInstanceOf(MathEvaluator.Result.EvalError::class.java)
    }

    @Test
    fun `sin of pi over 2 is 1`() {
        val r = eval.eval("sin(pi / 2)") as MathEvaluator.Result.Ok
        assertThat(r.value).isWithin(0.0001).of(1.0)
    }

    @Test
    fun `cos of 0 is 1`() {
        val r = eval.eval("cos(0)") as MathEvaluator.Result.Ok
        assertThat(r.value).isWithin(0.0001).of(1.0)
    }

    @Test
    fun `tan of pi over 4 is 1`() {
        val r = eval.eval("tan(pi / 4)") as MathEvaluator.Result.Ok
        assertThat(r.value).isWithin(0.0001).of(1.0)
    }

    @Test
    fun `log base 10`() {
        val r = eval.eval("log(100)") as MathEvaluator.Result.Ok
        assertThat(r.value).isWithin(0.0001).of(2.0)
    }

    @Test
    fun `ln of e is 1`() {
        val r = eval.eval("ln(e)") as MathEvaluator.Result.Ok
        assertThat(r.value).isWithin(0.0001).of(1.0)
    }

    @Test
    fun `ln of non-positive is EvalError`() {
        val r = eval.eval("ln(-1)")
        assertThat(r).isInstanceOf(MathEvaluator.Result.EvalError::class.java)
    }

    @Test
    fun `log of zero is EvalError`() {
        val r = eval.eval("log(0)")
        assertThat(r).isInstanceOf(MathEvaluator.Result.EvalError::class.java)
    }
}
