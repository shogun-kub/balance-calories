package com.balancecalories.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The macro line on Today is assembled from three label fragments and the grams beside them:
 * "Белки 20 · Жиры 12 · Углеводы 0". The screen appends the number itself, so a label that carries a
 * format specifier prints the specifier: `stringResource(id)` without arguments returns the raw template.
 *
 * That is not hypothetical. The first device build of M7.7 rendered
 * "Белки %1$d 20 · Жиры %1$d 12 · Углеводы %1$d 0" on the Honor X7c (2026-11-11). Every existing test
 * passed, because [StringResourceParityTest] compares the two string files to each other and never looks
 * at rendered text, and a rendered screen is exactly what a JVM test cannot see.
 *
 * The spoken description is a different string and does take six arguments; only the fragments are
 * checked here. A label is a fragment, not a sentence, so it holds a noun and nothing else.
 */
class MacroLabelTest {

    private val labels = listOf("today_entry_protein", "today_entry_fat", "today_entry_carbs")

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

    private fun strings(file: File): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        val result = LinkedHashMap<String, String>()
        for (index in 0 until nodes.length) {
            val element = nodes.item(index) as Element
            result[element.getAttribute("name")] = element.textContent.trim()
        }
        return result
    }

    @Test
    fun theMacroLabelsAreFragmentsWithNoFormatSpecifier() {
        val files = stringFiles()
        assertTrue("No strings.xml files found under $resDir", files.size >= 2)
        for (file in files) {
            val values = strings(file)
            for (label in labels) {
                val text = values[label]
                assertTrue("${file.parentFile!!.name} has no $label", text != null)
                assertFalse(
                    "$label in ${file.parentFile!!.name} is rendered without arguments, so it must hold " +
                        "no format specifier: \"$text\"",
                    text!!.contains('%'),
                )
                assertTrue("$label in ${file.parentFile!!.name} is empty", text.isNotBlank())
            }
        }
    }
}
