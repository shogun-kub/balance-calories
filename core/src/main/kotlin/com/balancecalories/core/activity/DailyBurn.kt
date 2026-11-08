package com.balancecalories.core.activity

/**
 * One local day's expenditure, with the origin of the number kept visible.
 *
 * Precedence, and why it is this order:
 *
 * 1. [totalKcal] — Health Connect's own total burned. It already includes resting metabolism as the band
 *    computed it, so adding anything to it would double-count.
 * 2. [bmrKcal] + [activeKcal] — when only the active part was recorded. The app's own BMR is used for the
 *    resting half, and that mix is why [burnedIsMeasured] is true but [usesFormulaBmr] is not false.
 * 3. [palKcal] — no band data at all, so the budget's PAL estimate stands in. [quality] is then
 *    [ActivityQuality.ESTIMATED] and the UI must say so.
 *
 * The budget model (ADR-004) keeps the daily *target* fixed from TDEE and shows the burn as context; this
 * type deliberately has no field for "calories earned back", because that feature does not exist.
 */
data class DailyBurn(
    val quality: ActivityQuality,
    /** Preserved as reported: steps are a count, not an estimate. */
    val steps: Int? = null,
    val activeKcal: Double? = null,
    val totalKcal: Double? = null,
    val distanceM: Double? = null,
    val sleepMinutes: Int? = null,
    val restingHr: Int? = null,
    /** The app's own BMR for this day, from the profile's weight at that date. */
    val bmrKcal: Double,
    /** BMR × the profile's activity multiplier, used only when there is no band data. */
    val palKcal: Double,
) {
    init {
        require(bmrKcal >= 0.0) { "bmrKcal must not be negative, was $bmrKcal" }
        require(palKcal >= 0.0) { "palKcal must not be negative, was $palKcal" }
        activeKcal?.let { require(it >= 0.0) { "activeKcal must not be negative, was $it" } }
    }

    /** Total expenditure for the day, by the precedence documented on the class. */
    val burnedKcal: Double
        get() = totalKcal ?: activeKcal?.let { bmrKcal + it } ?: palKcal

    /** Whether any part of [burnedKcal] came from the band rather than from a formula. */
    val burnedIsMeasured: Boolean
        get() = totalKcal != null || activeKcal != null

    /**
     * True when the resting half is the app's formula even though the active half is measured.
     *
     * Worth knowing for the debug screen: "BMR + active" is a hybrid, and calling it a measurement without
     * qualification would be the kind of rounding-up the product rules forbid.
     */
    val mixesFormulaBmr: Boolean
        get() = totalKcal == null && activeKcal != null

    /** How much of the figure is the palette of guesswork, for the UI's one-line explanation. */
    val source: BurnSource
        get() = when {
            totalKcal != null -> BurnSource.BAND_TOTAL
            activeKcal != null -> BurnSource.BAND_ACTIVE_PLUS_BMR
            else -> BurnSource.PAL_ESTIMATE
        }

    companion object {

        /** The no-band case: the formula is the number, and it is labelled as one. */
        fun estimated(bmrKcal: Double, palKcal: Double): DailyBurn = DailyBurn(
            quality = ActivityQuality.ESTIMATED,
            bmrKcal = bmrKcal,
            palKcal = palKcal,
        )
    }
}

/** Where a day's burn figure came from, in the order of trustworthiness. */
enum class BurnSource {
    /** The band's own total for the day. */
    BAND_TOTAL,

    /** The band's active calories plus the app's BMR formula. */
    BAND_ACTIVE_PLUS_BMR,

    /** No band data: BMR × PAL. */
    PAL_ESTIMATE,
}
