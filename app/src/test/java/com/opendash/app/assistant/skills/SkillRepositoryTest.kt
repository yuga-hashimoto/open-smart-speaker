package com.opendash.app.assistant.skills

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SkillRepositoryTest {

    @TempDir
    lateinit var userDir: File

    private lateinit var registry: SkillRegistry
    private lateinit var repo: SkillRepository

    @BeforeEach
    fun setup() {
        registry = SkillRegistry()
        repo = SkillRepository(registry, userDir)
    }

    @Test
    fun `bundled skill not deletable`() {
        registry.register(Skill("bundled1", "desc", "body", source = "asset:skills/bundled1/SKILL.md"))

        val views = repo.listAll()
        assertThat(views).hasSize(1)
        assertThat(views[0].deletable).isFalse()
    }

    @Test
    fun `installed skill is deletable and removes directory`() {
        val skillDir = File(userDir, "test-skill").apply { mkdirs() }
        val skillFile = File(skillDir, "SKILL.md").apply { writeText("test body") }
        registry.register(Skill(
            "test-skill", "desc", "body",
            source = "installed:${skillFile.absolutePath}"
        ))

        assertThat(repo.listAll()[0].deletable).isTrue()

        val deleted = repo.delete("test-skill")
        assertThat(deleted).isTrue()
        assertThat(registry.get("test-skill")).isNull()
        assertThat(skillDir.exists()).isFalse()
    }

    @Test
    fun `delete bundled returns false`() {
        registry.register(Skill("b", "d", "body", source = "asset:skills/b/SKILL.md"))

        assertThat(repo.delete("b")).isFalse()
        assertThat(registry.get("b")).isNotNull()
    }

    @Test
    fun `delete unknown returns false`() {
        assertThat(repo.delete("nope")).isFalse()
    }

    @Test
    fun `setEnabled toggles registry enabled state`() {
        registry.register(Skill("alpha", "d", "body", source = "asset:skills/alpha/SKILL.md"))
        assertThat(registry.isEnabled("alpha")).isTrue()

        repo.setEnabled("alpha", false)

        assertThat(registry.isEnabled("alpha")).isFalse()
        assertThat(repo.listAll().single().enabled).isFalse()

        repo.setEnabled("alpha", true)
        assertThat(repo.listAll().single().enabled).isTrue()
    }

    @Test
    fun `setEnabled is silent for unknown skill`() {
        repo.setEnabled("ghost", false)
        // Must not throw and must not register a phantom skill.
        assertThat(repo.listAll()).isEmpty()
    }

    @Test
    fun `reloadFromDisk picks up newly added skill files`() {
        // Start empty.
        assertThat(repo.listAll()).isEmpty()

        // Side-load a skill bundle while the app is "running".
        val skillDir = File(userDir, "side-loaded").apply { mkdirs() }
        File(skillDir, "SKILL.md").writeText(
            """
            ---
            name: side-loaded
            description: Added by user via file manager.
            ---
            # Side-loaded
            """.trimIndent()
        )

        repo.reloadFromDisk()

        val views = repo.listAll()
        assertThat(views.map { it.name }).contains("side-loaded")
        // Loaded from userSkillsDir → must be deletable.
        val view = views.single { it.name == "side-loaded" }
        assertThat(view.deletable).isTrue()
    }

    @Test
    fun `delete removes user skill directory and registry entry`() {
        val skillDir = File(userDir, "wipe-me").apply { mkdirs() }
        File(skillDir, "SKILL.md").writeText("test")
        registry.register(Skill(
            "wipe-me", "desc", "body",
            source = "file:${skillDir.absolutePath}/SKILL.md"
        ))

        assertThat(repo.delete("wipe-me")).isTrue()
        assertThat(skillDir.exists()).isFalse()
        assertThat(registry.get("wipe-me")).isNull()
    }
}
