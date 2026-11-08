package com.balancecalories.data.off

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Search-response tests, run on the JVM against a payload captured from the live endpoint on 2026-11-08
 * (`fixtures/off/search-hits.json`: query "nutella", three hits, 631 matches in total).
 *
 * The fixture is the point: search answers with a different shape from a barcode lookup, and the difference
 * is exactly where a parser goes wrong.
 */
class OffSearchParserTest {

    private fun fixture(name: String): String {
        val file = File("../fixtures/off/$name")
        assertTrue("missing fixture: ${file.absolutePath}", file.isFile)
        return file.readText()
    }

    @Test
    fun searchResultCarriesTheTotalCountNotJustThePageLength() {
        val search = OffSearchParser.parse(200, fixture("search-hits.json"))

        assertTrue(search is OffSearch.Found)
        val found = search as OffSearch.Found
        // 631 matches, 3 on the page: a UI that reported products.size would tell the user "3 found".
        assertEquals(631, found.totalCount)
        assertEquals(3, found.products.size)
    }

    @Test
    fun everyHitIsParsedThroughTheProductParser() {
        val found = OffSearchParser.parse(200, fixture("search-hits.json")) as OffSearch.Found

        val nutella = found.products.first { it.code == "0098008952506" }
        assertEquals("Nutella", nutella.name)
        assertEquals("Nutella", nutella.brand)
        assertEquals(540.54054054054, nutella.energy!!.kcal, 1e-9)
        assertFalse("kcal was stated by the label, not derived", nutella.energy.estimated)
        assertTrue(nutella.isUsable)
    }

    @Test
    fun anEmptyResultIsAnAnswerRatherThanAFailure() {
        val body = """{"hits":[],"count":0,"page":1,"page_size":3,"page_count":0}"""

        val found = OffSearchParser.parse(200, body) as OffSearch.Found
        assertEquals(0, found.totalCount)
        assertTrue(found.isEmpty)
    }

    @Test
    fun aHitWithoutACodeIsDropped() {
        // The code is the product's identity: a result that cannot be stored is not a result we can offer.
        val body = """
            {"hits":[
              {"product_name":"No barcode here","nutriments":{"energy-kcal_100g":100}},
              {"code":"4001234567890","product_name":"Has a barcode","nutriments":{"energy-kcal_100g":200}}
            ],"count":2}
        """.trimIndent()

        val found = OffSearchParser.parse(200, body) as OffSearch.Found
        assertEquals(1, found.products.size)
        assertEquals("4001234567890", found.products.single().code)
        assertEquals("count still describes the server's answer", 2, found.totalCount)
    }

    @Test
    fun aHitWithoutEnergyIsKeptAndSaysWhy() {
        val body = """
            {"hits":[{"code":"4630250260075","product_name":"Молоко 1,5"}],"count":1}
        """.trimIndent()

        val found = OffSearchParser.parse(200, body) as OffSearch.Found
        val milk = found.products.single()
        assertFalse("no energy cannot be logged", milk.isUsable)
        assertEquals(UnusableReason.NO_ENERGY, milk.unusableReason)
    }

    @Test
    fun aServerErrorIsUnavailableNotAnEmptyResult() {
        assertTrue(OffSearchParser.parse(503, null) is OffSearch.Unavailable)
        assertTrue(OffSearchParser.parse(200, "not json at all") is OffSearch.Unavailable)
        // A 200 whose body has no hits array is a contract change, not "nothing found".
        assertTrue(OffSearchParser.parse(200, """{"products":[]}""") is OffSearch.Unavailable)
    }
}
