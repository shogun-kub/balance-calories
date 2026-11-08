package com.balancecalories.core.food

/**
 * A dish the user cooks, defined by what goes into the pan while it is still raw.
 *
 * The model follows the cooking research (docs/02-research.md §3.5) rather than the supermarket view of
 * food, and the difference is deliberately visible:
 *
 * - **Ingredient grams are raw.** That is what is actually weighed and put in the pan.
 * - **Water loss and gain change weight, not energy.** Meat loses 20–30% of its mass, rice and pasta absorb
 *   water and roughly double or triple. So the recipe keeps [cookedWeightG] as a *measured* number, and the
 *   per-100 g figure is computed against it — because that is what a portion is served from.
 * - **When the cooked weight is unknown the raw sum is used, and [usedRawWeight] says so.** A per-100 g value
 *   computed on raw weight for a stew is wrong in the direction of "looks like less food", so the UI has to
 *   be able to warn instead of pretending the number is comparable.
 *
 * [servings] is a division factor, not a portion: `servings = 4` means the pot is cut into four equal plates,
 * which is how people actually serve a stew. It is a Double because "1.5 servings" happens.
 */
data class Recipe(
    val id: Long = NEW,
    val profileId: Long,
    val name: String,
    /** Measured weight after cooking, grams. Null means "not measured yet". */
    val cookedWeightG: Double? = null,
    val servings: Double = 1.0,
    val notes: String? = null,
    val items: List<RecipeItem> = emptyList(),
) {

    companion object {
        const val NEW = 0L
    }
}

/** One ingredient of a [Recipe]: a product and how many raw grams of it the dish takes. */
data class RecipeItem(
    val product: Product,
    val grams: Double,
) {
    init {
        require(grams > 0.0) { "an ingredient needs a positive weight, was $grams" }
    }
}
