package com.opendash.app.assistant.skills

import timber.log.Timber
import java.io.File

/**
 * Loads SKILL.md files from a writable directory on disk.
 * Used for user-installed skills that live alongside bundled assets.
 *
 * Directory layout:
 *   <baseDir>/<skill-name>/SKILL.md
 */
class FileSystemSkillLoader(
    private val baseDir: File,
    private val parser: SkillParser = SkillParser()
) {

    fun loadAll(): List<Skill> {
        if (!baseDir.exists() || !baseDir.isDirectory) return emptyList()

        val skills = mutableListOf<Skill>()
        baseDir.listFiles()?.forEach { subdir ->
            if (!subdir.isDirectory) return@forEach
            val skillFile = File(subdir, "SKILL.md")
            if (!skillFile.exists()) return@forEach

            val content = try {
                skillFile.readText()
            } catch (e: Exception) {
                Timber.w(e, "Failed to read ${skillFile.absolutePath}")
                return@forEach
            }

            val skill = parser.parse(
                source = "file:${skillFile.absolutePath}",
                content = content
            )
            if (skill != null) {
                skills.add(skill)
            } else {
                Timber.w("Failed to parse skill at ${skillFile.absolutePath}")
            }
        }
        return skills
    }
}
