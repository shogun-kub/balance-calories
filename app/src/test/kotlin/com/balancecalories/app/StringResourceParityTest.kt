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
 * - an empty translation, which usually means someone trimmed instead of translated,
 * - a `<plurals>` that exists in one language only, or whose quantities disagree about their arguments.
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

    /**
     * `<plurals>` are a second namespace of text: `translations` above only reads `<string>`, so a plural
     * could exist in one language alone and no test would notice. The quantities are per language on
     * purpose -- English has one/other, Russian needs one/few/many/other, and a missing `few` silently
     * falls back to `other` ("5 дня").
     */
    private fun pluralForms(file: File): Map<String, Map<String, String>> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("plurals")
        val result = LinkedHashMap<String, Map<String, String>>()
        for (index in 0 until nodes.length) {
            val element = nodes.item(index) as Element
            val items = LinkedHashMap<String, String>()
            val children = element.getElementsByTagName("item")
            for (itemIndex in 0 until children.length) {
                val item = children.item(itemIndex) as Element
                items[item.getAttribute("quantity")] = item.textContent.trim()
            }
            result[element.getAttribute("name")] = items
        }
        return result
    }

    private fun defaultStrings(): Map<String, String> =
        translations(stringFiles().first { it.parentFile!!.name == "values" })

    private fun defaultPlurals(): Map<String, Map<String, String>> =
        pluralForms(stringFiles().first { it.parentFile!!.name == "values" })

    @Test
    fun everyLocaleDeclaresTheSamePluralNamesAsTheDefault() {
        val default = defaultPlurals()
        assertTrue("No plurals are declared at all", default.isNotEmpty())

        for (file in stringFiles().filter { it.parentFile!!.name != "values" }) {
            val locale = pluralForms(file)
            assertEquals(
                "${file.path} is missing plurals: ${(default.keys - locale.keys).sorted()}",
                emptyList<String>(),
                (default.keys - locale.keys).sorted(),
            )
            assertEquals(
                "${file.path} has plurals absent from values/: ${(locale.keys - default.keys).sorted()}",
                emptyList<String>(),
                (locale.keys - default.keys).sorted(),
            )
        }
    }

    @Test
    fun everyQuantityOfAPluralKeepsTheSameFormatArguments() {
        for (file in stringFiles()) {
            for ((name, items) in pluralForms(file)) {
                val shapes = items.mapValues { (_, text) -> formatArguments(text) }
                val distinct = shapes.values.distinct()
                assertEquals(
                    "${file.path}: quantities of \"$name\" disagree on format arguments: $shapes",
                    1,
                    distinct.size,
                )
                assertTrue("${file.path}: \"$name\" has no format arguments at all", distinct.single().isNotEmpty())
            }
        }
    }

    @Test
    fun quantitiesHoldTheCategoriesTheirGrammarNeeds() {
        val english = defaultPlurals()
        for ((name, items) in english) {
            assertTrue("values: \"$name\" lacks one/other: ${items.keys}", items.keys.containsAll(listOf("one", "other")))
        }

        val russian = pluralForms(stringFiles().first { it.parentFile!!.name == "values-ru" })
        for ((name, items) in russian) {
            assertTrue(
                "values-ru: \"$name\" lacks one/few/many/other: ${items.keys}",
                items.keys.containsAll(listOf("one", "few", "many", "other")),
            )
        }
    }

    @Test
    fun everyLocaleDeclaresTheSameKeysAsTheDefault() {
        val files = stringFiles()
        assertTrue("No strings.xml found under ${resDir.path}", files.isNotEmpty())

        val default = defaultStrings()
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
