package com.balancecalories.core.food

/**
 * When the three named meals are supposed to happen, in minutes from midnight.
 *
 * The owner asked for pre-defined meals with a planned time (2026-11-12): a card that says "Завтрак 08:00"
 * answers "what is still ahead today" without opening anything, and a time is a plan, not a measurement, so
 * it is stored per profile as a setting rather than inferred from when food happened to be logged.
 *
 * Eating earlier or later than the plan is not an error and nothing is compared against these minutes: they
 * label the card and sort nothing. Snacks have no time - they happen when they happen.
 */
data class MealTimes(
    val breakfastMinutes: Int,
    val lunchMinutes: Int,
    val dinnerMinutes: Int,
) {

    fun minutesOf(meal: Meal): Int? = when (meal) {
        Meal.BREAKFAST -> breakfastMinutes
        Meal.LUNCH -> lunchMinutes
        Meal.DINNER -> dinnerMinutes
        Meal.SNACK -> null
    }

    /** `480,780,1140`, the same comma-separated shape the macro norm uses in `app_setting`. */
    fun encode(): String = "$breakfastMinutes,$lunchMinutes,$dinnerMinutes"

    companion object {

        const val MINUTES_PER_DAY = 24 * 60

        /** 08:00, 13:00, 19:00 - three meals a person can keep, and what the owner named. */
        val DEFAULT = MealTimes(8 * 60, 13 * 60, 19 * 60)

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
