package com.opendash.app.assistant.skills

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SkillInstallerTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var server: MockWebServer
    private lateinit var registry: SkillRegistry
    private lateinit var installer: SkillInstaller

    @BeforeEach
    fun setup() {
        server = MockWebServer().apply { start() }
        registry = SkillRegistry()
        installer = SkillInstaller(
            client = OkHttpClient(),
            skillsDir = tempDir,
            registry = registry
        )
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `installs valid skill from URL`() = runTest {
        server.enqueue(MockResponse().setBody("""---
name: test-skill
description: A test skill
---
# Body
Instructions here."""))

        val result = installer.installFromUrl(server.url("/SKILL.md").toString())

        assertThat(result).isInstanceOf(SkillInstaller.Result.Installed::class.java)
        val installed = result as SkillInstaller.Result.Installed
        assertThat(installed.skill.name).isEqualTo("test-skill")
        assertThat(installed.path.exists()).isTrue()
        assertThat(registry.get("test-skill")).isNotNull()
    }

    @Test
    fun `fails on HTTP error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val result = installer.installFromUrl(server.url("/SKILL.md").toString())

        assertThat(result).isInstanceOf(SkillInstaller.Result.Failed::class.java)
    }

    @Test
    fun `fails on malformed frontmatter`() = runTest {
        server.enqueue(MockResponse().setBody("just plain markdown no frontmatter"))

        val result = installer.installFromUrl(server.url("/SKILL.md").toString())

        assertThat(result).isInstanceOf(SkillInstaller.Result.Failed::class.java)
        assertThat((result as SkillInstaller.Result.Failed).reason).contains("frontmatter")
    }

    @Test
    fun `sanitizes skill name for filesystem`() = runTest {
        server.enqueue(MockResponse().setBody("""---
name: My Weird Name!!!
description: test
---
body"""))

        val result = installer.installFromUrl(server.url("/SKILL.md").toString())

        assertThat(result).isInstanceOf(SkillInstaller.Result.Installed::class.java)
        val installed = result as SkillInstaller.Result.Installed
        // Directory name should only have lowercase alphanumerics, _ and -
        assertThat(installed.path.parentFile!!.name).matches("[a-z0-9_-]+")
    }
}
