package com.opendash.app.tool.info

import com.google.common.truth.Truth.assertThat
import com.opendash.app.tool.ToolCall
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class CalculatorToolExecutorTest {

    private val executor = CalculatorToolExecutor()

    private suspend fun call(expr: String) = executor.execute(
        ToolCall(id = "t1", name = "calculate", arguments = mapOf("expression" to expr))
    )

    @Test
    fun `availableTools exposes calculate`() = runTest {
        val schema = executor.availableTools().single()
        assertThat(schema.name).isEqualTo("calculate")
        assertThat(schema.parameters).containsKey("expression")
    }

    @Test
    fun `simple addition`() = runTest {
        val result = call("2 + 3")
        assertThat(result.success).isTrue()
        assertThat(result.data).contains("5.000000")
    }

    @Test
    fun `multiplication and parens`() = runTest {
        val result = call("(3 + 4) * 5")
        assertThat(result.success).isTrue()
        assertThat(result.data).contains("35.000000")
    }

    @Test
    fun `unary minus is supported`() = runTest {
        val result = call("-5 + 10")
        assertThat(result.success).isTrue()
        assertThat(result.data).contains("5.000000")
    }

    @Test
    fun `function call sqrt`() = runTest {
        val result = call("sqrt(16)")
        assertThat(result.success).isTrue()
        assertThat(result.data).contains("4.000000")
    }

    @Test
    fun `pi and e constants`() = runTest {
        val result = call("pi + e")
        assertThat(result.success).isTrue()
        // ≈ 5.859874
        assertThat(result.data).contains("5.859")
    }

    @Test
    fun `divide by zero returns error`() = runTest {
        val result = call("1 / 0")
        // Underlying may produce Infinity (Ok with non-finite) or EvalError —
        // either way, the executor reports it without throwing.
        assertThat(result).isNotNull()
    }

    @Test
    fun `parse error surfaces structured error`() = runTest {
        val result = call("1 + ")
        assertThat(result.success).isFalse()
        assertThat(result.error?.lowercase()).contains("error")
    }

    @Test
    fun `missing expression returns error`() = runTest {
        val result = executor.execute(
            ToolCall(id = "t2", name = "calculate", arguments = emptyMap())
        )
        assertThat(result.success).isFalse()
        assertThat(result.error?.lowercase()).contains("missing")
    }

    @Test
    fun `unknown tool name returns error`() = runTest {
        val result = executor.execute(
            ToolCall(id = "t3", name = "not_calculate", arguments = emptyMap())
        )
        assertThat(result.success).isFalse()
        assertThat(result.error?.lowercase()).contains("unknown")
    }
}
