package com.opensmarthome.speaker.assistant.routine

import com.opensmarthome.speaker.tool.ToolCall
import com.opensmarthome.speaker.tool.ToolExecutor
import com.opensmarthome.speaker.tool.ToolParameter
import com.opensmarthome.speaker.tool.ToolResult
import com.opensmarthome.speaker.tool.ToolSchema
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Lets the LLM create and run user-defined routines.
 */
class RoutineToolExecutor(
    private val store: RoutineStore,
    private val toolExecutor: ToolExecutor
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "run_routine",
            description = "Run a saved routine by name (e.g. 'good night', 'coming home').",
            parameters = mapOf(
                "name" to ToolParameter("string", "Routine name", required = true)
            )
        ),
        ToolSchema(
            name = "list_routines",
            description = "List all saved routines and their actions.",
            parameters = emptyMap()
        ),
        ToolSchema(
            name = "delete_routine",
            description = "Delete a routine by id.",
            parameters = mapOf(
                "id" to ToolParameter("string", "Routine id", required = true)
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "run_routine" -> executeRun(call)
                "list_routines" -> executeList(call)
                "delete_routine" -> executeDelete(call)
                else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Routine tool failed")
            ToolResult(call.id, false, "", e.message ?: "Routine error")
        }
    }

    private suspend fun executeRun(call: ToolCall): ToolResult {
        val name = call.arguments["name"] as? String
            ?: return ToolResult(call.id, false, "", "Missing name")

        val routine = store.getByName(name)
            ?: return ToolResult(call.id, false, "", "Routine not found: $name")

        val results = runRoutine(routine)
        val failures = results.count { !it.first }
        val data = """{"routine":"${routine.name}","ran":${results.size},"failures":$failures}"""
        return ToolResult(call.id, failures == 0, data, if (failures > 0) "$failures actions failed" else null)
    }

    private suspend fun runRoutine(routine: Routine): List<Pair<Boolean, String>> {
        val results = mutableListOf<Pair<Boolean, String>>()
        for (action in routine.actions) {
            if (action.delayMs > 0) delay(action.delayMs)
            val toolCall = ToolCall(
                id = "routine_${routine.id}_${results.size}",
                name = action.toolName,
                arguments = action.arguments
            )
            val result = toolExecutor.execute(toolCall)
            results.add(result.success to result.data)
        }
        return results
    }

    private suspend fun executeList(call: ToolCall): ToolResult {
        val routines = store.listAll()
        val data = routines.joinToString(",") { r ->
            val actions = r.actions.joinToString(",") { a ->
                """{"tool":"${a.toolName.escapeJson()}"}"""
            }
            """{"id":"${r.id}","name":"${r.name.escapeJson()}","description":"${r.description.escapeJson()}","actions":[$actions]}"""
        }
        return ToolResult(call.id, true, "[$data]")
    }

    private suspend fun executeDelete(call: ToolCall): ToolResult {
        val id = call.arguments["id"] as? String
            ?: return ToolResult(call.id, false, "", "Missing id")
        return if (store.delete(id)) {
            ToolResult(call.id, true, """{"deleted":"$id"}""")
        } else {
            ToolResult(call.id, false, "", "No routine with id $id")
        }
    }

    private fun String.escapeJson(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
}
