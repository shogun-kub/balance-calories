package com.balancecalories.data.repo

import com.balancecalories.core.food.DailyIntake
import com.balancecalories.core.food.FoodMath
import com.balancecalories.core.food.LoggedEntry
import com.balancecalories.core.food.Recipe
import com.balancecalories.core.food.RecipeMath
import com.balancecalories.core.food.RecentlyEaten
import com.balancecalories.core.food.Product
import androidx.room.withTransaction
import com.balancecalories.data.db.BalanceCaloriesDatabase
import com.balancecalories.data.db.entity.FoodEntryEntity
import com.balancecalories.data.mapper.toDomain
import java.time.LocalDate

/**
 * Logging what was eaten.
 *
 * Every entry is a **snapshot**: grams, scaled nutrition and the product's name are copied at log time
 * (ADR-005, schema v4). Editing or deleting a product tomorrow therefore cannot rewrite yesterday.
 */
class FoodEntryRepository(
    private val database: BalanceCaloriesDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val entries = database.foodEntryDao()
    private val products = database.productDao()

    /**
     * Logs [grams] of [product] and returns the stored entry.
     *
     * Refuses a product with no energy rather than writing a zero-calorie row: a wrong number with no
     * warning is worse than a refusal, and the UI offers manual entry instead.
     */
    suspend fun log(
        profileId: Long,
        product: Product,
        grams: Double,
        mealKey: String,
        on: LocalDate = LocalDate.now(),
    ): LoggedEntry {
        require(grams > 0.0) { "a portion must be positive, was $grams" }
        val portion = FoodMath.portion(product, grams)
        val kcal = portion.kcal
            ?: throw IllegalArgumentException("product ${product.id} has no energy to log")

        val entity = FoodEntryEntity(
            profileId = profileId,
            consumedAt = clock(),
            localDate = on.toString(),
            meal = mealKey,
            productId = product.id.takeIf { it != Product.NEW },
            productName = product.name,
            productNameRu = product.nameRu,
            grams = grams,
            kcal = kcal,
            proteinG = portion.proteinG,
            fatG = portion.fatG,
            carbsG = portion.carbsG,
            fiberG = portion.fiberG,
            sugarG = portion.sugarG,
            saltG = portion.saltG,
            wasEstimated = product.energyEstimated,
        )
        val id = entries.insert(entity)
        return entity.copy(id = id).toDomain()
    }

    /**
     * Logs [grams] of a finished [recipe] — the plate as served.
     *
     * The dish's energy is worked out here, once, from the same [RecipeMath] the recipe screen previews with, so
     * the preview and the stored row cannot drift apart. The entry keeps a snapshot of the derived numbers
     * (ADR-005) and only a link to the recipe: correcting an ingredient tomorrow must not rewrite today's
     * lunch, and deleting the recipe must not delete what was eaten.
     *
     * [wasEstimated] is true because recipe nutrition is always derived from other products' labels, never
     * measured on the dish itself.
     */
    suspend fun logRecipe(
        profileId: Long,
        recipe: Recipe,
        grams: Double,
        mealKey: String,
        on: LocalDate = LocalDate.now(),
    ): LoggedEntry {
        require(grams > 0.0) { "a portion must be positive, was $grams" }
        val totals = RecipeMath.totals(recipe.items)
        val basis = RecipeMath.weightBasis(totals, recipe.cookedWeightG)
        val portion = RecipeMath.portion(totals, basis, grams)
            ?: throw IllegalArgumentException("recipe ${recipe.id} has no weight to log")
        val kcal = portion.kcal
            ?: throw IllegalArgumentException("recipe ${recipe.id} has no energy to log")

        val entity = FoodEntryEntity(
            profileId = profileId,
            consumedAt = clock(),
            localDate = on.toString(),
            meal = mealKey,
            productId = null,
            productName = recipe.name,
            productNameRu = null,
            recipeId = recipe.id.takeIf { it != Recipe.NEW },
            grams = grams,
            kcal = kcal,
            proteinG = portion.proteinG,
            fatG = portion.fatG,
            carbsG = portion.carbsG,
            fiberG = portion.fiberG,
            sugarG = portion.sugarG,
            saltG = portion.saltG,
            wasEstimated = true,
        )
        val id = entries.insert(entity)
        return entity.copy(id = id).toDomain()
    }

    suspend fun day(profileId: Long, on: LocalDate, targetKcal: Int): DailyIntake {
        val rows = entries.forDay(profileId, on.toString())
        return DailyIntake(
            eatenKcal = rows.sumOf { it.kcal },
            targetKcal = targetKcal,
            meals = rows.map { it.toDomain() },
        )
    }

    suspend fun eatenKcal(profileId: Long, on: LocalDate): Double =
        entries.kcalForDay(profileId, on.toString())

    /**
     * What this profile has eaten, most recent first — the shortcut for the second time a food is logged.
     *
     * Rows whose product row has disappeared are dropped rather than shown: a recents list exists to be
     * tapped, and there is nothing left to log. Nothing is inferred here; an empty log returns an empty list.
     */
    suspend fun recentlyEaten(profileId: Long, limit: Int = 8): List<RecentlyEaten> =
        entries.recentlyEaten(profileId, limit).mapNotNull { usage ->
            val product = products.byId(usage.productId)?.toDomain() ?: return@mapNotNull null
            RecentlyEaten(product = product, times = usage.times, lastEatenAt = usage.lastEatenAt)
        }

    suspend fun countForDay(profileId: Long, on: LocalDate): Int =
        entries.countForDay(profileId, on.toString())

    /**
     * Changes how much was eaten, scaling the entry's own snapshot.
     *
     * The stored per-portion numbers are rescaled by the ratio of the two weights rather than re-read from
     * the product: the entry is a snapshot (ADR-005/016), so editing it must stay inside that snapshot. If
     * the product was corrected afterwards, the old entry keeps the numbers it was logged with — the user
     * can delete and re-log if they want the new ones.
     */
    suspend fun updateGrams(entryId: Long, grams: Double): LoggedEntry {
        require(grams > 0.0) { "a portion must be positive, was $grams" }
        val current = entries.byId(entryId) ?: throw IllegalArgumentException("no entry $entryId")
        val ratio = if (current.grams > 0.0) grams / current.grams else 1.0
        val updated = current.copy(
            grams = grams,
            kcal = current.kcal * ratio,
            proteinG = current.proteinG?.times(ratio),
            fatG = current.fatG?.times(ratio),
            carbsG = current.carbsG?.times(ratio),
            fiberG = current.fiberG?.times(ratio),
            sugarG = current.sugarG?.times(ratio),
            saltG = current.saltG?.times(ratio),
        )
        entries.update(updated)
        return updated.toDomain()
    }

    /**
     * Copies everything visible from one day to another, keeping the snapshot.
     *
     * Yesterday's breakfast is today's breakfast. The copies are new rows with a fresh `consumed_at`, so
     * the original day stays untouched and either can be edited or deleted independently.
     */
    suspend fun copyDay(profileId: Long, from: LocalDate, to: LocalDate): Int {
        val source = entries.forDay(profileId, from.toString())
        if (source.isEmpty()) return 0
        val now = clock()
        var copied = 0
        database.withTransaction {
            for (row in source) {
                entries.insert(
                    row.copy(
                        id = 0L,
                        profileId = profileId,
                        consumedAt = now,
                        localDate = to.toString(),
                        deletedAt = null,
                    ),
                )
                copied++
            }
        }
        return copied
    }

    /** Soft delete: the row stays, so "what did I eat last Tuesday" stays answerable. */
    suspend fun delete(entryId: Long) = entries.softDelete(entryId, clock())

    private fun FoodEntryEntity.toDomain(): LoggedEntry = LoggedEntry(
        id = id,
        productId = productId,
        name = productName ?: "",
        nameRu = productNameRu,
        mealKey = meal,
        grams = grams,
        kcal = kcal,
        proteinG = proteinG,
        fatG = fatG,
        carbsG = carbsG,
        wasEstimated = wasEstimated,
    )
}
