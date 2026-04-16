package com.opensmarthome.speaker.assistant.skills

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SkillParserTest {

    private val parser = SkillParser()

    @Test
    fun `parse valid skill with frontmatter and body`() {
        val content = """---
name: weather-helper
description: Helps with weather-related queries
---
# Weather Skill

When the user asks about weather, use get_weather first.
Then format the result as plain prose.
""".trimIndent()

        val skill = parser.parse("test.md", content)

        assertThat(skill).isNotNull()
        assertThat(skill?.name).isEqualTo("weather-helper")
        assertThat(skill?.description).isEqualTo("Helps with weather-related queries")
        assertThat(skill?.body).contains("Weather Skill")
    }

    @Test
    fun `parse missing frontmatter returns null`() {
        val content = "# Just markdown\n\nNo frontmatter here."
        val skill = parser.parse("test.md", content)
        assertThat(skill).isNull()
    }

    @Test
    fun `parse missing name returns null`() {
        val content = """---
description: No name field
---
body
""".trimIndent()

        val skill = parser.parse("test.md", content)
        assertThat(skill).isNull()
    }

    @Test
    fun `parse strips quotes from values`() {
        val content = """---
name: "quoted-name"
description: 'single quoted desc'
---
body
""".trimIndent()

        val skill = parser.parse("test.md", content)
        assertThat(skill?.name).isEqualTo("quoted-name")
        assertThat(skill?.description).isEqualTo("single quoted desc")
    }
}
