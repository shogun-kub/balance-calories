package com.balancecalories.core.food

/**
 * The clock helpers for planned meal times: `08:30` to 510 and back, plus the stored form of the old
 * three-times setting.
 *
 * The times themselves moved into the `meal` table with schema v7, because the owner wanted to add, rename and
 * delete meals rather than only retime three of them. What is left here is the parsing every screen shares, and
 * [encode]/[decode] because the v6 -> v7 migration reads the per-profile setting this replaced.
 */
data class MealTimes(
    val breakfastMinutes: Int,
    val lunchMinutes: Int,
    val dinnerMinutes: Int,
) {

    /** `480,780,1140`, the same comma-separated shape the macro norm uses in `app_setting`. */
    fun encode(): String = "$breakfastMinutes,$lunchMinutes,$dinnerMinutes"

    companion object {

        const val MINUTES_PER_DAY = 24 * 60

        /** 08:00, 13:00, 19:00 - what [MealDefaults] seeds a new profile with. */
        val DEFAULT = MealTimes(8 * 60, 13 * 60, 19 * 60)

        fun of(breakfast: Int, lunch: Int, dinner: Int): MealTimes = MealTimes(breakfast, lunch, dinner)

        /**
         * Parse a stored value, falling back to [DEFAULT] as a whole.
         *
         * A partially valid value is not half-honoured: a wrong "10:00,-5,19:00" would otherwise put one card
         * at a plausible hour and another nowhere, and nobody could tell which half was wrong.
         */
        fun decode(raw: String?): MealTimes {
            val parts = raw?.split(',')?.map { it.trim() } ?: return DEFAULT
            if (parts.size != 3) return DEFAULT
            val minutes = parts.map { it.toIntOrNull() ?: return DEFAULT }
            if (minutes.any { it < 0 || it >= MINUTES_PER_DAY }) return DEFAULT
            return MealTimes(minutes[0], minutes[1], minutes[2])
        }

        /**
         * `"08:30"` to 510, or null when the text is not a clock time.
         *
         * Strict on purpose: `toIntOrNull` on "8.30", "8 30" or "0830" would quietly accept three ways of
         * typing the same thing and then store whichever one came out, and the person would see the field
         * rewrite itself after leaving it.
         */
        fun parseClock(text: String): Int? {
            val trimmed = text.trim()
            if (trimmed.length !in 4..5) return null
            val separator = trimmed.indexOf(':')
            if (separator < 1 || separator > 2) return null
            val hours = trimmed.substring(0, separator).toIntOrNull() ?: return null
            val minutesText = trimmed.substring(separator + 1)
            if (minutesText.length != 2) return null
            val minutes = minutesText.toIntOrNull() ?: return null
            if (hours !in 0..23 || minutes !in 0..59) return null
            return hours * 60 + minutes
        }

        /** 510 to `"08:30"`. A zero-padded 24-hour clock, which both languages here read the same way. */
        fun formatClock(minutes: Int): String {
            val clamped = minutes.coerceIn(0, MINUTES_PER_DAY - 1)
            return "%02d:%02d".format(clamped / 60, clamped % 60)
        }
    }
}
