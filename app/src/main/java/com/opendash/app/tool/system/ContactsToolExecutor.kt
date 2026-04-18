package com.opendash.app.tool.system

import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.ToolParameter
import com.opendash.app.tool.ToolResult
import com.opendash.app.tool.ToolSchema
import timber.log.Timber

class ContactsToolExecutor(
    private val provider: ContactsProvider
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "search_contacts",
            description = "Search contacts by name. Returns matching contacts with phone numbers and emails.",
            parameters = mapOf(
                "query" to ToolParameter("string", "Name query (partial match)", required = true),
                "limit" to ToolParameter("number", "Max results (1-50, default 10)", required = false)
            )
        ),
        ToolSchema(
            name = "list_contacts",
            description = "List all contacts (most recent first). Use search_contacts when you know a name.",
            parameters = mapOf(
                "limit" to ToolParameter("number", "Max results (1-200, default 50)", required = false)
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "search_contacts" -> executeSearch(call)
                "list_contacts" -> executeList(call)
                else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Contacts tool failed: ${call.name}")
            ToolResult(call.id, false, "", e.message ?: "Execution failed")
        }
    }

    private suspend fun executeSearch(call: ToolCall): ToolResult {
        if (!provider.hasPermission()) {
            return ToolResult(call.id, false, "",
                "Contacts permission not granted. Ask user to grant READ_CONTACTS.")
        }
        val query = call.arguments["query"] as? String
            ?: return ToolResult(call.id, false, "", "Missing query")
        val limit = (call.arguments["limit"] as? Number)?.toInt() ?: 10
        val results = provider.search(query, limit)
        return ToolResult(call.id, true, formatContacts(results))
    }

    private suspend fun executeList(call: ToolCall): ToolResult {
        if (!provider.hasPermission()) {
            return ToolResult(call.id, false, "",
                "Contacts permission not granted.")
        }
        val limit = (call.arguments["limit"] as? Number)?.toInt() ?: 50
        val results = provider.listAll(limit)
        return ToolResult(call.id, true, formatContacts(results))
    }

    private fun formatContacts(contacts: List<ContactInfo>): String {
        val items = contacts.joinToString(",") { c ->
            val phones = c.phoneNumbers.joinToString(",") { """"${it.escapeJson()}"""" }
            val emails = c.emails.joinToString(",") { """"${it.escapeJson()}"""" }
            """{"id":"${c.id}","name":"${c.displayName.escapeJson()}","phones":[$phones],"emails":[$emails]}"""
        }
        return "[$items]"
    }

    private fun String.escapeJson(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
}
