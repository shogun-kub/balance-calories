package com.balancecalories.core.food

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class FoodMathTest {

    private val oats = Product(
        id = 7,
        name = "Oats, dry",
        source = ProductSource.STARTER,
        kcal100g = 389.0,
        protein100g = 16.9,
        fat100g = 6.9,
        carbs100g = 66.3,
        fiber100g = 10.6,
        sugar100g = 1.0,
        salt100g = 0.0,
    )

    @Test
    @DisplayName("scaling is linear: 50 g is exactly half of 100 g")
    fun scalesLinearly() {
        val portion = FoodMath.portion(oats, grams = 50.0)

        assertEquals(194.5, portion.kcal!!, 1e-9)
        assertEquals(8.45, portion.proteinG!!, 1e-9)
        assertEquals(3.45, portion.fatG!!, 1e-9)
        assertEquals(33.15, portion.carbsG!!, 1e-9)
    }

    @Test
    @DisplayName("100 g is the stored value unchanged")
    fun hundredGramsIsIdentity() {
        val portion = FoodMath.portion(oats, grams = 100.0)

        assertEquals(389.0, portion.kcal!!, 1e-12)
    }

    @Test
    @DisplayName("zero grams is zero energy, not a missing value")
    fun zeroGramsIsZero() {
        val portion = FoodMath.portion(oats, grams = 0.0)

        assertEquals(0.0, portion.kcal!!, 1e-12)
    }

    @Test
    @DisplayName("a missing per-100 g value stays missing in the portion")
    fun missingStaysMissing() {
        val unknownFibre = Product(name = "Mystery", source = ProductSource.OFF, kcal100g = 100.0)

        val portion = FoodMath.portion(unknownFibre, grams = 250.0)

        assertEquals(250.0, portion.kcal!!, 1e-9)
        assertNull(portion.fiberG)
        assertNull(portion.proteinG)
    }

    @Test
    @DisplayName("a product with no energy yields no energy, and says so through hasEnergy")
    fun noEnergyStaysUnknown() {
        val noEnergy = Product(name = "Nothing known", source = ProductSource.OFF)

        assertTrue(!noEnergy.hasEnergy)
        assertNull(FoodMath.portion(noEnergy, grams = 100.0).kcal)
    }

    @Test
    @DisplayName("water is honestly zero, and that counts as having energy data")
    fun zeroKcalCountsAsKnown() {
        val water = Product(name = "Water", source = ProductSource.STARTER, kcal100g = 0.0)

        assertTrue(water.hasEnergy)
        assertEquals(0.0, FoodMath.portion(water, grams = 500.0).kcal!!, 1e-12)
    }

    @Test
    @DisplayName("negative grams is a programming error, not a silently negative meal")
    fun negativeGramsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            FoodMath.portion(oats, grams = -1.0)
        }
    }

    @Test
    @DisplayName("the Russian name is used for a Russian locale and falls back when absent")
    fun displayNameFollowsLanguage() {
        val bilingual = oats.copy(nameRu = "Овсяные хлопья")

        assertEquals("Овсяные хлопья", bilingual.displayName("ru"))
        assertEquals("Овсяные хлопья", bilingual.displayName("ru-RU"))
        assertEquals("Oats, dry", bilingual.displayName("en"))
        assertEquals("Oats, dry", bilingual.displayName(null))
        // No Russian name recorded: show the English one rather than an empty line.
        assertEquals("Oats, dry", oats.displayName("ru"))
    }
}
