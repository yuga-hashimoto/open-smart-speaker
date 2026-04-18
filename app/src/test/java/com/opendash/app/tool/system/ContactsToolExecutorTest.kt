package com.opendash.app.tool.system

import com.google.common.truth.Truth.assertThat
import com.opendash.app.tool.ToolCall
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ContactsToolExecutorTest {

    private lateinit var executor: ContactsToolExecutor
    private lateinit var provider: ContactsProvider

    @BeforeEach
    fun setup() {
        provider = mockk(relaxed = true)
        executor = ContactsToolExecutor(provider)
    }

    @Test
    fun `availableTools includes search and list`() = runTest {
        val names = executor.availableTools().map { it.name }
        assertThat(names).containsExactly("search_contacts", "list_contacts")
    }

    @Test
    fun `search_contacts without permission returns error`() = runTest {
        every { provider.hasPermission() } returns false

        val result = executor.execute(
            ToolCall(id = "1", name = "search_contacts", arguments = mapOf("query" to "Alice"))
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("permission")
    }

    @Test
    fun `search_contacts missing query returns error`() = runTest {
        every { provider.hasPermission() } returns true

        val result = executor.execute(
            ToolCall(id = "2", name = "search_contacts", arguments = emptyMap())
        )

        assertThat(result.success).isFalse()
    }

    @Test
    fun `search_contacts returns formatted results`() = runTest {
        every { provider.hasPermission() } returns true
        coEvery { provider.search("Alice", 10) } returns listOf(
            ContactInfo("1", "Alice Smith", listOf("+1234"), listOf("alice@example.com"))
        )

        val result = executor.execute(
            ToolCall(id = "3", name = "search_contacts", arguments = mapOf("query" to "Alice"))
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("Alice Smith")
        assertThat(result.data).contains("+1234")
        assertThat(result.data).contains("alice@example.com")
    }

    @Test
    fun `list_contacts uses limit parameter`() = runTest {
        every { provider.hasPermission() } returns true
        coEvery { provider.listAll(5) } returns emptyList()

        val result = executor.execute(
            ToolCall(id = "4", name = "list_contacts", arguments = mapOf("limit" to 5.0))
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).isEqualTo("[]")
    }

    @Test
    fun `special chars in name are JSON escaped`() = runTest {
        every { provider.hasPermission() } returns true
        coEvery { provider.search(any(), any()) } returns listOf(
            ContactInfo("x", """Name with "quotes"""", emptyList(), emptyList())
        )

        val result = executor.execute(
            ToolCall(id = "5", name = "search_contacts", arguments = mapOf("query" to "x"))
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\\\"quotes\\\"")
    }
}
