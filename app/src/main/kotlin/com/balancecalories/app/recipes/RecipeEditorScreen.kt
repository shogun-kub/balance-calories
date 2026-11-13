package com.balancecalories.app.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.balancecalories.app.AppContainer
import com.balancecalories.app.R
import com.balancecalories.app.food.MIN_REMOTE_QUERY
import com.balancecalories.app.food.REMOTE_DEBOUNCE_MS
import com.balancecalories.app.food.RemoteState
import com.balancecalories.app.food.searchOpenFoodFacts
import com.balancecalories.app.food.trimNumber
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.core.food.Product
import com.balancecalories.core.food.Recipe
import com.balancecalories.core.food.RecipeItem
import com.balancecalories.core.food.RecipeMath
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A recipe being written, rendered from [draft].
 *
 * Everything that must outlive this screen - the name, the numbers, the ingredients as typed - lives in the draft,
 * which MainActivity owns and saves, because this screen is now **left** on purpose: the scanner is its own
 * destination (owner's request, 2026-11-13), and a rotation disposes the composition as well. What stays local here
 * is only what is true for one visit: the error message, the saving flag, the search results.
 *
 * Ingredients are `productId` + grams in the draft, so the products are read back from the database here. That is
 * also why an ingredient corrected today is already corrected in a draft written yesterday.
 */
@Composable
fun RecipeEditorScreen(
    container: AppContainer,
    profileId: Long,
    draft: RecipeDraft,
    onScan: () -> Unit,
    onSaved: (Recipe) -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val languageTag = LocalConfiguration.current.locales[0]?.toLanguageTag()

    var products by remember { mutableStateOf<Map<Long, Product>>(emptyMap()) }
    var found by remember { mutableStateOf<List<Product>>(emptyList()) }
    var remote by remember { mutableStateOf<RemoteState>(RemoteState.Idle) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    // The draft keeps ids, so the names and the numbers come from the database. Keyed on the ids, not on the list:
    // typing grams must not re-read the whole catalogue on every keystroke.
    LaunchedEffect(draft.items.map { it.productId }) {
        val missing = draft.items.map { it.productId }.distinct().filter { it !in products }
        if (missing.isEmpty()) return@LaunchedEffect
        val loaded = missing.mapNotNull { id -> runCatching { container.products.byId(id) }.getOrNull() }
        if (loaded.isNotEmpty()) products = products + loaded.associateBy { it.id }
    }

    LaunchedEffect(draft.query) {
        val asked = draft.query.trim()
        found = if (asked.length < 2) {
            emptyList()
        } else {
            runCatching { container.products.search(asked, limit = 6) }.getOrDefault(emptyList())
        }
        remote = RemoteState.Idle
        // The same automatic fallback as the products list: an ingredient the phone does not know is looked up in
        // Open Food Facts without a second tap, after the typing settles.
        if (asked.length >= MIN_REMOTE_QUERY && found.isEmpty()) {
            delay(REMOTE_DEBOUNCE_MS)
            if (draft.query.trim() == asked) {
                remote = RemoteState.Searching
                remote = searchOpenFoodFacts(container, asked)
            }
        }
    }

    val parsedItems = draft.items.mapNotNull { row ->
        val product = products[row.productId] ?: return@mapNotNull null
        val grams = row.grams.replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
        if (grams <= 0.0) return@mapNotNull null
        RecipeItem(product = product, grams = grams)
    }
    val totals = RecipeMath.totals(parsedItems)
    val cooked = draft.cookedWeight.replace(',', '.').toDoubleOrNull()
    val basis = RecipeMath.weightBasis(totals, cooked)
    val per100 = RecipeMath.per100g(totals, basis)
    val servingsValue = draft.servings.replace(',', '.').toDoubleOrNull() ?: 0.0
    val perServing = RecipeMath.perServing(totals, basis, servingsValue)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WrappingRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            Text(
                text = stringResource(if (draft.recipeId == null) R.string.recipe_new else R.string.action_edit),
                style = MaterialTheme.typography.titleLarge,
            )
        }

        OutlinedTextField(
            value = draft.name,
            onValueChange = { draft.name = it },
            label = { Text(stringResource(R.string.field_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft.servings,
                onValueChange = { draft.servings = it },
                label = { Text(stringResource(R.string.recipe_servings)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = draft.cookedWeight,
                onValueChange = { draft.cookedWeight = it },
                label = { Text(stringResource(R.string.recipe_cooked_weight)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = stringResource(R.string.recipe_cooked_help),
            style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider()
        Text(text = stringResource(R.string.recipe_ingredients), style = MaterialTheme.typography.titleSmall)

        for ((index, row) in draft.items.withIndex()) {
            val product = products[row.productId]
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = product?.displayName(languageTag) ?: stringResource(R.string.recipe_loading_ingredient),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = row.grams,
                    onValueChange = { draft.items[index] = row.copy(grams = it) },
                    label = { Text(stringResource(R.string.recipe_ingredient_grams)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { draft.items.removeAt(index) }) {
                    Text(stringResource(R.string.action_delete))
                }
            }
        }

        OutlinedTextField(
            value = draft.query,
            onValueChange = { draft.query = it },
            label = { Text(stringResource(R.string.recipe_search_products)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        WrappingRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Its own screen, as the owner asked: the draft is safe because it does not live here.
            TextButton(onClick = onScan) { Text(stringResource(R.string.action_scan)) }
            if (draft.query.trim().length >= MIN_REMOTE_QUERY && (found.isNotEmpty() || remote is RemoteState.Failed)) {
                TextButton(
                    enabled = remote !is RemoteState.Searching,
                    onClick = {
                        val asked = draft.query.trim()
                        remote = RemoteState.Searching
                        scope.launch { remote = searchOpenFoodFacts(container, asked) }
                    },
                ) {
                    Text(
                        text = if (remote is RemoteState.Searching) {
                            stringResource(R.string.food_search_off_busy)
                        } else {
                            stringResource(R.string.food_search_off)
                        },
                    )
                }
            }
        }

        if (draft.query.trim().length >= 2 && found.isEmpty() && remote !is RemoteState.Found) {
            Text(text = stringResource(R.string.recipe_no_products), style = MaterialTheme.typography.bodySmall)
        }

        draft.scanMiss?.let { code ->
            Text(
                text = stringResource(R.string.recipe_scan_not_found, code),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        for (product in found) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    // A hundred grams is the default because it is one round number to correct, not a guess at
                    // the recipe; the field is right there and stays editable.
                    draft.items.add(DraftIngredient(product.id, "100"))
                    draft.query = ""
                    error = null
                }.padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = product.displayName(languageTag),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.recipe_add_ingredient),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        when (val state = remote) {
            RemoteState.Idle, RemoteState.Searching -> Unit

            is RemoteState.Failed -> Text(
                text = stringResource(R.string.food_search_off_failed, state.reason),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )

            is RemoteState.Found -> {
                val hits = state.search.products
                if (hits.isEmpty()) {
                    Text(
                        text = stringResource(R.string.food_search_off_nothing),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.food_search_off_count, state.search.totalCount),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    for (hit in hits) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                scope.launch {
                                    val stored = runCatching { container.products.importSearchHit(hit) }.getOrNull()
                                    if (stored != null) {
                                        draft.items.add(DraftIngredient(stored.id, "100"))
                                        draft.query = ""
                                        remote = RemoteState.Idle
                                    }
                                }
                            }.padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = hit.name ?: hit.code,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = stringResource(R.string.recipe_add_ingredient),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider()
        Text(text = stringResource(R.string.recipe_dish), style = MaterialTheme.typography.titleSmall)
        Text(
            text = stringResource(R.string.recipe_raw_weight, trimNumber(totals.rawGrams)),
            style = MaterialTheme.typography.bodySmall,
        )
        val dishKcal = totals.kcal
        if (dishKcal != null) {
            Text(
                text = stringResource(R.string.recipe_kcal_total, dishKcal),
                style = MaterialTheme.typography.bodyMedium,
            )
            per100?.kcal?.let {
                Text(
                    text = stringResource(R.string.recipe_per_100g_kcal, it),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            perServing?.kcal?.let {
                Text(
                    text = stringResource(R.string.recipe_portion_kcal, it),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else if (parsedItems.isNotEmpty()) {
            // Only with ingredients in the list: an empty draft has no unknown ingredients, it has none at all.
            Text(
                text = stringResource(R.string.recipe_missing_energy, totals.missingEnergyItems),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (basis.usedRawWeight && parsedItems.isNotEmpty()) {
            Text(text = stringResource(R.string.recipe_basis_raw), style = MaterialTheme.typography.bodySmall)
        }

        error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = {
                if (draft.name.isBlank() || parsedItems.isEmpty()) {
                    error = context.getString(R.string.recipe_incomplete)
                    return@Button
                }
                saving = true
                error = null
                scope.launch {
                    runCatching {
                        container.recipes.save(
                            Recipe(
                                id = draft.recipeId ?: Recipe.NEW,
                                profileId = profileId,
                                name = draft.name.trim(),
                                cookedWeightG = cooked,
                                servings = if (servingsValue > 0.0) servingsValue else 1.0,
                                notes = null,
                                items = parsedItems,
                            ),
                        )
                    }.onSuccess { onSaved(it) }.onFailure {
                        // A database refusal is a message, not a crash: the draft is still here and the person
                        // can try again or copy the numbers out.
                        error = context.getString(R.string.recipe_save_failed, it.message.orEmpty())
                        saving = false
                    }
                }
            },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_save))
        }
    }
}
