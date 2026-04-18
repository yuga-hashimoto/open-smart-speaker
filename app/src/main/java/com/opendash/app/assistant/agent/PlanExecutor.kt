package com.opendash.app.assistant.agent

import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import timber.log.Timber
import java.util.UUID

/**
 * Executes an AgentPlan step-by-step, collecting results.
 *
 * Supports:
 *   - Sequential execution (plan step i+1 runs after step i)
 *   - Early stop when a required step fails
 *   - Step results for verification / user feedback
 *
 * This is the Plan → Execute → Verify pattern from the OpenClaw agent loop.
 */
class PlanExecutor(
    private val toolExecutor: ToolExecutor,
    private val stopOnFailure: Boolean = true
) {

    suspend fun execute(plan: AgentPlan): List<StepResult> {
        val results = mutableListOf<StepResult>()

        for (step in plan.steps) {
            val result = executeStep(step)
            results.add(result)
            if (stopOnFailure && !result.success) {
                Timber.w("Plan '${plan.goal}' halted at step ${step.index}: ${result.error}")
                break
            }
        }

        return results
    }

    private suspend fun executeStep(step: PlanStep): StepResult {
        val toolName = step.toolName ?: return StepResult(
            stepIndex = step.index,
            success = true,
            output = "[think] ${step.description}"
        )

        return try {
            val call = ToolCall(
                id = "plan_${step.index}_${UUID.randomUUID().toString().take(6)}",
                name = toolName,
                arguments = step.toolArguments
            )
            val toolResult = toolExecutor.execute(call)
            StepResult(
                stepIndex = step.index,
                success = toolResult.success,
                output = toolResult.data,
                error = toolResult.error
            )
        } catch (e: Exception) {
            Timber.e(e, "Step ${step.index} failed")
            StepResult(
                stepIndex = step.index,
                success = false,
                output = "",
                error = e.message ?: "Execution error"
            )
        }
    }
}
