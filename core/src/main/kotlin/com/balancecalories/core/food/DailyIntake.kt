package com.balancecalories.core.food

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
    val meal: Meal,
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

    fun entriesFor(meal: Meal): List<LoggedEntry> = meals.filter { it.meal == meal }

    fun eatenFor(meal: Meal): Double = entriesFor(meal).sumOf { it.kcal }

    val proteinG: Double get() = meals.sumOf { it.proteinG ?: 0.0 }
    val fatG: Double get() = meals.sumOf { it.fatG ?: 0.0 }
    val carbsG: Double get() = meals.sumOf { it.carbsG ?: 0.0 }

    /** True when any logged entry carries a derived number, so the screen can say "estimated". */
    val hasEstimates: Boolean get() = meals.any { it.wasEstimated }

    /**
     * The day's energy split by macro, for the Today gauge — the same maths the history chart draws, so the two
     * screens cannot disagree about how much of a day was protein.
     */
    val macroSplit: MacroSplit get() = MacroEnergy.split(proteinG, fatG, carbsG, eatenKcal)
}
