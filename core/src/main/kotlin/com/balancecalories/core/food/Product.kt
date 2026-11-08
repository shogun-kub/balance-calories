package com.balancecalories.core.food

/** Where a product came from. The UI shows this, because a user's own entry outranks a crowd-sourced one. */
enum class ProductSource {
    /** OpenFoodFacts, crowd-sourced and sometimes wrong. */
    OFF,

    /** Typed or corrected by the user. Never overwritten by a refresh. */
    USER,

    /** The bundled starter pack: composition-table estimates, good for cooking. */
    STARTER,
    ;

    companion object {
        fun fromStorage(value: String): ProductSource =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: OFF

        fun toStorage(source: ProductSource): String = source.name.lowercase()
    }
}

/**
 * A food as the rest of the app sees it.
 *
 * Nutrition is per 100 g (or 100 ml when [isLiquid]) and every value is nullable: "nobody recorded this"
 * is a real state, and defaulting it to zero would put a zero-calorie food on the plate.
 *
 * Two flags carry the honesty of the number: [energyEstimated] says the kcal was derived rather than
 * stated, and [isOverride] says the user corrected the row and a refresh must leave it alone.
 */
data class Product(
    val id: Long = NEW,
    val barcode: String? = null,
    val name: String,
    val nameRu: String? = null,
    val brand: String? = null,
    val source: ProductSource,
    val kcal100g: Double? = null,
    val protein100g: Double? = null,
    val fat100g: Double? = null,
    val carbs100g: Double? = null,
    val fiber100g: Double? = null,
    val sugar100g: Double? = null,
    val salt100g: Double? = null,
    val isLiquid: Boolean = false,
    val servingGrams: Double? = null,
    val packGrams: Double? = null,
    val imageUrl: String? = null,
    val isOverride: Boolean = false,
    val energyEstimated: Boolean = false,
) {

    /** Energy is present and non-negative; zero is legitimate (water), absent is not. */
    val hasEnergy: Boolean get() = kcal100g != null && kcal100g >= 0.0

    /**
     * The name to show for a language tag.
     *
     * Russian falls back to the English name rather than showing a blank row: an untranslated product is
     * still a product, and a half-empty card is worse than an English word inside a Russian screen.
     */
    fun displayName(languageTag: String?): String {
        val wantsRussian = languageTag?.lowercase()?.startsWith("ru") == true
        return if (wantsRussian) nameRu?.takeIf { it.isNotBlank() } ?: name else name
    }

    companion object {
        const val NEW: Long = 0L
    }
}
