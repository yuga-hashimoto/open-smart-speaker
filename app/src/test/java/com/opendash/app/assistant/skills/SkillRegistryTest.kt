package com.opendash.app.assistant.skills

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SkillRegistryTest {

    @Test
    fun `empty registry returns empty XML`() {
        val registry = SkillRegistry()
        assertThat(registry.toPromptXml()).isEmpty()
        assertThat(registry.isEmpty()).isTrue()
    }

    @Test
    fun `register skill makes it retrievable`() {
        val registry = SkillRegistry()
        val skill = Skill(name = "test", description = "A test skill", body = "body")

        registry.register(skill)

        assertThat(registry.get("test")).isEqualTo(skill)
        assertThat(registry.all()).hasSize(1)
    }

    @Test
    fun `toPromptXml produces OpenClaw-compatible format`() {
        val registry = SkillRegistry()
        registry.register(Skill(name = "weather", description = "Weather queries", body = ""))
        registry.register(Skill(name = "home", description = "Home control", body = ""))

        val xml = registry.toPromptXml()

        assertThat(xml).contains("<available_skills>")
        assertThat(xml).contains("</available_skills>")
        assertThat(xml).contains("<name>weather</name>")
        assertThat(xml).contains("<description>Weather queries</description>")
        assertThat(xml).contains("<name>home</name>")
    }

    @Test
    fun `XML special chars are escaped`() {
        val registry = SkillRegistry()
        registry.register(Skill(name = "a&b", description = "<tag> queries", body = ""))

        val xml = registry.toPromptXml()
        assertThat(xml).contains("a&amp;b")
        assertThat(xml).contains("&lt;tag&gt;")
    }

    @Test
    fun `disabled skill stays out of prompt`() {
        val registry = SkillRegistry()
        registry.register(Skill(name = "weather", description = "Weather queries", body = ""))
        registry.register(Skill(name = "home", description = "Home control", body = ""))
        registry.setEnabled("home", false)

        assertThat(registry.isEnabled("home")).isFalse()
        assertThat(registry.isEnabled("weather")).isTrue()
        assertThat(registry.enabled().map { it.name }).containsExactly("weather")
        val xml = registry.toPromptXml()
        assertThat(xml).doesNotContain("<name>home</name>")
        assertThat(xml).contains("<name>weather</name>")
    }

    @Test
    fun `setEnabled on missing skill is a no-op`() {
        val registry = SkillRegistry()
        registry.setEnabled("nonexistent", false)
        assertThat(registry.isEnabled("nonexistent")).isFalse()
    }

    @Test
    fun `re-enabling a disabled skill puts it back in the prompt`() {
        val registry = SkillRegistry()
        registry.register(Skill(name = "a", description = "x", body = ""))
        registry.setEnabled("a", false)
        assertThat(registry.toPromptXml()).isEmpty()

        registry.setEnabled("a", true)
        assertThat(registry.isEnabled("a")).isTrue()
        assertThat(registry.toPromptXml()).contains("<name>a</name>")
    }

    @Test
    fun `registerAll adds multiple skills`() {
        val registry = SkillRegistry()
        registry.registerAll(
            listOf(
                Skill("s1", "desc1", ""),
                Skill("s2", "desc2", "")
            )
        )

        assertThat(registry.all()).hasSize(2)
    }
}
