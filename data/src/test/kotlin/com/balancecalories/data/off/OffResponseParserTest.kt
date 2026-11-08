package com.balancecalories.data.off

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parser tests run on the JVM against the payloads in `fixtures/off/`, so the shape of a real
 * OpenFoodFacts answer is exercised without a device and without the network.
 */
class OffResponseParserTest {

    private fun fixture(name: String): String {
        val file = File("../fixtures/off/$name")
        assertTrue("missing fixture: ${file.absolutePath}", file.isFile)
        return file.readText()
    }

    @Test
    fun foundProductCarriesEveryFieldTheAppUses() {
        val lookup = OffResponseParser.parse(200, fixture("found-nutella-like.json"))

        val product = (lookup as OffLookup.Found).product
        assertEquals("3017620422003", product.code)
        assertEquals("Nutella", product.name)
        assertEquals("Nutella", product.brand) // first of "Nutella, Ferrero"
        assertEquals("400 g e", product.quantityText)
        assertEquals(400.0, product.packGrams!!, 1e-9)
        assertNull(product.packMillilitres)
        assertEquals(539.0, product.energy!!.kcal, 1e-9)
        assertFalse("kcal was stated, so it is not an estimate", product.energy.estimated)
        assertEquals(6.3, product.proteinG!!, 1e-9)
        assertEquals(0.107, product.saltG!!, 1e-9)
        assertEquals(1793873005L, product.lastModifiedSeconds)
        assertTrue(product.isUsable)
        assertNull(product.unusableReason)
    }

    @Test
    fun absentProductIsReportedAsNotFoundRatherThanUnavailable() {
        val lookup = OffResponseParser.parse(404, fixture("not-found-404.json"))

        assertEquals(OffLookup.NotFound, lookup)
    }

    @Test
    fun theV3StatusWordIsUnderstoodAsSuccess() {
        val product = (OffResponseParser.parse(200, fixture("found-v3.json")) as OffLookup.Found).product

        assertEquals("Nutella", product.name)
        assertEquals("Нутелла", product.nameRu)
        assertEquals(539.0, product.energy!!.kcal, 1e-9)
        assertFalse(product.energy.estimated)
    }

    @Test
    fun aFailedV3StatusIsNotMistakenForAProduct() {
        val body = """{"status":"failure","errors":[{"field":{"id":"code"},"impact":{"id":"failure"}}]}"""

        val lookup = OffResponseParser.parse(200, body)

        assertTrue("expected a miss, got $lookup", lookup is OffLookup.NotFound || lookup is OffLookup.Invalid)
    }

    @Test
    fun v3InvalidCodeIsInvalidNotMissing() {
        val body = """{"status":"failure","errors":[{"message":{"id":"invalid_code"}}]}"""

        val lookup = OffResponseParser.parse(200, body)

        assertTrue("expected Invalid, got $lookup", lookup is OffLookup.Invalid)
    }

    @Test
    fun malformedCodeIsInvalidNotMissing() {
        val lookup = OffResponseParser.parse(200, fixture("invalid-code.json"))

        assertTrue("expected Invalid, got $lookup", lookup is OffLookup.Invalid)
    }

    @Test
    fun kilojoulesOnlyYieldAnEstimatedEnergyInKcal() {
        val product = (OffResponseParser.parse(200, fixture("kj-only-russian.json")) as OffLookup.Found).product

        assertEquals(1436.0 / 4.184, product.energy!!.kcal, 1e-9)
        assertTrue("energy came from joules, so it is an estimate", product.energy.estimated)
        assertEquals("Крупа гречневая", product.nameRu)
        assertEquals(1000.0, product.packGrams!!, 1e-9)
        assertEquals(50.0, product.servingGrams!!, 1e-9)
        assertEquals(1760000000L, product.lastModifiedSeconds) // arrived as a string
        assertEquals(0.01, product.saltG!!, 1e-9) // sodium 0.004 × 2.5
    }

    @Test
    fun aProductWithoutEnergyIsUnusableAndSaysWhy() {
        val body = """
            {"status":1,"product":{"code":"12345678","product_name":"Mystery snack","nutriments":{}}}
        """.trimIndent()

        val product = (OffResponseParser.parse(200, body) as OffLookup.Found).product

        assertFalse(product.isUsable)
        assertEquals(UnusableReason.NO_ENERGY, product.unusableReason)
    }

    @Test
    fun aProductWithoutANameIsUnusableAndSaysWhy() {
        val body = """
            {"status":1,"product":{"code":"12345678","nutriments":{"energy-kcal_100g":100}}}
        """.trimIndent()

        val product = (OffResponseParser.parse(200, body) as OffLookup.Found).product

        assertEquals(UnusableReason.NO_NAME, product.unusableReason)
    }

    @Test
    fun volumeIsKeptAsVolumeInsteadOfGuessedIntoGrams() {
        val grams = OffResponseParser.parseQuantity("1 l")
        assertEquals(null, grams.grams)
        assertEquals(1000.0, grams.millilitres!!, 1e-9)

        assertEquals(250.0, OffResponseParser.parseQuantity("250 ml").millilitres!!, 1e-9)
        assertEquals(750.0, OffResponseParser.parseQuantity("0,75 l").millilitres!!, 1e-9)
        assertEquals(1500.0, OffResponseParser.parseQuantity("1.5 kg").grams!!, 1e-9)
        assertEquals(400.0, OffResponseParser.parseQuantity("400 g e").grams!!, 1e-9)
        assertNull(OffResponseParser.parseQuantity("about a box").grams)
        assertNull(OffResponseParser.parseQuantity(null).grams)
    }

    @Test
    fun aServerErrorIsUnavailableNotNotFound() {
        val lookup = OffResponseParser.parse(503, null)

        assertTrue("expected Unavailable, got $lookup", lookup is OffLookup.Unavailable)
    }

    @Test
    fun garbageBodyIsUnavailable() {
        val lookup = OffResponseParser.parse(200, "<html>maintenance</html>")

        assertTrue(lookup is OffLookup.Unavailable)
    }
}
