package com.balancecalories.data.repo

import androidx.room.withTransaction
import com.balancecalories.core.food.Recipe
import com.balancecalories.core.food.RecipeItem
import com.balancecalories.core.food.RecipeMath
import com.balancecalories.data.db.BalanceCaloriesDatabase
import com.balancecalories.data.db.entity.RecipeEntity
import com.balancecalories.data.db.entity.RecipeItemEntity
import com.balancecalories.data.mapper.toDomain

/**
 * Recipes: a named list of raw ingredients plus the weight it had after cooking.
 *
 * Two deliberate properties:
 *
 * - **Components are live references.** Re-reading the ingredients' products at load time means correcting a
 *   product's label also corrects every recipe that uses it — which is what a person expects when they fix a
 *   number. What must *not* change retroactively is a meal already logged, and that is protected by the food
 *   entry snapshot (ADR-005), not here.
 * - **Saving replaces the ingredient list wholesale.** Deleting and re-inserting inside one transaction keeps
 *   positions dense and ordered, and avoids the class of bug where an edited item silently keeps a stale slot.
 */
class RecipeRepository(
    private val database: BalanceCaloriesDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val recipes = database.recipeDao()
    private val products = database.productDao()

    /** Recipes for one profile, most recently edited first. */
    suspend fun list(profileId: Long, limit: Int = 200): List<Recipe> =
        recipes.forProfile(profileId).take(limit).mapNotNull { entity -> byId(entity.id) }

    suspend fun byId(id: Long): Recipe? {
        val entity = recipes.byId(id) ?: return null
        val items = recipes.itemsFor(id).mapNotNull { item ->
            val product = products.byId(item.productId)?.toDomain() ?: return@mapNotNull null
            RecipeItem(product = product, grams = item.grams)
        }
        return entity.toDomain(items)
    }

    /**
     * Stores a new recipe or replaces an existing one.
     *
     * Returns the stored recipe with its real id, so the caller can navigate to it without re-reading.
     */
    suspend fun save(recipe: Recipe): Recipe {
        require(recipe.name.isNotBlank()) { "a recipe needs a name" }
        require(recipe.servings > 0.0) { "a recipe needs at least a serving" }
        recipe.cookedWeightG?.let { require(it > 0.0) { "a cooked weight must be positive, was $it" } }
        require(recipe.items.isNotEmpty()) { "a recipe needs at least one ingredient" }

        val now = clock()
        return database.withTransaction {
            val existing = recipe.id.takeIf { it != Recipe.NEW }?.let { recipes.byId(it) }
            val entity = RecipeEntity(
                id = existing?.id ?: 0,
                profileId = recipe.profileId,
                name = recipe.name.trim(),
                cookedWeightG = recipe.cookedWeightG,
                servings = recipe.servings,
                notes = recipe.notes,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
            val recipeId = if (existing == null) {
                recipes.insert(entity)
            } else {
                recipes.update(entity)
                existing.id
            }

            recipes.deleteItems(recipeId)
            recipes.insertItems(
                recipe.items.mapIndexed { position, item ->
                    require(item.product.id != com.balancecalories.core.food.Product.NEW) {
                        "an ingredient must be a stored product"
                    }
                    RecipeItemEntity(
                        recipeId = recipeId,
                        productId = item.product.id,
                        grams = item.grams,
                        position = position,
                    )
                },
            )

            recipe.copy(id = recipeId, name = entity.name, items = recipe.items)
        }
    }

    /**
     * Deletes a recipe.
     *
     * Its ingredients go with it (cascade), and any food entry that pointed at it keeps its own numbers and
     * only loses the link — history is not rewritten by a deletion (ADR-005/016).
     */
    suspend fun delete(id: Long) = recipes.delete(id)

    /** A recipe's dish totals, per-100 g and weight basis in one call, so screens cannot disagree. */
    data class Derived(
        val totals: RecipeMath.Totals,
        val basis: RecipeMath.WeightBasis,
        val per100g: com.balancecalories.core.food.PortionNutrition?,
    )

    fun derive(recipe: Recipe): Derived {
        val totals = RecipeMath.totals(recipe.items)
        val basis = RecipeMath.weightBasis(totals, recipe.cookedWeightG)
        return Derived(totals = totals, basis = basis, per100g = RecipeMath.per100g(totals, basis))
    }
}

/** Database row → domain, with the ingredients already resolved. */
fun RecipeEntity.toDomain(items: List<RecipeItem>): Recipe = Recipe(
    id = id,
    profileId = profileId,
    name = name,
    cookedWeightG = cookedWeightG,
    servings = servings,
    notes = notes,
    items = items,
)
