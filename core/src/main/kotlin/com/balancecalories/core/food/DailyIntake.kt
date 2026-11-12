package com.balancecalories.core.food

import kotlin.math.roundToInt

/**
 * One food already eaten, as a fact of history.
 *
 * The nutrition values are a snapshot taken when the entry was logged, not a live join to the product, so
 * correcting a product tomorrow cannot change what yesterday's plate contained.
 */
data class LoggedEntry(
    val id: Long,
    val productId: Long?,
    val name: String,
    val nameRu: String? = null,
    val mealKey: String,
    val grams: Double,
    val kcal: Double,
    val proteinG: Double? = null,
    val fatG: Double? = null,
    val carbsG: Double? = null,
    val wasEstimated: Boolean = false,
) {
    fun displayName(languageTag: String?): String {
        val wantsRussian = languageTag?.lowercase()?.startsWith("ru") == true
        return if (wantsRussian) nameRu?.takeIf { it.isNotBlank() } ?: name else name
    }
}

/**
 * The day's arithmetic: what was eaten against what the profile is allowed.
 *
 * Lives in `:core` because two screens showing different remainders is the fastest way to lose a user's
 * trust in the whole app (AGENTS.md, hard rule 12).
 */
data class DailyIntake(
    val eatenKcal: Double,
    val targetKcal: Int,
    val meals: List<LoggedEntry> = emptyList(),
) {

    /** Negative when the day is over budget: that is information, not an error. */
    val remainingKcal: Double get() = targetKcal - eatenKcal

    val isOverBudget: Boolean get() = eatenKcal > targetKcal

    fun entriesFor(mealKey: String): List<LoggedEntry> = meals.filter { it.mealKey == mealKey }

    fun eatenFor(mealKey: String): Double = entriesFor(mealKey).sumOf { it.kcal }

    val proteinG: Double get() = meals.sumOf { it.proteinG ?: 0.0 }
    val fatG: Double get() = meals.sumOf { it.fatG ?: 0.0 }
    val carbsG: Double get() = meals.sumOf { it.carbsG ?: 0.0 }

    /** What one meal contributed, for the four cards on Today. */
    fun totalsFor(mealKey: String): MealTotals {
        val entries = entriesFor(mealKey)
        return MealTotals(
            kcal = entries.sumOf { it.kcal },
            proteinG = entries.sumOf { it.proteinG ?: 0.0 },
            fatG = entries.sumOf { it.fatG ?: 0.0 },
            carbsG = entries.sumOf { it.carbsG ?: 0.0 },
            entryCount = entries.size,
        )
    }

    /**
     * The meal's share of the day's calorie target, as a whole percent - 412 of 2283 is 18.
     *
     * Shares are of the **target**, not of what was eaten so far: a card is answering "how much of today is
     * this meal", and a proportion of a total still being written would change every time a snack was added.
     * A target of zero has no share to speak of and answers 0.
     */
    fun shareOfTargetFor(mealKey: String): Int {
        if (targetKcal <= 0) return 0
        return (totalsFor(mealKey).kcal / targetKcal * 100.0).roundToInt()
    }

    /** True when any logged entry carries a derived number, so the screen can say "estimated". */
    val hasEstimates: Boolean get() = meals.any { it.wasEstimated }

    /**
     * The day's energy split by macro, for the Today gauge — the same maths the history chart draws, so the two
     * screens cannot disagree about how much of a day was protein.
     */
    val macroSplit: MacroSplit get() = MacroEnergy.split(proteinG, fatG, carbsG, eatenKcal)
}

/** One meal's contribution to the day: the numbers a meal card prints. */
data class MealTotals(
    val kcal: Double,
    val proteinG: Double,
    val fatG: Double,
    val carbsG: Double,
    val entryCount: Int,
) {

    /** A card with nothing in it still exists - it is an invitation, and it carries the planned time. */
    val isEmpty: Boolean get() = entryCount == 0
}
