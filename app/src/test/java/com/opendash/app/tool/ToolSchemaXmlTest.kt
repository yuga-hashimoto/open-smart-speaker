package com.opendash.app.tool

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ToolSchemaXmlTest {

    @Test
    fun `empty tools produces empty string`() {
        assertThat(ToolSchemaXml.render(emptyList())).isEmpty()
    }

    @Test
    fun `single tool without parameters`() {
        val tools = listOf(
            ToolSchema(
                name = "list_skills",
                description = "List all available skills.",
                parameters = emptyMap()
            )
        )

        val xml = ToolSchemaXml.render(tools)

        assertThat(xml).contains("<available_tools>")
        assertThat(xml).contains("</available_tools>")
        assertThat(xml).contains("<tool name=\"list_skills\">")
        assertThat(xml).contains("<description>List all available skills.</description>")
    }

    @Test
    fun `tool with parameters renders each param`() {
        val tools = listOf(
            ToolSchema(
                name = "set_timer",
                description = "Set a countdown timer.",
                parameters = mapOf(
                    "seconds" to ToolParameter("number", "Duration in seconds", required = true),
                    "label" to ToolParameter("string", "Timer label", required = false)
                )
            )
        )

        val xml = ToolSchemaXml.render(tools)

        assertThat(xml).contains("<param name=\"seconds\" type=\"number\" required=\"true\">")
        assertThat(xml).contains("<param name=\"label\" type=\"string\" required=\"false\">")
    }

    @Test
    fun `enum parameter includes enum attribute`() {
        val tools = listOf(
            ToolSchema(
                name = "set_mode",
                description = "desc",
                parameters = mapOf(
                    "mode" to ToolParameter("string", "mode", required = true, enum = listOf("a", "b", "c"))
                )
            )
        )

        val xml = ToolSchemaXml.render(tools)

        assertThat(xml).contains("enum=\"a,b,c\"")
    }

    @Test
    fun `special characters are XML-escaped`() {
        val tools = listOf(
            ToolSchema(
                name = "weird",
                description = "Has <tags> & \"quotes\"",
                parameters = mapOf(
                    "p" to ToolParameter("string", "with <angle>", required = false)
                )
            )
        )

        val xml = ToolSchemaXml.render(tools)

        assertThat(xml).contains("&lt;tags&gt;")
        assertThat(xml).contains("&amp;")
        assertThat(xml).contains("&quot;quotes&quot;")
        assertThat(xml).contains("with &lt;angle&gt;")
    }
}
