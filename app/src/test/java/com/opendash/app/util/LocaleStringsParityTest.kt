package com.opendash.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Drift guard for every values-locale/strings.xml file. Every shipped
 * locale must declare the same set of string names as the default
 * values/strings.xml, otherwise the app silently falls back to English
 * for missing keys and users see half-translated UI.
 */
class LocaleStringsParityTest {

    private val stringNameRegex = Regex("""<string\s+[^>]*\bname\s*=\s*"([^"]+)"""")

    @Test
    fun `every shipped locale mirrors the default string names`() {
        val resDir = findResDir()
        val defaultFile = File(resDir, "values/strings.xml")
        assertThat(defaultFile.exists()).isTrue()
        val defaultNames = readStringNames(defaultFile)
        assertThat(defaultNames).isNotEmpty()

        val localeDirs = resDir.listFiles { f ->
            f.isDirectory && f.name.startsWith("values-") && File(f, "strings.xml").exists()
        }.orEmpty()
        assertThat(localeDirs).isNotEmpty()

        val drift = mutableListOf<String>()
        for (dir in localeDirs) {
            val names = readStringNames(File(dir, "strings.xml"))
            val missing = defaultNames - names
            val extra = names - defaultNames
            if (missing.isNotEmpty() || extra.isNotEmpty()) {
                drift += "${dir.name}: missing=${missing.sorted()} extra=${extra.sorted()}"
            }
        }
        assertThat(drift).isEmpty()
    }

    private fun findResDir(): File {
        val candidates = listOf(
            File("app/src/main/res"),
            File("../app/src/main/res"),
            File("src/main/res")
        )
        return candidates.first { it.exists() && it.isDirectory }
    }

    private fun readStringNames(file: File): Set<String> {
        val text = file.readText()
        return stringNameRegex.findAll(text).map { it.groupValues[1] }.toSet()
    }
}
