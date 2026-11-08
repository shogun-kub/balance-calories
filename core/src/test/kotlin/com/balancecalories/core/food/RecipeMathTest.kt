package com.balancecalories.core.food

import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Recipe arithmetic, including the property the plan asks for: scaling every ingredient scales the dish,
 * and the per-100 g density does not move.
 *
 * Deterministic randomness (a fixed seed) rather than a library generator: property-based in spirit, and a
 * failure is reproducible without a seed printed in a log nobody reads.
 */
class RecipeMathTest {

    private fun product(
        name: String,
        kcal: Double?,
        protein: Double? = 0.0,
        fat: Double? = 0.0,
        carbs: Double? = 0.0,
        fiber: Double? = null,
    ) = Product(
        id = 1,
        name = name,
        source = ProductSource.STARTER,
        kcal100g = kcal,
        protein100g = protein,
        fat100g = fat,
        carbs100g = carbs,
        fiber100g = fiber,
    )

    private fun chicken(grams: Double) = RecipeItem(product("Chicken breast", 165.0, 31.0, 3.6, 0.0), grams)
    private fun rice(grams: Double) = RecipeItem(product("Rice, dry", 344.0, 6.8, 0.6, 78.0), grams)
    private fun oil(grams: Double) = RecipeItem(product("Olive oil", 884.0, 0.0, 100.0, 0.0), grams)

    @Test
    fun totalsAddUpTheRawComponents() {
        val totals = RecipeMath.totals(listOf(chicken(200.0), rice(100.0), oil(10.0)))

        assertEquals(310.0, totals.rawGrams, 1e-9)
        // 165*2 + 344*1 + 884*0.1
        assertEquals(330.0 + 344.0 + 88.4, totals.kcal!!, 1e-9)
        assertEquals(62.0 + 6.8, totals.proteinG!!, 1e-9)
        assertEquals(7.2 + 0.6 + 10.0, totals.fatG!!, 1e-9)
        assertEquals(78.0, totals.carbsG!!, 1e-9)
        assertEquals(0, totals.missingEnergyItems)
        assertTrue(totals.hasEnergy)
    }

    @Test
    fun oneIngredientWithoutEnergyMakesTheDishUnknownRatherThanUnderstated() {
        val unknown = RecipeItem(product("Mystery sauce", kcal = null, protein = null), 30.0)
        val totals = RecipeMath.totals(listOf(chicken(200.0), unknown))

        assertNull(totals.kcal, "summing only the known ingredients would understate the dish")
        assertNull(totals.proteinG)
        // The parts that do not depend on the unknown ingredient stay honest.
        assertEquals(230.0, totals.rawGrams, 1e-9)
        assertEquals(1, totals.missingEnergyItems)
    }

    @Test
    fun cookedWeightSetsTheBasisAndRawWeightIsOnlyTheFallback() {
        val totals = RecipeMath.totals(listOf(chicken(200.0), rice(100.0)))

        val cooked = RecipeMath.weightBasis(totals, cookedWeightG = 480.0)
        assertEquals(RecipeMath.BasisKind.COOKED, cooked.kind)
        assertEquals(480.0, cooked.grams, 1e-9)
        assertTrue(!cooked.usedRawWeight)

        val fallback = RecipeMath.weightBasis(totals, cookedWeightG = null)
        assertEquals(RecipeMath.BasisKind.RAW_FALLBACK, fallback.kind)
        assertEquals(300.0, fallback.grams, 1e-9)
        assertTrue(fallback.usedRawWeight)
    }

    @Test
    fun aZeroCookedWeightIsNotABasis() {
        // Treating 0 g as measured would divide by zero and claim infinite calories per 100 g.
        val totals = RecipeMath.totals(listOf(chicken(200.0)))
        val basis = RecipeMath.weightBasis(totals, cookedWeightG = 0.0)

        assertEquals(RecipeMath.BasisKind.RAW_FALLBACK, basis.kind)
        assertEquals(200.0, basis.grams, 1e-9)
    }

    @Test
    fun perHundredGramsUsesTheCookedWeight() {
        val totals = RecipeMath.totals(listOf(chicken(200.0), rice(100.0), oil(10.0)))
        val basis = RecipeMath.weightBasis(totals, cookedWeightG = 500.0)

        val per100 = RecipeMath.per100g(totals, basis)!!

        assertEquals(100.0, per100.grams, 1e-9)
        // 762.4 kcal over 500 g = 152.48 per 100 g.
        assertEquals(762.4 / 5.0, per100.kcal!!, 1e-9)
    }

    @Test
    fun aServingIsTheDishDividedByItsServings() {
        val totals = RecipeMath.totals(listOf(chicken(200.0), rice(100.0), oil(10.0)))
        val basis = RecipeMath.weightBasis(totals, cookedWeightG = 500.0)

        val quarter = RecipeMath.perServing(totals, basis, servings = 4.0)!!

        assertEquals(125.0, quarter.grams, 1e-9)
        assertEquals(762.4 / 4.0, quarter.kcal!!, 1e-9)
    }

    @Test
    fun aPortionByWeightScalesTheDish() {
        val totals = RecipeMath.totals(listOf(chicken(200.0), rice(100.0)))
        val basis = RecipeMath.weightBasis(totals, cookedWeightG = 480.0)

        val half = RecipeMath.portion(totals, basis, grams = 240.0)!!
        assertEquals(240.0, half.grams, 1e-9)
        assertEquals(totals.kcal!! / 2.0, half.kcal!!, 1e-9)

        val all = RecipeMath.portion(totals, basis, grams = 480.0)!!
        assertEquals(totals.kcal!!, all.kcal!!, 1e-9)
    }

    @Test
    fun anImpossiblePortionIsRefusedRatherThanGuessed() {
        val totals = RecipeMath.totals(listOf(chicken(200.0)))
        val basis = RecipeMath.weightBasis(totals, cookedWeightG = 160.0)

        assertNull(RecipeMath.per100g(totals, RecipeMath.WeightBasis(0.0, RecipeMath.BasisKind.COOKED)), "a dish with no measurable weight has no per-100 g value")
        assertNull(RecipeMath.perServing(totals, basis, servings = 0.0))
        assertNotNull(RecipeMath.portion(totals, basis, grams = 1.0))

        val thrown = runCatching { RecipeMath.portion(totals, basis, grams = 0.0) }.exceptionOrNull()
        assertTrue(thrown is IllegalArgumentException, "zero grams is a caller bug, not a portion")
    }

    /**
     * The property the plan asks for: doubling every ingredient doubles the energy and the weight, and leaves
     * the per-100 g density untouched — for arbitrary weights, not just the ones chosen above.
     */
    @Test
    fun doublingEveryIngredientDoublesTheDishAndKeepsTheDensity() {
        val random = Random(seed = 20261108)
        repeat(200) {
            val parts = List(random.nextInt(1, 5)) {
                when (random.nextInt(3)) {
                    0 -> chicken(random.nextDouble(1.0, 900.0))
                    1 -> rice(random.nextDouble(1.0, 900.0))
                    else -> oil(random.nextDouble(0.1, 200.0))
                }
            }
            val single = RecipeMath.totals(parts)
            val doubled = RecipeMath.totals(parts.map { it.copy(grams = it.grams * 2.0) })

            assertEquals(single.rawGrams * 2.0, doubled.rawGrams, 1e-9)
            assertEquals(single.kcal!! * 2.0, doubled.kcal!!, 1e-6)

            // A cooked weight that scales with the dish, so the density must not move at all.
            val one = RecipeMath.weightBasis(single, single.rawGrams * 0.8)
            val two = RecipeMath.weightBasis(doubled, doubled.rawGrams * 0.8)
            assertEquals(
                RecipeMath.per100g(single, one)!!.kcal!!,
                RecipeMath.per100g(doubled, two)!!.kcal!!,
                1e-6,
            )
        }
    }

    /** A recipe with no ingredients has no energy and no weight; it must not look like an empty zero-calorie dish. */
    @Test
    fun anEmptyRecipeHasNoNumbers() {
        val totals = RecipeMath.totals(emptyList())
        val basis = RecipeMath.weightBasis(totals, cookedWeightG = null)

        assertEquals(0.0, totals.rawGrams, 1e-9)
        assertNull(totals.kcal) // sum() of nothing is 0.0 for known products, but there are none
        assertNull(RecipeMath.per100g(totals, basis))
        assertEquals(0, totals.missingEnergyItems)
    }

    @Test
    fun kcalPerHundredIsNullWhenEnergyIsUnknown() {
        val totals = RecipeMath.totals(listOf(RecipeItem(product("Mystery", kcal = null), 100.0)))
        val basis = RecipeMath.weightBasis(totals, cookedWeightG = 90.0)

        assertNull(RecipeMath.kcalPer100g(totals, basis))
    }
}
