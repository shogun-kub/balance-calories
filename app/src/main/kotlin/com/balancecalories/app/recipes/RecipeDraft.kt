package com.balancecalories.app.recipes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import com.balancecalories.core.food.Recipe

/**
 * The recipe being written, hoisted out of the editor.
 *
 * Three things the owner asked for on 2026-11-13 forced this shape:
 *
 * 1. **The scanner is a screen of its own.** A separate destination means the editor leaves composition, and a draft
 *    that lives inside it would be thrown away by a scan - on the fourth ingredient, which is when a person least
 *    wants to start again.
 * 2. **Rotation must not lose it either.** An `Activity` recreation disposes the whole composition, so the draft has
 *    to be `rememberSaveable`, which is why [Saver] exists and why it stores primitive strings only.
 * 3. **Only an explicit cancel clears it.** Nothing here is discarded by navigation, by opening the scanner, or by
 *    turning the phone; MainActivity clears it when the person presses «Отмена» or when a save succeeds.
 *
 * Ingredients are stored as `productId` and the grams **as typed**, never as a `Product` copy: the product is the
 * source of truth and is re-read from the database after a restore, so an ingredient corrected this morning is
 * corrected inside a draft written yesterday.
 */
class RecipeDraft {

    /** The recipe being edited, or null for a new one. */
    var recipeId by mutableStateOf<Long?>(null)

    var name by mutableStateOf("")
    var servings by mutableStateOf("1")
    var cookedWeight by mutableStateOf("")
    var query by mutableStateOf("")

    /** A scanned code that resolved to nothing, shown until the person does something about it. */
    var scanMiss by mutableStateOf<String?>(null)

    /** Ingredients in order: the product id and the grams as typed. */
    val items = mutableStateListOf<DraftIngredient>()

    fun clear() {
        recipeId = null
        name = ""
        servings = "1"
        cookedWeight = ""
        query = ""
        scanMiss = null
        items.clear()
    }

    /** Fills the draft from a stored recipe, for "Изменить" rather than "Новый рецепт". */
    fun seedFrom(recipe: Recipe) {
        clear()
        recipeId = recipe.id
        name = recipe.name
        servings = trim(recipe.servings)
        cookedWeight = recipe.cookedWeightG?.let { trim(it) } ?: ""
        recipe.items.forEach { items.add(DraftIngredient(it.product.id, trim(it.grams))) }
    }

    companion object {

        private const val VERSION = "v1"
        private const val ITEM = "i"

        private fun trim(value: Double): String =
            if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

        /**
         * One flat list of strings, because that is what `rememberSaveable` can put in a saved instance state without
         * a parcelable class of our own. Item entries carry an `i` prefix so a grams string containing a colon could
         * never be mistaken for a header field.
         */
        val Saver = listSaver<RecipeDraft, String>(
            save = { draft ->
                buildList {
                    add(VERSION)
                    add(draft.recipeId?.toString().orEmpty())
                    add(draft.name)
                    add(draft.servings)
                    add(draft.cookedWeight)
                    add(draft.query)
                    add(draft.scanMiss.orEmpty())
                    draft.items.forEach { add("$ITEM:${it.productId}:${it.grams}") }
                }
            },
            restore = { saved ->
                RecipeDraft().also { draft ->
                    if (saved.firstOrNull() != VERSION) return@also
                    draft.recipeId = saved.getOrNull(1)?.takeIf { it.isNotEmpty() }?.toLongOrNull()
                    draft.name = saved.getOrNull(2).orEmpty()
                    draft.servings = saved.getOrNull(3).orEmpty().ifEmpty { "1" }
                    draft.cookedWeight = saved.getOrNull(4).orEmpty()
                    draft.query = saved.getOrNull(5).orEmpty()
                    draft.scanMiss = saved.getOrNull(6)?.takeIf { it.isNotEmpty() }
                    saved.drop(7).forEach { entry ->
                        val parts = entry.split(':')
                        if (parts.size == 3 && parts[0] == ITEM) {
                            val id = parts[1].toLongOrNull() ?: return@forEach
                            draft.items.add(DraftIngredient(id, parts[2]))
                        }
                    }
                }
            },
        )
    }
}

/** One ingredient of the draft: which product, and how much of it, exactly as typed. */
data class DraftIngredient(val productId: Long, val grams: String)
