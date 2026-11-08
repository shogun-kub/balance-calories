package com.balancecalories.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Both languages are meant to be maintained, not one translated after the other (AGENTS.md). Nothing
 * else enforces that: Android silently falls back to the default string when a translation is missing,
 * so a forgotten `values-ru` key ships as English text inside a Russian screen and no build fails.
 *
 * These tests read every `strings.xml` under `res` and fail on:
 * - a key that exists in one language but not the other,
 * - a format-argument mismatch (for example `%1$d` in one file and `%1$s` in the other), which would
 *   either crash at runtime or print the wrong thing,
 * - an empty translation, which usually means someone trimmed instead of translated.
 */
class StringResourceParityTest {

    private val resDir: File = locateResDir()

    private fun locateResDir(): File {
        // AGP runs unit tests with the module directory as the working directory, but do not depend on it.
        var dir: File? = File("src/main/res").absoluteFile
        while (dir != null && !File(dir, "values").isDirectory) {
            dir = dir.parentFile
        }
        return dir ?: error("Could not locate src/main/res from ${File(".").absolutePath}")
    }

    private fun stringFiles(): List<File> = resDir.listFiles()
        .orEmpty()
        .filter { it.isDirectory && (it.name == "values" || it.name.startsWith("values-")) }
        .mapNotNull { dir -> File(dir, "strings.xml").takeIf { it.isFile } }
        .sortedBy { it.parentFile!!.name }

    private fun translations(file: File): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        val result = LinkedHashMap<String, String>()
        for (index in 0 until nodes.length) {
            val element = nodes.item(index) as Element
            result[element.getAttribute("name")] = element.textContent.trim()
        }
        return result
    }

    private fun formatArguments(value: String): List<String> =
        Regex("""%(?:\d+\$)?([sdf])""").findAll(value).map { it.value }.sorted().toList()

    @Test
    fun everyLocaleDeclaresTheSameKeysAsTheDefault() {
        val files = stringFiles()
        assertTrue("No strings.xml found under ${resDir.path}", files.isNotEmpty())

        val default = translations(files.first { it.parentFile!!.name == "values" })
        assertTrue("Default strings.xml is empty", default.isNotEmpty())

        for (file in files.filter { it.parentFile!!.name != "values" }) {
            val locale = translations(file)
            val missing = (default.keys - locale.keys).sorted()
            val extra = (locale.keys - default.keys).sorted()

            assertEquals("${file.path} is missing keys: $missing", emptyList<String>(), missing)
            assertEquals("${file.path} has keys absent from values/: $extra", emptyList<String>(), extra)
        }
    }

    @Test
    fun formatArgumentsMatchAcrossLocales() {
        val default = translations(stringFiles().first { it.parentFile!!.name == "values" })

        for (file in stringFiles().filter { it.parentFile!!.name != "values" }) {
            val locale = translations(file)
            for ((key, expected) in default) {
                val translated = locale[key] ?: continue
                assertEquals(
                    "${file.path}: format arguments for \"$key\" differ from the default",
                    formatArguments(expected),
                    formatArguments(translated),
                )
            }
        }
    }

    @Test
    fun noTranslationIsBlank() {
        for (file in stringFiles()) {
            for ((key, value) in translations(file)) {
                assertTrue("${file.path}: \"$key\" is blank", value.isNotBlank())
            }
        }
    }

    @Test
    fun theAppNameIsLocalisedInBothLanguages() {
        val byLocale = stringFiles().associate { it.parentFile!!.name to translations(it)["app_name"] }

        assertEquals("Balance Calories", byLocale["values"])
        assertEquals("Баланс Калорий", byLocale["values-ru"])
    }
}
