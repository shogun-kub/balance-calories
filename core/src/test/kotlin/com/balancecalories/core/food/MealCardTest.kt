package com.balancecalories.core.food

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Planned meal times and the numbers on a meal card.
 *
 * A card prints three things that came from elsewhere - the kcal sum, the macro sums and the share of the
 * target - and each is pinned here so a card cannot quietly disagree with the day's total.
 */
class MealCardTest {

    private fun entry(meal: Meal, kcal: Double, protein: Double?, fat: Double?, carbs: Double?) = LoggedEntry(
        id = 0,
        productId = null,
        name = "x",
        meal = meal,
        grams = 100.0,
        kcal = kcal,
        proteinG = protein,
        fatG = fat,
        carbsG = carbs,
    )

    @Test
    fun `the default plan is breakfast at eight, lunch at thirteen, dinner at nineteen`() {
        assertEquals(8 * 60, MealTimes.DEFAULT.minutesOf(Meal.BREAKFAST))
        assertEquals(13 * 60, MealTimes.DEFAULT.minutesOf(Meal.LUNCH))
        assertEquals(19 * 60, MealTimes.DEFAULT.minutesOf(Meal.DINNER))
        assertNull(MealTimes.DEFAULT.minutesOf(Meal.SNACK))
    }

    @Test
    fun `a stored plan round-trips and keeps its minutes`() {
        val times = MealTimes(7 * 60 + 15, 12 * 60 + 40, 20 * 60)

        assertEquals(times, MealTimes.decode(times.encode()))
        assertEquals("435,760,1200", times.encode())
    }

    @Test
    fun `a partly broken plan falls back as a whole`() {
        // One plausible hour and one impossible one must not produce a half-honoured plan.
        assertEquals(MealTimes.DEFAULT, MealTimes.decode("600,-5,1140"))
        assertEquals(MealTimes.DEFAULT, MealTimes.decode("600,1140"))
        assertEquals(MealTimes.DEFAULT, MealTimes.decode("600,abc,1140"))
        assertEquals(MealTimes.DEFAULT, MealTimes.decode("600,780,1440"))
        assertEquals(MealTimes.DEFAULT, MealTimes.decode(null))
    }

    @Test
    fun `clock text is strict about its shape`() {
        assertEquals(8 * 60 + 30, MealTimes.parseClock("08:30"))
        assertEquals(8 * 60 + 30, MealTimes.parseClock("8:30"))
        assertEquals(0, MealTimes.parseClock("00:00"))
        assertEquals(23 * 60 + 59, MealTimes.parseClock("23:59"))

        assertNull(MealTimes.parseClock("8.30"))
        assertNull(MealTimes.parseClock("0830"))
        assertNull(MealTimes.parseClock("24:00"))
        assertNull(MealTimes.parseClock("12:60"))
        assertNull(MealTimes.parseClock(""))
        assertNull(MealTimes.parseClock("ab:cd"))
    }

    @Test
    fun `a clock time is printed zero padded`() {
        assertEquals("08:00", MealTimes.formatClock(8 * 60))
        assertEquals("00:05", MealTimes.formatClock(5))
        assertEquals("23:59", MealTimes.formatClock(23 * 60 + 59))
    }

    @Test
    fun `a meal card sums only its own entries`() {
        val intake = DailyIntake(
            eatenKcal = 700.0,
            targetKcal = 2283,
            meals = listOf(
                entry(Meal.BREAKFAST, 200.0, 10.0, 5.0, 30.0),
                entry(Meal.BREAKFAST, 100.0, 4.0, 2.0, 12.0),
                entry(Meal.LUNCH, 400.0, 30.0, null, 20.0),
            ),
        )

        val breakfast = intake.totalsFor(Meal.BREAKFAST)
        assertEquals(300.0, breakfast.kcal, 1e-9)
        assertEquals(14.0, breakfast.proteinG, 1e-9)
        assertEquals(7.0, breakfast.fatG, 1e-9)
        assertEquals(42.0, breakfast.carbsG, 1e-9)
        assertEquals(2, breakfast.entryCount)
        assertTrue(intake.totalsFor(Meal.DINNER).isEmpty)

        // A null macro is nothing eaten, not a reason to drop the entry from the sum.
        assertEquals(400.0, intake.totalsFor(Meal.LUNCH).kcal, 1e-9)
        assertEquals(0.0, intake.totalsFor(Meal.LUNCH).fatG, 1e-9)
    }

    @Test
    fun `a meal share is of the day target, not of what was eaten`() {
        val intake = DailyIntake(
            eatenKcal = 300.0,
            targetKcal = 2283,
            meals = listOf(entry(Meal.BREAKFAST, 300.0, 10.0, 5.0, 30.0)),
        )

        assertEquals(13, intake.shareOfTargetFor(Meal.BREAKFAST))
        assertEquals(0, intake.shareOfTargetFor(Meal.LUNCH))
        // A broken target has no share to give.
        assertEquals(0, intake.copy(targetKcal = 0).shareOfTargetFor(Meal.BREAKFAST))
    }
}
