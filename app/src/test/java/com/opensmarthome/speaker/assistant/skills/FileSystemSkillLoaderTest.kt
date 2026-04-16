package com.opensmarthome.speaker.assistant.skills

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FileSystemSkillLoaderTest {

    @TempDir
    lateinit var baseDir: File

    private fun skill(name: String, content: String) {
        val dir = File(baseDir, name).apply { mkdirs() }
        File(dir, "SKILL.md").writeText(content)
    }

    @Test
    fun `returns empty when base dir does not exist`() {
        val missing = File(baseDir, "does-not-exist")
        val loader = FileSystemSkillLoader(missing)
        assertThat(loader.loadAll()).isEmpty()
    }

    @Test
    fun `returns empty when base dir is a file not a directory`() {
        val asFile = File(baseDir, "not-a-dir.txt").apply { writeText("noop") }
        val loader = FileSystemSkillLoader(asFile)
        assertThat(loader.loadAll()).isEmpty()
    }

    @Test
    fun `loads a single valid skill`() {
        skill(
            "alpha",
            """
            ---
            name: alpha
            description: First skill
            ---
            # Alpha
            body
            """.trimIndent()
        )
        val result = FileSystemSkillLoader(baseDir).loadAll()
        assertThat(result).hasSize(1)
        assertThat(result[0].name).isEqualTo("alpha")
        assertThat(result[0].description).isEqualTo("First skill")
        assertThat(result[0].source).startsWith("file:")
    }

    @Test
    fun `loads multiple skills from sibling subdirectories`() {
        skill(
            "alpha",
            """
            ---
            name: alpha
            description: first
            ---
            body a
            """.trimIndent()
        )
        skill(
            "beta",
            """
            ---
            name: beta
            description: second
            ---
            body b
            """.trimIndent()
        )
        val result = FileSystemSkillLoader(baseDir).loadAll()
        assertThat(result.map { it.name }).containsExactly("alpha", "beta")
    }

    @Test
    fun `skips subdirectory without SKILL dot md`() {
        File(baseDir, "empty-dir").mkdirs()
        skill(
            "alpha",
            """
            ---
            name: alpha
            description: only one
            ---
            body
            """.trimIndent()
        )
        val result = FileSystemSkillLoader(baseDir).loadAll()
        assertThat(result).hasSize(1)
        assertThat(result[0].name).isEqualTo("alpha")
    }

    @Test
    fun `skips file entries in base dir`() {
        // A stray file next to the subdirs must not crash or produce a skill.
        File(baseDir, "stray.txt").writeText("nothing")
        skill(
            "alpha",
            """
            ---
            name: alpha
            description: d
            ---
            body
            """.trimIndent()
        )
        val result = FileSystemSkillLoader(baseDir).loadAll()
        assertThat(result.map { it.name }).containsExactly("alpha")
    }

    @Test
    fun `skips unparseable SKILL content without throwing`() {
        skill("bad", "not-a-valid-skill-md")
        skill(
            "good",
            """
            ---
            name: good
            description: d
            ---
            body
            """.trimIndent()
        )
        val result = FileSystemSkillLoader(baseDir).loadAll()
        // 'bad' should be silently skipped; 'good' stays.
        assertThat(result.map { it.name }).containsExactly("good")
    }
}
