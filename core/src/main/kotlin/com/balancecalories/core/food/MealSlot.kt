package com.balancecalories.core.food

/**
 * The meal slots a **new** profile starts with.
 *
 * Until 2026-11-12 the app had four hard-coded slots and one clock table. The owner's correction: the number of
 * meals, their names and their times are his, and breakfast/lunch/dinner/snack are only the default set a new
 * profile begins with - so the slots became rows in the database (`meal` table, schema v7) and these constants
 * are what the seeding writes.
 *
 * The `key` is the **stable identity** of a slot and lives in `food_entry.meal`: renaming a meal changes its
 * name, never its key, so a year of entries cannot be rewritten by a rename. Keys are never shown.
 */
object MealDefaults {

    const val BREAKFAST = "breakfast"
    const val LUNCH = "lunch"
    const val DINNER = "dinner"
    const val SNACK = "snack"

    /** One default slot: its key, its planned time (null = no time, like snacks) and whether it is fixed. */
    data class Default(val key: String, val minutes: Int?, val fixed: Boolean)

    /**
     * The default set, in display order.
     *
     * Snacks are the only **fixed** slot: they carry no time and cannot be renamed or deleted, because "Перекус"
     * is the bucket everything unplanned falls into, including anything the app itself cannot place.
     */
    val SLOTS: List<Default> = listOf(
        Default(BREAKFAST, 8 * 60, false),
        Default(LUNCH, 13 * 60, false),
        Default(DINNER, 19 * 60, false),
        Default(SNACK, null, true),
    )

    fun isDefault(key: String): Boolean = SLOTS.any { it.key == key }

    /** The default name key for a slot: the app maps it to a string resource. Null for a generated key. */
    fun defaultNameKey(key: String): String? = SLOTS.firstOrNull { it.key == key }?.key
}

/**
 * One meal slot of one profile, as stored.
 *
 * [name] is blank when the person never renamed the slot, and the screen then prints the localized default name
 * for [key]. That keeps the store free of English words on a Russian phone: the defaults are translated where
 * translations live, and a rename stores exactly what was typed.
 */
data class MealSlot(
    val key: String,
    val name: String,
    val sortOrder: Int,
    val plannedMinutes: Int? = null,
    val fixed: Boolean = false,
) {

    /** A fixed slot (the snack bucket) is the one thing that cannot be removed or renamed. */
    val canRename: Boolean get() = !fixed

    val canDelete: Boolean get() = !fixed

    val hasDefaultName: Boolean get() = name.isBlank()
}

/**
 * Which slot a portion most likely belongs to, from the clock alone.
 *
 * The **nearest** planned time on a 24-hour circle, earlier slot winning a tie. Nearest rather than "latest that
 * has passed", because the two cases that get that rule wrong are the ones a person actually hits: food logged at
 * 07:50 belongs to the breakfast eight minutes away, not to yesterday's dinner, and food logged at 00:30 belongs
 * to the dinner five and a half hours behind rather than to a breakfast seven and a half hours ahead.
 *
 * A profile with no planned times at all answers with its first slot, which is the only answer that cannot be
 * wrong about a timetable that does not exist. Nothing is stored: this is a starting selection the person can
 * change with one tap.
 */
fun guessMealKey(slots: List<MealSlot>, minutesOfDay: Int): String? {
    val timed = slots.filter { it.plannedMinutes != null }
    if (timed.isEmpty()) return slots.firstOrNull()?.key
    val minutes = minutesOfDay.coerceIn(0, MealTimes.MINUTES_PER_DAY - 1)
    return timed.minByOrNull { slot ->
        val direct = kotlin.math.abs(slot.plannedMinutes!! - minutes)
        minOf(direct, MealTimes.MINUTES_PER_DAY - direct)
    }?.key
}
