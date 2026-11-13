package com.balancecalories.app.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.balancecalories.app.AppContainer
import com.balancecalories.app.R
import com.balancecalories.app.food.MIN_REMOTE_QUERY
import com.balancecalories.app.food.REMOTE_DEBOUNCE_MS
import com.balancecalories.app.food.RemoteState
import com.balancecalories.app.food.searchOpenFoodFacts
import com.balancecalories.app.scanner.ScannerScreen
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.app.food.trimNumber
import com.balancecalories.core.food.Product
import com.balancecalories.core.food.Recipe
import com.balancecalories.core.food.RecipeItem
import com.balancecalories.core.food.RecipeMath
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One ingredient while it is being edited: the product, and its grams as typed text. */
private data class EditableItem(val product: Product, var grams: String)

/**
 * Creating or editing a recipe.
 *
 * The grams live as text, not as numbers, because a half-typed "12" must not become a 12-gram ingredient in the
 * live preview: parsing happens for the preview and the save, and a row that does not parse is simply left out
 * of both. The preview uses the same [RecipeMath] the detail screen uses, so what is shown while editing is
 * exactly what will be stored.
 */
@Composable
fun RecipeEditorScreen(
    container: AppContainer,
    profileId: Long,
    existing: Recipe?,
    onSaved: (Recipe) -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val languageTag = LocalConfiguration.current.locales[0]?.toLanguageTag()

    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var servings by rememberSaveable { mutableStateOf(existing?.servings?.let { trimNumber(it) } ?: "1") }
    var cookedWeight by rememberSaveable { mutableStateOf(existing?.cookedWeightG?.let { trimNumber(it) }.orEmpty()) }
    var query by rememberSaveable { mutableStateOf("") }
    var found by remember { mutableStateOf<List<Product>>(emptyList()) }
    var remote by remember { mutableStateOf<RemoteState>(RemoteState.Idle) }
    // The scanner is drawn *inside* this screen rather than as an overlay, so a scan does not take the recipe being
    // written out of composition and throw it away.
    var scanning by remember { mutableStateOf(false) }
    var scanMiss by remember { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }

    val rows: SnapshotStateList<EditableItem> = remember {
        mutableStateListOf<EditableItem>().also { list ->
            existing?.items?.forEach { list.add(EditableItem(it.product, trimNumber(it.grams))) }
        }
    }

    LaunchedEffect(query) {
        val asked = query.trim()
        found = if (asked.length < 2) {
            emptyList()
        } else {
            runCatching { container.products.search(asked, limit = 6) }.getOrDefault(emptyList())
        }
        remote = RemoteState.Idle
        // The same automatic fallback as the products list (owner's note, 2026-11-13): an ingredient the phone does
        // not know is looked up in Open Food Facts without a second tap, after the typing settles.
        if (asked.length >= MIN_REMOTE_QUERY && found.isEmpty()) {
            delay(REMOTE_DEBOUNCE_MS)
            if (query.trim() == asked) {
                remote = RemoteState.Searching
                remote = searchOpenFoodFacts(container, asked)
            }
        }
    }

    val parsedItems = rows.mapNotNull { row ->
        val grams = row.grams.replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
        if (grams <= 0.0) return@mapNotNull null
        RecipeItem(product = row.product, grams = grams)
    }
    val totals = RecipeMath.totals(parsedItems)
    val cooked = cookedWeight.replace(',', '.').toDoubleOrNull()
    val basis = RecipeMath.weightBasis(totals, cooked)
    val per100 = RecipeMath.per100g(totals, basis)
    val servingsValue = servings.replace(',', '.').toDoubleOrNull() ?: 0.0
    val perServing = RecipeMath.perServing(totals, basis, servingsValue)

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WrappingRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            Text(
                text = stringResource(
                    if (existing == null) R.string.recipe_new else R.string.action_edit,
                ),
                style = MaterialTheme.typography.titleLarge,
            )
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.field_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = servings,
                onValueChange = { servings = it },
                label = { Text(stringResource(R.string.recipe_servings)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = cookedWeight,
                onValueChange = { cookedWeight = it },
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

        for ((index, row) in rows.withIndex()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = row.product.displayName(languageTag),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = row.grams,
                    onValueChange = { rows[index] = row.copy(grams = it) },
                    label = { Text(stringResource(R.string.recipe_ingredient_grams)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { rows.removeAt(index) }) {
                    Text(stringResource(R.string.action_delete))
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.recipe_search_products)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        WrappingRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { scanning = true }) { Text(stringResource(R.string.action_scan)) }
            if (query.trim().length >= MIN_REMOTE_QUERY && (found.isNotEmpty() || remote is RemoteState.Failed)) {
                TextButton(
                    enabled = remote !is RemoteState.Searching,
                    onClick = {
                        val asked = query.trim()
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

        if (query.trim().length >= 2 && found.isEmpty() && remote !is RemoteState.Found) {
            Text(text = stringResource(R.string.recipe_no_products), style = MaterialTheme.typography.bodySmall)
        }

        scanMiss?.let { code ->
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
                    rows.add(EditableItem(product, "100"))
                    query = ""
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
                                        rows.add(EditableItem(stored, "100"))
                                        query = ""
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

        error?.let { Text(text = stringResource(it), color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = {
                if (name.isBlank() || parsedItems.isEmpty()) {
                    error = R.string.recipe_incomplete
                    return@Button
                }
                saving = true
                error = null
                scope.launch {
                    runCatching {
                        container.recipes.save(
                            Recipe(
                                id = existing?.id ?: Recipe.NEW,
                                profileId = profileId,
                                name = name.trim(),
                                cookedWeightG = cooked,
                                servings = if (servingsValue > 0.0) servingsValue else 1.0,
                                notes = existing?.notes,
                                items = parsedItems,
                            ),
                        )
                    }.onSuccess { onSaved(it) }.onFailure {
                        error = null
                        saving = false
                        throw it
                    }
                }
            },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_save))
        }
    }

    if (scanning) {
        // Opaque, because the scanner shares a Box with the editor now instead of owning the screen: without a
        // surface behind it the recipe shows through the camera preview.
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            ScannerScreen(
                container = container,
                onBack = { scanning = false },
                onProduct = { product ->
                    scanning = false
                    scanMiss = null
                    rows.add(EditableItem(product, "100"))
                    query = ""
                },
                onAddManually = { code ->
                    // The code stays on screen instead of opening the product form: that form is a different
                    // destination, and leaving this one would throw away the recipe being written.
                    scanning = false
                    scanMiss = code
                },
            )
        }
    }
    }
}
