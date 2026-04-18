package com.opendash.app.tool

/**
 * Renders a list of ToolSchema to OpenClaw-style XML for prompt injection.
 *
 *   <available_tools>
 *     <tool name="...">
 *       <description>...</description>
 *       <param name="..." type="..." required="true|false">desc</param>
 *     </tool>
 *   </available_tools>
 *
 * Complements the existing Markdown tool section — models differ in which
 * format they follow better. Keeping both available lets us A/B.
 */
object ToolSchemaXml {

    fun render(tools: List<ToolSchema>): String {
        if (tools.isEmpty()) return ""
        val items = tools.joinToString("\n") { renderTool(it) }
        return "<available_tools>\n$items\n</available_tools>"
    }

    private fun renderTool(tool: ToolSchema): String {
        val sb = StringBuilder()
        sb.append("  <tool name=\"${tool.name.escapeXml()}\">\n")
        sb.append("    <description>${tool.description.escapeXml()}</description>\n")
        for ((name, param) in tool.parameters) {
            val enumAttr = param.enum?.let { " enum=\"${it.joinToString(",").escapeXml()}\"" } ?: ""
            sb.append(
                "    <param name=\"${name.escapeXml()}\" " +
                    "type=\"${param.type.escapeXml()}\" " +
                    "required=\"${param.required}\"$enumAttr>" +
                    param.description.escapeXml() +
                    "</param>\n"
            )
        }
        sb.append("  </tool>")
        return sb.toString()
    }

    private fun String.escapeXml(): String =
        replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
