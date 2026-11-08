package com.balancecalories.core.food

/**
 * A product the user has eaten before, with how often and how recently.
 *
 * This exists for one reason: the second time a food is logged should not require a search. Ordering is by
 * [lastEatenAt] because the list answers "what did I just eat", while [times] lets the UI mark a habit
 * without a second, differently ordered list competing for the same screen space.
 *
 * Both numbers are observations from the food log, not predictions: nothing here is guessed, and a day with
 * no entries produces no rows rather than a suggestion.
 */
data class RecentlyEaten(
    val product: Product,
    /** How many times this product appears in the log, deleted entries excluded. */
    val times: Int,
    /** UTC epoch millis of the most recent entry. */
    val lastEatenAt: Long,
)
