package com.opendash.app.assistant.skills

import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File

/**
 * Downloads a SKILL.md from a URL and installs it into the user skills directory,
 * then registers it live in the SkillRegistry.
 *
 * Does NOT trust arbitrary executable content — only the Markdown body. The skill
 * cannot do anything on its own; it only describes behavior for the LLM.
 */
class SkillInstaller(
    private val client: OkHttpClient,
    private val skillsDir: File,
    private val registry: SkillRegistry,
    private val parser: SkillParser = SkillParser()
) {

    sealed class Result {
        data class Installed(val skill: Skill, val path: File) : Result()
        data class Failed(val reason: String) : Result()
    }

    suspend fun installFromUrl(url: String): Result {
        val content = fetch(url) ?: return Result.Failed("Download failed")
        val skill = parser.parse(source = "installed:$url", content = content)
            ?: return Result.Failed("SKILL.md is missing required frontmatter (name + description)")

        val targetDir = File(skillsDir, sanitizeName(skill.name))
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            return Result.Failed("Failed to create ${targetDir.absolutePath}")
        }
        val targetFile = File(targetDir, "SKILL.md")
        return try {
            targetFile.writeText(content)
            registry.register(skill.copy(source = "installed:${targetFile.absolutePath}"))
            Result.Installed(skill, targetFile)
        } catch (e: Exception) {
            Timber.e(e, "Failed to write skill")
            Result.Failed(e.message ?: "Write error")
        }
    }

    private fun fetch(url: String): String? {
        return try {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string()
            }
        } catch (e: Exception) {
            Timber.w(e, "Skill fetch failed")
            null
        }
    }

    private fun sanitizeName(name: String): String =
        name.lowercase()
            .replace(Regex("[^a-z0-9_-]"), "-")
            .trim('-')
            .ifBlank { "skill-${System.currentTimeMillis()}" }
}
