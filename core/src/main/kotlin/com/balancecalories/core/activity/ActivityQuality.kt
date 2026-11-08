package com.balancecalories.core.activity

/**
 * How much a day's burn figure can be trusted (docs/03-architecture.md §8, product rule 4).
 *
 * The distinction exists because the alternative is a screen that shows four different kinds of number in
 * exactly the same font: a measured band total, a band total that is still filling up, a formula estimate
 * standing in for a missing band, and a day with no data at all.
 *
 * **The honest limitation.** Health Connect has no device attribution (decision D3 in the research), so an
 * absent band and a day genuinely spent on the sofa produce the same evidence. Nothing here claims to tell
 * them apart: [NO_BAND] means "this app has no band data for that day", not "you did not move".
 */
enum class ActivityQuality {
    /** A finished day, with band data, checked after the day closed. */
    COMPLETE,

    /**
     * Data exists, but not enough to call the day measured: it is still running, nobody looked at it since it
     * closed, or the numbers carry no app origin (a platform baseline rather than a band reading).
     */
    PARTIAL,

    /** No band data for this day at all. */
    NO_BAND,

    /** No band data, so the budget's PAL formula stands in. A formula, not a measurement. */
    ESTIMATED,
}

/**
 * Decides [ActivityQuality] from the two things the app can actually observe: whether there is data, and
 * whether the day has been inspected since it ended.
 *
 * Deliberately threshold-free. An invented "coverage below 80% is partial" rule would have to pretend that
 * empty hours mean missing data, when a sleeping user produces exactly that. "Was this day fetched after it
 * closed?" is a fact, and it is the fact that matters: a past day fetched while it was still happening is
 * incomplete no matter how plausible it looks.
 */
object ActivityQualityRules {

    fun classify(
        hasMeasuredData: Boolean,
        isToday: Boolean,
        syncedAfterWindowEnd: Boolean,
        /**
         * False when the day holds energy that **no app claims** — Health Connect's own platform layer writes a
         * constant daily total on some phones, and a baseline must not be presented as a measurement.
         * Defaults to true so callers that cannot know stay unchanged.
         */
        hasAttributedOrigin: Boolean = true,
    ): ActivityQuality = when {
        !hasMeasuredData -> ActivityQuality.NO_BAND
        isToday -> ActivityQuality.PARTIAL
        !syncedAfterWindowEnd -> ActivityQuality.PARTIAL
        !hasAttributedOrigin -> ActivityQuality.PARTIAL
        else -> ActivityQuality.COMPLETE
    }
}

/**
 * Stored form, as the `daily_activity.quality` column documents it: `complete|partial|no_band|estimated`.
 *
 * Lower case in storage keeps the column readable in an export and matches the values already written by the
 * schema documentation; the enum name is not a storage contract.
 */
fun ActivityQuality.toStorage(): String = when (this) {
    ActivityQuality.COMPLETE -> "complete"
    ActivityQuality.PARTIAL -> "partial"
    ActivityQuality.NO_BAND -> "no_band"
    ActivityQuality.ESTIMATED -> "estimated"
}

/**
 * Reads the stored form. An unknown value means the database was written by a newer version, which is treated
 * as [ActivityQuality.PARTIAL]: the safest reading is "we do not know how much to trust this number".
 */
fun activityQualityFromStorage(raw: String?): ActivityQuality = when (raw?.trim()?.lowercase()) {
    "complete" -> ActivityQuality.COMPLETE
    "partial" -> ActivityQuality.PARTIAL
    "no_band" -> ActivityQuality.NO_BAND
    "estimated" -> ActivityQuality.ESTIMATED
    else -> ActivityQuality.PARTIAL
}