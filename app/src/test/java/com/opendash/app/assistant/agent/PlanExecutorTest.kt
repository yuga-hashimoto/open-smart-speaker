package com.opendash.app.assistant.agent

import com.google.common.truth.Truth.assertThat
import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.ToolResult
import com.opendash.app.tool.ToolSchema
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class PlanExecutorTest {

    private val toolExecutor: ToolExecutor = mockk(relaxed = true)

    @Test
    fun `executes all steps in order when all succeed`() = runTest {
        coEvery { toolExecutor.execute(any()) } answers {
            val call = firstArg<ToolCall>()
            ToolResult(call.id, true, "ok-${call.name}")
        }

        val plan = AgentPlan(
            goal = "Turn off lights",
            steps = listOf(
                PlanStep(0, "find lights", "get_devices_by_type", mapOf("type" to "light")),
                PlanStep(1, "turn off", "execute_command", mapOf("device_id" to "l1", "action" to "off"))
            )
        )

        val executor = PlanExecutor(toolExecutor)
        val results = executor.execute(plan)

        assertThat(results).hasSize(2)
        assertThat(results.all { it.success }).isTrue()
        assertThat(plan.isComplete(results)).isTrue()
    }

    @Test
    fun `stops on first failure when stopOnFailure is true`() = runTest {
        coEvery { toolExecutor.execute(any()) } answers {
            val call = firstArg<ToolCall>()
            if (call.name == "fails") ToolResult(call.id, false, "", "boom")
            else ToolResult(call.id, true, "ok")
        }

        val plan = AgentPlan(
            goal = "3-step plan",
            steps = listOf(
                PlanStep(0, "first", "ok_tool"),
                PlanStep(1, "broken", "fails"),
                PlanStep(2, "should not run", "ok_tool")
            )
        )

        val executor = PlanExecutor(toolExecutor, stopOnFailure = true)
        val results = executor.execute(plan)

        assertThat(results).hasSize(2)
        assertThat(results.last().success).isFalse()
        assertThat(plan.isComplete(results)).isFalse()
        assertThat(plan.hasFailure(results)).isTrue()
    }

    @Test
    fun `continues on failure when stopOnFailure is false`() = runTest {
        coEvery { toolExecutor.execute(any()) } answers {
            val call = firstArg<ToolCall>()
            ToolResult(call.id, call.name != "fails", if (call.name == "fails") "" else "ok", null)
        }

        val plan = AgentPlan(
            goal = "tolerant",
            steps = listOf(
                PlanStep(0, "a", "ok"),
                PlanStep(1, "b", "fails"),
                PlanStep(2, "c", "ok")
            )
        )

        val executor = PlanExecutor(toolExecutor, stopOnFailure = false)
        val results = executor.execute(plan)

        assertThat(results).hasSize(3)
        assertThat(results.count { it.success }).isEqualTo(2)
    }

    @Test
    fun `step without toolName produces think result`() = runTest {
        val plan = AgentPlan(
            goal = "thinking",
            steps = listOf(PlanStep(0, "just a thought", toolName = null))
        )

        val executor = PlanExecutor(toolExecutor)
        val results = executor.execute(plan)

        assertThat(results).hasSize(1)
        assertThat(results[0].success).isTrue()
        assertThat(results[0].output).contains("think")
    }

    @Test
    fun `nextStepIndex tracks progress`() {
        val plan = AgentPlan(
            goal = "x",
            steps = listOf(PlanStep(0, "a"), PlanStep(1, "b"), PlanStep(2, "c"))
        )
        assertThat(plan.nextStepIndex(emptyList())).isEqualTo(0)
        assertThat(plan.nextStepIndex(listOf(StepResult(0, true, "ok")))).isEqualTo(1)
        val allDone = (0..2).map { StepResult(it, true, "ok") }
        assertThat(plan.nextStepIndex(allDone)).isEqualTo(-1)
    }
}
