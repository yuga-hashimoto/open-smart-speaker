package com.opensmarthome.speaker.assistant.agent

/**
 * A simple multi-step plan that the agent can follow to accomplish complex requests.
 * Each step describes an intent and (optionally) which tool to call.
 *
 * Example: "Turn off all lights and set the AC to 22°"
 *   Step 1: list lights via get_devices_by_type
 *   Step 2: execute_command off for each
 *   Step 3: execute_command set_temperature 22 on AC
 *   Step 4: confirm to user
 */
data class AgentPlan(
    val goal: String,
    val steps: List<PlanStep>
) {
    /** Index of the next step to execute, or -1 when complete. */
    fun nextStepIndex(results: List<StepResult>): Int {
        if (results.size >= steps.size) return -1
        return results.size
    }

    /** True when all steps completed successfully. */
    fun isComplete(results: List<StepResult>): Boolean =
        results.size == steps.size && results.all { it.success }

    /** True when any step failed — caller should decide whether to retry or give up. */
    fun hasFailure(results: List<StepResult>): Boolean =
        results.any { !it.success }
}

data class PlanStep(
    val index: Int,
    val description: String,
    val toolName: String? = null,
    val toolArguments: Map<String, Any?> = emptyMap()
)

data class StepResult(
    val stepIndex: Int,
    val success: Boolean,
    val output: String,
    val error: String? = null
)
