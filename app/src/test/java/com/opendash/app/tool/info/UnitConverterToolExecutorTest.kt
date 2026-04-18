package com.opendash.app.tool.info

import com.google.common.truth.Truth.assertThat
import com.opendash.app.tool.ToolCall
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class UnitConverterToolExecutorTest {

    private val executor = UnitConverterToolExecutor()

    private suspend fun call(value: Any?, from: String?, to: String?): com.opendash.app.tool.ToolResult {
        val args = buildMap<String, Any?> {
            value?.let { put("value", it) }
            from?.let { put("from", it) }
            to?.let { put("to", it) }
        }
        return executor.execute(
            ToolCall(id = "t1", name = "convert_units", arguments = args)
        )
    }

    @Test
    fun `availableTools exposes convert_units`() = runTest {
        val schema = executor.availableTools().single()
        assertThat(schema.name).isEqualTo("convert_units")
        assertThat(schema.parameters).containsKey("value")
        assertThat(schema.parameters).containsKey("from")
        assertThat(schema.parameters).containsKey("to")
    }

    @Test
    fun `kg to lb`() = runTest {
        val r = call(1.0, "kg", "lb")
        assertThat(r.success).isTrue()
        assertThat(r.data).contains("\"unit\":\"lb\"")
        // 1 kg ≈ 2.2046 lb
        assertThat(r.data).contains("2.20")
    }

    @Test
    fun `celsius to fahrenheit`() = runTest {
        val r = call(0.0, "c", "f")
        assertThat(r.success).isTrue()
        // 0°C = 32°F
        assertThat(r.data).contains("32.0")
    }

    @Test
    fun `meter to foot`() = runTest {
        val r = call(1.0, "m", "foot")
        assertThat(r.success).isTrue()
        // 1 m ≈ 3.28 ft
        assertThat(r.data).contains("3.28")
    }

    @Test
    fun `liter to ml`() = runTest {
        val r = call(2.5, "l", "ml")
        assertThat(r.success).isTrue()
        // 2.5 L = 2500 ml
        assertThat(r.data).contains("2500")
    }

    @Test
    fun `incompatible dimensions returns error`() = runTest {
        val r = call(1.0, "kg", "m")
        assertThat(r.success).isFalse()
        assertThat(r.error?.lowercase()).contains("dimension")
    }

    @Test
    fun `unknown unit returns error`() = runTest {
        val r = call(1.0, "wibble", "kg")
        assertThat(r.success).isFalse()
        assertThat(r.error?.lowercase()).contains("unknown")
    }

    @Test
    fun `missing value returns error`() = runTest {
        val r = call(value = null, from = "kg", to = "lb")
        assertThat(r.success).isFalse()
        assertThat(r.error?.lowercase()).contains("value")
    }

    @Test
    fun `missing from returns error`() = runTest {
        val r = call(value = 1.0, from = null, to = "lb")
        assertThat(r.success).isFalse()
        assertThat(r.error?.lowercase()).contains("from")
    }

    @Test
    fun `missing to returns error`() = runTest {
        val r = call(value = 1.0, from = "kg", to = null)
        assertThat(r.success).isFalse()
        assertThat(r.error?.lowercase()).contains("to")
    }

    @Test
    fun `unknown tool name returns error`() = runTest {
        val r = executor.execute(
            ToolCall(id = "t2", name = "not_convert", arguments = emptyMap())
        )
        assertThat(r.success).isFalse()
        assertThat(r.error?.lowercase()).contains("unknown")
    }
}
