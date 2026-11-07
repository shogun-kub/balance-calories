package com.balancecalories.core.energy

/**
 * One day's energy ledger, already aggregated for a single local day.
 *
 * The type does no time-zone work and no network work: callers hand it numbers that came from
 * Health Connect, from the food log, or from a formula, and the product rules require those
 * origins to stay distinguishable upstream (AGENTS.md, hard rule 4).
 */
data class DailyEnergy(
    val intakeKcal: Double,
    val bmrKcal: Double,
    val activeKcal: Double,
) {
    init {
        require(intakeKcal >= 0.0) { "intakeKcal must not be negative, was $intakeKcal" }
        require(bmrKcal >= 0.0) { "bmrKcal must not be negative, was $bmrKcal" }
        require(activeKcal >= 0.0) { "activeKcal must not be negative, was $activeKcal" }
    }

    /** Total expenditure: resting plus everything above it. */
    val burnedKcal: Double get() = bmrKcal + activeKcal

    /** Positive = surplus, negative = deficit. This is the number the Today screen shows. */
    val balanceKcal: Double get() = intakeKcal - burnedKcal

    val inDeficit: Boolean get() = balanceKcal < 0.0
}
