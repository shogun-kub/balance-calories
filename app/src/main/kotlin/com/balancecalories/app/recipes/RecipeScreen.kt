package com.balancecalories.app.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.balancecalories.app.food.MealChips
import com.balancecalories.app.ui.WrappingRow
import java.time.LocalTime
import com.balancecalories.app.AppContainer
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.app.R
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.app.food.trimNumber
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.core.food.MealSlot
import com.balancecalories.core.food.guessMealKey
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.core.food.Recipe
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.core.food.RecipeMath
import kotlinx.coroutines.launch

/**
 * One dish, with the numbers a portion is served from.
 *
 * Two warnings are part of the screen, not decoration: the dish total is unknown when an ingredient has no
 * calorie data, and per-100 g is computed on the raw weight when the cooked weight was never measured. Both
 * change how much a portion really is, so hiding them would make the number look more certain than it is.
 */
@Composable
fun RecipeScreen(
    container: AppContainer,
    profileId: Long,
    recipe: Recipe,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onLogged: () -> Unit,
    onDeleted: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val totals = RecipeMath.totals(recipe.items)
    val basis = RecipeMath.weightBasis(totals, recipe.cookedWeightG)
    val per100 = RecipeMath.per100g(totals, basis)
    val perServing = RecipeMath.perServing(totals, basis, recipe.servings)
    val languageTag = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]?.toLanguageTag()

    var portion by remember(recipe.id) {
        mutableStateOf(perServing?.grams?.let { trimNumber(it) } ?: "100")
    }
    var slots by remember(profileId) { mutableStateOf<List<MealSlot>>(emptyList()) }
    var mealKey by rememberSaveable(profileId) { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    // The picker is this profile's slots, not a constant list (schema v7).
    LaunchedEffect(profileId) {
        val loaded = container.meals.slots(profileId)
        slots = loaded
        if (mealKey == null) {
            val now = LocalTime.now()
            mealKey = guessMealKey(loaded, now.hour * 60 + now.minute)
        }
    }

    val grams = portion.replace(',', '.').toDoubleOrNull()
    val preview = if (grams != null && grams > 0) RecipeMath.portion(totals, basis, grams) else null

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WrappingRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            TextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
        }

        Text(text = recipe.name, style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.recipe_raw_weight, trimNumber(totals.rawGrams)),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = stringResource(R.string.recipe_servings) + ": " + trimNumber(recipe.servings),
            style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider()
        Text(text = stringResource(R.string.recipe_ingredients), style = MaterialTheme.typography.titleSmall)
        for (item in recipe.items) {
            WrappingRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = item.product.displayName(languageTag), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = stringResource(R.string.recipe_grams_value, trimNumber(item.grams)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        HorizontalDivider()
        Text(text = stringResource(R.string.recipe_dish), style = MaterialTheme.typography.titleSmall)
        val dishKcal = totals.kcal
        if (dishKcal != null) {
            Text(
                text = stringResource(R.string.recipe_kcal_total, dishKcal),
                style = MaterialTheme.typography.bodyMedium,
            )
            MacroLine(label = stringResource(R.string.recipe_protein), value = totals.proteinG)
            MacroLine(label = stringResource(R.string.recipe_fat), value = totals.fatG)
            MacroLine(label = stringResource(R.string.recipe_carbs), value = totals.carbsG)
        } else {
            Text(
                text = stringResource(R.string.recipe_missing_energy, totals.missingEnergyItems),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (per100 != null) {
            Text(text = stringResource(R.string.recipe_per_100g), style = MaterialTheme.typography.titleSmall)
            MacroLine(label = stringResource(R.string.recipe_kcal), value = per100.kcal)
            if (perServing != null) {
                Text(text = stringResource(R.string.recipe_per_serving), style = MaterialTheme.typography.titleSmall)
                MacroLine(label = stringResource(R.string.recipe_kcal), value = perServing.kcal)
            }
        }

        if (basis.usedRawWeight) {
            Text(
                text = stringResource(R.string.recipe_basis_raw),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }

        HorizontalDivider()
        OutlinedTextField(
            value = portion,
            onValueChange = { portion = it },
            label = { Text(stringResource(R.string.product_portion)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = preview?.kcal?.let { stringResource(R.string.product_portion_kcal, it) }
                ?: stringResource(R.string.product_no_energy),
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(text = stringResource(R.string.product_meal), style = MaterialTheme.typography.titleSmall)
        MealChips(slots = slots, selectedKey = mealKey, onSelect = { mealKey = it })

        error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = {
                val value = grams ?: return@Button
                saving = true
                error = null
                scope.launch {
                    runCatching {
                        container.food.logRecipe(profileId, recipe, value, mealKey ?: return@runCatching)
                    }
                        .onSuccess { onLogged() }
                        .onFailure {
                            error = it.message
                            saving = false
                        }
                }
            },
            enabled = !saving && totals.hasEnergy && (grams ?: 0.0) > 0.0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_log))
        }

        TextButton(onClick = { confirmingDelete = true }) {
            Text(stringResource(R.string.action_delete), color = Color.Unspecified)
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.action_delete)) },
            text = { Text(stringResource(R.string.recipe_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        scope.launch {
                            container.recipes.delete(recipe.id)
                            onDeleted()
                        }
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun MacroLine(label: String, value: Double?) {
    WrappingRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Text(
            text = value?.let { "%.1f".format(it) } ?: stringResource(R.string.debug_none),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

