package com.opensmarthome.speaker.tool.info

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.tool.ToolCall
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Pins [PercentageToolExecutor] JSON envelope and validation behaviour.
 * No randomness — results are deterministic functions of the inputs.
 */
class PercentageToolExecutorTest {

    private val exec = PercentageToolExecutor()

    @Test
    fun `percent_of returns X percent of Y`() = runTest {
        val result = exec.execute(
            ToolCall(
                id = "1",
                name = "percent_of",
                arguments = mapOf("percent" to 15, "value" to 80)
            )
        )
        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"result\":12.000000")
    }

    @Test
    fun `percent_of accepts string-encoded numbers`() = runTest {
        val result = exec.execute(
            ToolCall(
                id = "2",
                name = "percent_of",
                arguments = mapOf("percent" to "25", "value" to "200")
            )
        )
        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"result\":50.000000")
    }

    @Test
    fun `percent_of rejects NaN value`() = runTest {
        val result = exec.execute(
            ToolCall(
                id = "3",
                name = "percent_of",
                arguments = mapOf("percent" to 10, "value" to Double.NaN)
            )
        )
        assertThat(result.success).isFalse()
        assertThat(result.error).contains("value")
    }

    @Test
    fun `percent_of rejects infinite percent`() = runTest {
        val result = exec.execute(
            ToolCall(
                id = "4",
                name = "percent_of",
                arguments = mapOf("percent" to Double.POSITIVE_INFINITY, "value" to 10)
            )
        )
        assertThat(result.success).isFalse()
        assertThat(result.error).contains("percent")
    }

    @Test
    fun `percent_change computes positive delta`() = runTest {
        val result = exec.execute(
            ToolCall(
                id = "5",
                name = "percent_change",
                arguments = mapOf("before" to 100, "after" to 125)
            )
        )
        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"percent_change\":25.000000")
    }

    @Test
    fun `percent_change computes negative delta`() = runTest {
        val result = exec.execute(
            ToolCall(
                id = "6",
                name = "percent_change",
                arguments = mapOf("before" to 200, "after" to 150)
            )
        )
        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"percent_change\":-25.000000")
    }

    @Test
    fun `percent_change rejects zero baseline`() = runTest {
        val result = exec.execute(
            ToolCall(
                id = "7",
                name = "percent_change",
                arguments = mapOf("before" to 0, "after" to 5)
            )
        )
        assertThat(result.success).isFalse()
        assertThat(result.error).contains("before must be non-zero")
    }

    @Test
    fun `tip_calc returns tip and total for reasonable input`() = runTest {
        val result = exec.execute(
            ToolCall(
                id = "8",
                name = "tip_calc",
                arguments = mapOf("bill" to 50, "tip_percent" to 20)
            )
        )
        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"tip\":10.000000")
        assertThat(result.data).contains("\"total\":60.000000")
    }

    @Test
    fun `tip_calc rejects negative tip percent`() = runTest {
        val result = exec.execute(
            ToolCall(
                id = "9",
                name = "tip_calc",
                arguments = mapOf("bill" to 50, "tip_percent" to -5)
            )
        )
        assertThat(result.success).isFalse()
        assertThat(result.error).contains("non-negative")
    }

    @Test
    fun `tip_calc rejects tip percent above 100`() = runTest {
        val result = exec.execute(
            ToolCall(
                id = "10",
                name = "tip_calc",
                arguments = mapOf("bill" to 50, "tip_percent" to 150)
            )
        )
        assertThat(result.success).isFalse()
        assertThat(result.error).contains("must not exceed 100")
    }

    @Test
    fun `tip_calc accepts boundary tip percent of 100`() = runTest {
        val result = exec.execute(
            ToolCall(
                id = "11",
                name = "tip_calc",
                arguments = mapOf("bill" to 20, "tip_percent" to 100)
            )
        )
        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"tip\":20.000000")
        assertThat(result.data).contains("\"total\":40.000000")
    }

    @Test
    fun `unknown tool name returns failure`() = runTest {
        val result = exec.execute(
            ToolCall(id = "12", name = "bogus", arguments = emptyMap())
        )
        assertThat(result.success).isFalse()
        assertThat(result.error).contains("Unknown tool")
    }

    @Test
    fun `availableTools advertises three tools`() = runTest {
        val names = exec.availableTools().map { it.name }
        assertThat(names).containsExactly("percent_of", "percent_change", "tip_calc")
    }
}
