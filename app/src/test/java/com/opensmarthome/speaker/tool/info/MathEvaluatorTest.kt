package com.opensmarthome.speaker.tool.info

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
        val r = eval.eval("log(10)")
        assertThat(r).isInstanceOf(MathEvaluator.Result.EvalError::class.java)
    }
}
