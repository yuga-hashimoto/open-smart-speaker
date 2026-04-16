package com.opensmarthome.speaker.assistant.routine

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.tool.ToolCall
import com.opensmarthome.speaker.tool.ToolExecutor
import com.opensmarthome.speaker.tool.ToolResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RoutineToolExecutorTest {

    private lateinit var store: InMemoryRoutineStore
    private lateinit var toolExecutor: ToolExecutor
    private lateinit var executor: RoutineToolExecutor

    @BeforeEach
    fun setup() {
        store = InMemoryRoutineStore()
        toolExecutor = mockk()
        executor = RoutineToolExecutor(store, toolExecutor)
        coEvery { toolExecutor.execute(any()) } answers {
            val call = firstArg<ToolCall>()
            ToolResult(call.id, true, "ok")
        }
    }

    @Test
    fun `availableTools has three routine tools`() = runTest {
        val names = executor.availableTools().map { it.name }
        assertThat(names).containsExactly("run_routine", "list_routines", "delete_routine")
    }

    @Test
    fun `run_routine executes all actions in order`() = runTest {
        store.save(Routine(
            id = "r1",
            name = "good night",
            description = "Sleep prep",
            actions = listOf(
                RoutineAction("execute_command", mapOf("device_id" to "light1", "action" to "off")),
                RoutineAction("set_volume", mapOf("level" to 10))
            )
        ))

        val result = executor.execute(
            ToolCall("1", "run_routine", mapOf("name" to "good night"))
        )

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("\"ran\":2")
        coVerify(exactly = 2) { toolExecutor.execute(any()) }
    }

    @Test
    fun `run_routine name matching is case-insensitive`() = runTest {
        store.save(Routine("r", "Good Night", "", listOf()))

        val result = executor.execute(
            ToolCall("2", "run_routine", mapOf("name" to "good night"))
        )

        assertThat(result.success).isTrue()
    }

    @Test
    fun `run_routine unknown returns error`() = runTest {
        val result = executor.execute(
            ToolCall("3", "run_routine", mapOf("name" to "nope"))
        )

        assertThat(result.success).isFalse()
    }

    @Test
    fun `run_routine counts failures`() = runTest {
        coEvery { toolExecutor.execute(any()) } answers {
            val call = firstArg<ToolCall>()
            if (call.name == "fails") ToolResult(call.id, false, "", "oops")
            else ToolResult(call.id, true, "ok")
        }

        store.save(Routine("r", "mixed", "", listOf(
            RoutineAction("ok_tool", emptyMap()),
            RoutineAction("fails", emptyMap()),
            RoutineAction("ok_tool", emptyMap())
        )))

        val result = executor.execute(
            ToolCall("4", "run_routine", mapOf("name" to "mixed"))
        )

        assertThat(result.success).isFalse()
        assertThat(result.data).contains("\"failures\":1")
    }

    @Test
    fun `list_routines returns all stored`() = runTest {
        store.save(Routine("r1", "first", "desc", listOf()))
        store.save(Routine("r2", "second", "desc2", listOf()))

        val result = executor.execute(ToolCall("5", "list_routines", emptyMap()))

        assertThat(result.success).isTrue()
        assertThat(result.data).contains("first")
        assertThat(result.data).contains("second")
    }

    @Test
    fun `delete_routine removes by id`() = runTest {
        store.save(Routine("r", "x", "", listOf()))

        val result = executor.execute(
            ToolCall("6", "delete_routine", mapOf("id" to "r"))
        )

        assertThat(result.success).isTrue()
        assertThat(store.listAll()).isEmpty()
    }
}
