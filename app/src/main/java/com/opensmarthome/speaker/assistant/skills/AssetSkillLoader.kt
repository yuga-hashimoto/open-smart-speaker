package com.opensmarthome.speaker.assistant.skills

import android.content.Context
import timber.log.Timber

/**
 * Loads SKILL.md files from the app's assets/skills/ directory.
 *
 * Directory layout:
 *   assets/skills/<skill-name>/SKILL.md
 *
 * Each SKILL.md must have the YAML-style frontmatter with `name` and `description`.
 */
class AssetSkillLoader(
    private val context: Context,
    private val parser: SkillParser = SkillParser()
) {

    fun loadAll(directory: String = "skills"): List<Skill> {
        val assets = context.assets
        val names = try {
            assets.list(directory) ?: return emptyList()
        } catch (e: Exception) {
            Timber.w(e, "Failed to list assets/$directory")
            return emptyList()
        }

        val skills = mutableListOf<Skill>()
        for (subdir in names) {
            val path = "$directory/$subdir/SKILL.md"
            val content = try {
                assets.open(path).bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                Timber.d("No SKILL.md at $path: ${e.message}")
                continue
            }
            val skill = parser.parse(source = "asset:$path", content = content)
            if (skill != null) {
                skills.add(skill)
            } else {
                Timber.w("Failed to parse skill at $path")
            }
        }
        return skills
    }
}
