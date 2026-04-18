package com.opendash.app.tool.info

import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.ToolParameter
import com.opendash.app.tool.ToolResult
import com.opendash.app.tool.ToolSchema
import timber.log.Timber
import kotlin.random.Random

/**
 * Three tiny randomness primitives the LLM can reach for:
 *
 * - `flip_coin` — canonical "heads or tails" answer for tie-breaking.
 * - `roll_dice` — N-sided dice, optional count. Returns each roll plus
 *   the sum so the LLM can phrase either as needed.
 * - `pick_random` — uniform choice from a caller-supplied list.
 *
 * Seeded via a caller-provided [random] so tests can pin the output.
 * No external state, no network — stays on-device and deterministic
 * under test.
 */
class RandomToolExecutor(
    private val random: Random = Random.Default
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "flip_coin",
            description = "Flip a fair coin. Returns 'heads' or 'tails'.",
            parameters = emptyMap()
        ),
        ToolSchema(
            name = "roll_dice",
            description = "Roll one or more N-sided dice. Defaults to a single 6-sided die.",
            parameters = mapOf(
                "sides" to ToolParameter(
                    type = "integer",
                    description = "Number of sides per die (2..100). Default 6.",
                    required = false
                ),
                "count" to ToolParameter(
                    type = "integer",
                    description = "How many dice to roll (1..10). Default 1.",
                    required = false
                )
            )
        ),
        ToolSchema(
            name = "pick_random",
            description = "Pick one uniformly random element from a comma-separated list.",
            parameters = mapOf(
                "options" to ToolParameter(
                    type = "string",
                    description = "Comma-separated list of choices, e.g. 'pizza,sushi,ramen'.",
                    required = true
                )
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult = try {
        when (call.name) {
            "flip_coin" -> flipCoin(call)
            "roll_dice" -> rollDice(call)
            "pick_random" -> pickRandom(call)
            else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
        }
    } catch (e: Exception) {
        Timber.e(e, "Random tool failed")
        ToolResult(call.id, false, "", e.message ?: "Random tool failed")
    }

    private fun flipCoin(call: ToolCall): ToolResult {
        val face = if (random.nextBoolean()) "heads" else "tails"
        return ToolResult(call.id, true, """{"result":"$face"}""")
    }

    private fun rollDice(call: ToolCall): ToolResult {
        val sides = (call.arguments["sides"] as? Number)?.toInt() ?: 6
        val count = (call.arguments["count"] as? Number)?.toInt() ?: 1
        if (sides !in 2..100) {
            return ToolResult(call.id, false, "", "sides must be in 2..100")
        }
        if (count !in 1..10) {
            return ToolResult(call.id, false, "", "count must be in 1..10")
        }
        val rolls = List(count) { random.nextInt(1, sides + 1) }
        val total = rolls.sum()
        val rollsJson = rolls.joinToString(prefix = "[", postfix = "]")
        return ToolResult(
            call.id,
            true,
            """{"rolls":$rollsJson,"sum":$total,"sides":$sides}"""
        )
    }

    private fun pickRandom(call: ToolCall): ToolResult {
        val raw = call.arguments["options"] as? String
            ?: return ToolResult(call.id, false, "", "Missing options")
        val choices = raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (choices.isEmpty()) {
            return ToolResult(call.id, false, "", "options must contain at least one non-empty entry")
        }
        val pick = choices[random.nextInt(choices.size)]
        // JSON-escape the picked string — options may contain quotes or
        // backslashes we haven't forbidden, and a naive concatenation
        // would break the result envelope.
        val escaped = pick.replace("\\", "\\\\").replace("\"", "\\\"")
        return ToolResult(call.id, true, """{"result":"$escaped"}""")
    }
}
