package com.balancecalories.data.off

import com.balancecalories.core.food.EnergyPer100g

/**
 * One product as OpenFoodFacts describes it, reduced to the fields the app actually uses.
 *
 * Every value is nullable on purpose: OFF is crowd-sourced, and a missing field is information
 * ("nobody recorded this") rather than something to default to zero. [energy] carries its own
 * `estimated` flag so the UI can distinguish a stated kcal value from one derived by [LabelEnergy].
 */
data class OffProduct(
    val code: String,
    val name: String?,
    val nameRu: String?,
    val brand: String?,
    val quantityText: String?,
    val servingText: String?,
    val servingGrams: Double?,
    val packGrams: Double?,
    val packMillilitres: Double?,
    val energy: EnergyPer100g?,
    val proteinG: Double?,
    val fatG: Double?,
    val carbsG: Double?,
    val fiberG: Double?,
    val sugarG: Double?,
    val saltG: Double?,
    val imageUrl: String?,
    val lastModifiedSeconds: Long,
    val nutritionDataPer: String?,
    val declaresNoNutritionData: Boolean,
) {

    /**
     * Whether this product can actually be logged.
     *
     * A name alone is not enough — an entry with no energy would put a zero-calorie food on the plate.
     * The screen offers "add manually" instead.
     */
    val isUsable: Boolean get() = !name.isNullOrBlank() && energy != null

    /** Why the product cannot be logged, for a sentence in the UI. */
    val unusableReason: UnusableReason?
        get() = when {
            name.isNullOrBlank() -> UnusableReason.NO_NAME
            energy == null -> UnusableReason.NO_ENERGY
            else -> null
        }
}

enum class UnusableReason { NO_NAME, NO_ENERGY }

/** The outcome of looking a barcode up, with failure modes kept apart. */
sealed interface OffLookup {

    /** The server knows this code; [product] may still be unusable, see [OffProduct.isUsable]. */
    data class Found(val product: OffProduct) : OffLookup

    /** The server said the product does not exist (HTTP 404). Worth remembering. */
    data object NotFound : OffLookup

    /** The barcode is not a barcode we should send — wrong length or non-digits. */
    data class Invalid(val barcode: String) : OffLookup

    /** Network trouble, rate limiting or a server error. Worth retrying later. */
    data class Unavailable(val reason: String) : OffLookup
}
