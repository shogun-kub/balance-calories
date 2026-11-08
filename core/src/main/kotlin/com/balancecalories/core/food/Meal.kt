package com.balancecalories.core.food

/** The four slots a day is eaten in. Stored by name, so the order here is the display order. */
enum class Meal {
    BREAKFAST,
    LUNCH,
    DINNER,
    SNACK,
    ;

    companion object {
        fun fromStorage(value: String): Meal =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SNACK

        fun toStorage(meal: Meal): String = meal.name.lowercase()
    }
}
