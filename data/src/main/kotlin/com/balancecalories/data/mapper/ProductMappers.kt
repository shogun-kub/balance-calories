package com.balancecalories.data.mapper

import com.balancecalories.core.food.Product
import com.balancecalories.core.food.ProductSource
import com.balancecalories.data.db.entity.ProductEntity
import com.balancecalories.data.off.OffProduct

/**
 * Translates between the storage row and the domain type the rest of the app uses.
 *
 * `:app` never sees [ProductEntity], and `:core` never sees Room, so this file is the only place that
 * knows both — which is what keeps the UI testable and the maths portable.
 */
fun ProductEntity.toDomain(): Product = Product(
    id = id,
    barcode = barcode,
    name = name,
    nameRu = nameRu,
    brand = brand,
    source = ProductSource.fromStorage(source),
    kcal100g = kcal100g,
    protein100g = protein100g,
    fat100g = fat100g,
    carbs100g = carbs100g,
    fiber100g = fiber100g,
    sugar100g = sugar100g,
    salt100g = salt100g,
    isLiquid = isLiquid,
    servingGrams = servingGrams,
    packGrams = packGrams,
    imageUrl = imageUrl,
    isOverride = isOverride,
    energyEstimated = energyEstimated,
)

/** Used for products the user types in, and for corrections to an OFF product. */
fun Product.toEntity(createdAt: Long, updatedAt: Long): ProductEntity = ProductEntity(
    id = id,
    barcode = barcode,
    name = name,
    nameRu = nameRu,
    brand = brand,
    source = ProductSource.toStorage(source),
    kcal100g = kcal100g,
    protein100g = protein100g,
    fat100g = fat100g,
    carbs100g = carbs100g,
    fiber100g = fiber100g,
    sugar100g = sugar100g,
    salt100g = salt100g,
    isLiquid = isLiquid,
    servingGrams = servingGrams,
    packGrams = packGrams,
    imageUrl = imageUrl,
    offRev = null,
    isOverride = isOverride,
    energyEstimated = energyEstimated,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/**
 * An OpenFoodFacts product as a row to store.
 *
 * Returns null when the product cannot be logged at all (no name or no energy): storing a nameless,
 * calorie-less row would make "found" look like success and put a zero-calorie food on the plate.
 *
 * [existing] is the row already known for this barcode, when there is one. Passing it keeps the primary
 * key stable — food entries and recipe items point at it — and preserves fields OFF does not send in our
 * field subset, such as a hand-written Russian name.
 */
fun OffProduct.toEntity(existing: ProductEntity?, now: Long): ProductEntity? {
    if (!isUsable) return null
    return ProductEntity(
        id = existing?.id ?: 0L,
        barcode = code.ifBlank { existing?.barcode },
        name = name!!,
        nameRu = nameRu ?: existing?.nameRu,
        brand = brand ?: existing?.brand,
        source = ProductSource.toStorage(ProductSource.OFF),
        kcal100g = energy?.kcal,
        protein100g = proteinG,
        fat100g = fatG,
        carbs100g = carbsG,
        fiber100g = fiberG,
        sugar100g = sugarG,
        salt100g = saltG,
        // Sold by volume means the user thinks in millilitres; that is what is_liquid drives in the UI.
        isLiquid = packMillilitres != null || existing?.isLiquid == true,
        servingGrams = servingGrams ?: existing?.servingGrams,
        packGrams = packGrams ?: existing?.packGrams,
        imageUrl = imageUrl ?: existing?.imageUrl,
        offRev = lastModifiedSeconds.takeIf { it > 0L } ?: existing?.offRev,
        isOverride = existing?.isOverride ?: false,
        energyEstimated = energy?.estimated ?: false,
        createdAt = existing?.createdAt ?: now,
        updatedAt = now,
    )
}
