package com.opensmarthome.speaker.assistant.skills

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.tool.ToolCall
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SkillToolExecutorTest {

    private lateinit var registry: SkillRegistry
    private lateinit var executor: SkillToolExecutor

    @BeforeEach
    fun setup() {
        registry = SkillRegistry()
        executor = SkillToolExecutor(registry)
    }

    @Test
    fun `no tools exposed when registry is empty`() = runTest {
        val tools = executor.availableTools()
        assertThat(tools).isEmpty()
    }

    @Test
    fun `tools exposed when skills are registered`() = runTest {
        registry.register(Skill("s1", "d1", "body"))

        val tools = executor.availableTools()
        val names = tools.map { it.name }

        assertThat(names).containsExactly("get_skill", "list_skills")
    }

    @Test
    fun `get_skill returns full body`() = runTest {
        registry.register(Skill("weather", "Weather skill", "Do X then Y"))

        val result = executor.execute(
            ToolCall(id = "1", name = "get_skill", arguments = mapOf("name" to "weather"))
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("Do X then Y")
        assertThat(result.data).contains("weather")
    }

    @Test
    fun `get_skill missing name returns error`() = runTest {
        val result = executor.execute(
            ToolCall(id = "2", name = "get_skill", arguments = emptyMap())
        )

        assertThat(result.success).isFalse()
    }

    @Test
    fun `get_skill unknown name returns error`() = runTest {
        val result = executor.execute(
            ToolCall(id = "3", name = "get_skill", arguments = mapOf("name" to "missing"))
        )

        assertThat(result.success).isFalse()
        assertThat(result.error).contains("not found")
    }

    @Test
    fun `list_skills returns all registered`() = runTest {
        registry.register(Skill("a", "da", ""))
        registry.register(Skill("b", "db", ""))

        val result = executor.execute(
            ToolCall(id = "4", name = "list_skills", arguments = emptyMap())
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"a\"")
        assertThat(result.data).contains("\"b\"")
    }
}
