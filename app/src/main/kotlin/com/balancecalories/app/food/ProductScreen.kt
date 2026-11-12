package com.balancecalories.app.food

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.app.AppContainer
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.app.R
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.app.today.localized
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.core.food.FoodMath
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.core.food.Meal
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.core.food.Product
import kotlinx.coroutines.launch

/**
 * One product, one portion, one decision.
 *
 * The portion preview is computed with `:core`'s [FoodMath] — the same function that writes the entry — so
 * the number shown and the number stored cannot disagree. An estimated energy is labelled here, before the
 * user commits, rather than after in a history list nobody re-reads.
 */
@Composable
fun ProductScreen(
    product: Product,
    container: AppContainer,
    profileId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onLogged: () -> Unit,
    initialMeal: Meal? = null,
) {
    val scope = rememberCoroutineScope()
    val languageTag = LocalConfiguration.current.locales[0]?.toLanguageTag()
    var portion by remember(product.id) { mutableStateOf("100") }
    var meal by rememberSaveable { mutableStateOf(initialMeal ?: defaultMeal()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }

    val grams = portion.replace(',', '.').toDoubleOrNull()
    val preview = if (grams != null && grams > 0) FoodMath.portion(product, grams) else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WrappingRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            // Correcting a crowd-sourced product is the point of this button: the numbers on the pack win,
            // and `saveUserProduct` marks the row as an override so a later scan cannot undo the fix.
            TextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
        }

        Text(text = product.displayName(languageTag), style = MaterialTheme.typography.titleLarge)
        WrappingRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = product.source.localized(), style = MaterialTheme.typography.bodySmall)
            if (product.isOverride) {
                Text(text = stringResource(R.string.product_override), style = MaterialTheme.typography.bodySmall)
            }
            if (product.energyEstimated) {
                Text(
                    text = stringResource(R.string.product_estimated),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }

        HorizontalDivider()
        Text(text = stringResource(R.string.product_per_100g), style = MaterialTheme.typography.titleSmall)
        PerHundredRow(stringResource(R.string.field_kcal_100g), product.kcal100g)
        PerHundredRow(stringResource(R.string.field_protein_100g), product.protein100g)
        PerHundredRow(stringResource(R.string.field_fat_100g), product.fat100g)
        PerHundredRow(stringResource(R.string.field_carbs_100g), product.carbs100g)

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
            text = if (preview?.kcal != null) {
                stringResource(R.string.product_portion_kcal, preview.kcal!!)
            } else {
                stringResource(R.string.product_no_energy)
            },
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(text = stringResource(R.string.product_meal), style = MaterialTheme.typography.titleSmall)
        WrappingRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (option in Meal.entries) {
                FilterChip(
                    selected = meal == option,
                    onClick = { meal = option },
                    label = { Text(option.localized()) },
                )
            }
        }

        error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = {
                val value = grams ?: return@Button
                saving = true
                error = null
                scope.launch {
                    runCatching {
                        container.food.log(profileId, product, value, meal)
                    }.onSuccess {
                        onLogged()
                    }.onFailure {
                        error = it.message
                        saving = false
                    }
                }
            },
            enabled = !saving && product.hasEnergy && (grams ?: 0.0) > 0.0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_log))
        }
    }
}

@Composable
private fun PerHundredRow(label: String, value: Double?) {
    WrappingRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Text(
            text = value?.let { "%.1f".format(it) } ?: stringResource(R.string.debug_none),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Rough default by the clock: breakfast before 11, lunch before 16, dinner before 21, else a snack. */
private fun defaultMeal(): Meal {
    val hour = java.time.LocalTime.now().hour
    return when {
        hour < 11 -> Meal.BREAKFAST
        hour < 16 -> Meal.LUNCH
        hour < 21 -> Meal.DINNER
        else -> Meal.SNACK
    }
}