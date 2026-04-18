package com.opendash.app.tool.info

import com.google.common.truth.Truth.assertThat
import com.opendash.app.tool.ToolCall
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class DateToolExecutorTest {

    private val executor = DateToolExecutor()

    private suspend fun dateDiff(from: String, to: String) = executor.execute(
        ToolCall(id = "t", name = "date_diff", arguments = mapOf("from" to from, "to" to to))
    )

    private suspend fun addDays(date: String, days: Any) = executor.execute(
        ToolCall(id = "t", name = "add_days", arguments = mapOf("date" to date, "days" to days))
    )

    private suspend fun dayOfWeek(date: String) = executor.execute(
        ToolCall(id = "t", name = "day_of_week", arguments = mapOf("date" to date))
    )

    @Test
    fun `availableTools exposes three operations`() = runTest {
        val names = executor.availableTools().map { it.name }.toSet()
        assertThat(names).containsExactly("date_diff", "add_days", "day_of_week")
    }

    @Test
    fun `date_diff returns positive day count`() = runTest {
        val result = dateDiff("2026-04-17", "2026-12-25")
        assertThat(result.success).isTrue()
        // 2026-04-17 to 2026-12-25 = 252 days
        assertThat(result.data).contains("\"days\":252")
    }

    @Test
    fun `date_diff returns negative when reversed`() = runTest {
        val result = dateDiff("2026-12-25", "2026-04-17")
        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"days\":-252")
    }

    @Test
    fun `date_diff same date returns zero`() = runTest {
        val result = dateDiff("2026-04-17", "2026-04-17")
        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"days\":0")
    }

    @Test
    fun `date_diff rejects non-ISO dates`() = runTest {
        val result = dateDiff("04/17/2026", "2026-12-25")
        assertThat(result.success).isFalse()
        assertThat(result.error?.lowercase()).contains("invalid")
    }

    @Test
    fun `date_diff rejects invalid calendar date`() = runTest {
        val result = dateDiff("2026-02-30", "2026-03-01")
        assertThat(result.success).isFalse()
        assertThat(result.error?.lowercase()).contains("invalid")
    }

    @Test
    fun `date_diff missing args returns error`() = runTest {
        val result = executor.execute(
            ToolCall(id = "t", name = "date_diff", arguments = mapOf("from" to "2026-04-17"))
        )
        assertThat(result.success).isFalse()
        assertThat(result.error?.lowercase()).contains("missing")
    }

    @Test
    fun `add_days happy path`() = runTest {
        val result = addDays("2026-04-17", 30)
        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"result\":\"2026-05-17\"")
    }

    @Test
    fun `add_days accepts negative to subtract`() = runTest {
        val result = addDays("2026-04-17", -17)
        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"result\":\"2026-03-31\"")
    }

    @Test
    fun `add_days accepts long values`() = runTest {
        val result = addDays("2026-04-17", 1L)
        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"result\":\"2026-04-18\"")
    }

    @Test
    fun `add_days rejects non-integer days`() = runTest {
        val result = addDays("2026-04-17", "not-a-number")
        assertThat(result.success).isFalse()
        assertThat(result.error?.lowercase()).contains("integer")
    }

    @Test
    fun `add_days caps out-of-range values`() = runTest {
        val result = addDays("2026-04-17", 100_000)
        assertThat(result.success).isFalse()
        assertThat(result.error?.lowercase()).contains("out of range")
    }

    @Test
    fun `add_days rejects bad date`() = runTest {
        val result = addDays("April 17 2026", 5)
        assertThat(result.success).isFalse()
        assertThat(result.error?.lowercase()).contains("invalid")
    }

    @Test
    fun `day_of_week returns Friday for 2026-04-17`() = runTest {
        val result = dayOfWeek("2026-04-17")
        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"day_of_week\":\"Friday\"")
    }

    @Test
    fun `day_of_week rejects invalid format`() = runTest {
        val result = dayOfWeek("2026/04/17")
        assertThat(result.success).isFalse()
        assertThat(result.error?.lowercase()).contains("invalid")
    }

    @Test
    fun `day_of_week missing arg returns error`() = runTest {
        val result = executor.execute(
            ToolCall(id = "t", name = "day_of_week", arguments = emptyMap())
        )
        assertThat(result.success).isFalse()
        assertThat(result.error?.lowercase()).contains("missing")
    }

    @Test
    fun `unknown tool name returns error`() = runTest {
        val result = executor.execute(
            ToolCall(id = "t", name = "nope", arguments = emptyMap())
        )
        assertThat(result.success).isFalse()
        assertThat(result.error?.lowercase()).contains("unknown")
    }
}
