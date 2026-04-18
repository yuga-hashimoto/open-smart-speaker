package com.opendash.app.tool.info

import com.google.common.truth.Truth.assertThat
import com.opendash.app.tool.ToolCall
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CurrencyToolExecutorTest {

    private lateinit var executor: CurrencyToolExecutor

    @BeforeEach
    fun setup() {
        executor = CurrencyToolExecutor()
    }

    @Test
    fun `availableTools has three currency tools`() = runTest {
        val names = executor.availableTools().map { it.name }
        assertThat(names).containsExactly(
            "set_currency_rate", "convert_currency", "list_currencies"
        )
    }

    @Test
    fun `set_currency_rate + convert_currency round trip`() = runTest {
        executor.execute(
            ToolCall("1", "set_currency_rate", mapOf("currency" to "EUR", "rate_usd" to 1.08))
        )

        val result = executor.execute(
            ToolCall("2", "convert_currency", mapOf(
                "amount" to 100.0, "from" to "EUR", "to" to "USD"
            ))
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("108.0000")
    }

    @Test
    fun `convert between two non-base currencies`() = runTest {
        executor.execute(ToolCall("1", "set_currency_rate", mapOf("currency" to "EUR", "rate_usd" to 1.08)))
        executor.execute(ToolCall("2", "set_currency_rate", mapOf("currency" to "JPY", "rate_usd" to 0.0067)))

        val result = executor.execute(
            ToolCall("3", "convert_currency", mapOf(
                "amount" to 50.0, "from" to "EUR", "to" to "JPY"
            ))
        )

        assertThat(result.success).isTrue()
        // 50 EUR * 1.08 = 54 USD, then 54 / 0.0067 ~= 8059.7
        assertThat(result.data).contains("8059")
    }

    @Test
    fun `unknown currency returns error with hint`() = runTest {
        val result = executor.execute(
            ToolCall("1", "convert_currency", mapOf(
                "amount" to 100.0, "from" to "XXX", "to" to "USD"
            ))
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("web_search")
    }

    @Test
    fun `negative rate returns error`() = runTest {
        val result = executor.execute(
            ToolCall("1", "set_currency_rate", mapOf(
                "currency" to "EUR", "rate_usd" to -1.0
            ))
        )

        assertThat(result.success).isFalse()
    }

    @Test
    fun `list_currencies includes USD by default`() = runTest {
        val result = executor.execute(ToolCall("1", "list_currencies", emptyMap()))
        assertThat(result.success).isTrue()
        assertThat(result.data).contains("USD")
    }

    @Test
    fun `case insensitive currency codes`() = runTest {
        executor.execute(ToolCall("1", "set_currency_rate", mapOf("currency" to "eur", "rate_usd" to 1.1)))
        val result = executor.execute(
            ToolCall("2", "convert_currency", mapOf("amount" to 10.0, "from" to "EUR", "to" to "usd"))
        )
        assertThat(result.success).isTrue()
        assertThat(result.data).contains("11.0000")
    }
}
