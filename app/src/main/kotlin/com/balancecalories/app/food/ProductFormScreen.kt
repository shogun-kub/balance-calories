package com.balancecalories.app.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.balancecalories.app.AppContainer
import com.balancecalories.app.R
import com.balancecalories.app.ui.NumberField
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.core.food.MacroEnergy
import com.balancecalories.core.food.Product
import com.balancecalories.core.food.ProductSource
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A product the user types in, or a correction to a scanned one.
 *
 * Values are per 100 g because that is what the app stores and what every label states. A correction is
 * saved as an override, so a later scan of the same barcode cannot undo it.
 */
@Composable
fun ProductFormScreen(
    container: AppContainer,
    existing: Product?,
    initialBarcode: String? = null,
    onSaved: (Product) -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var nameRu by remember { mutableStateOf(existing?.nameRu.orEmpty()) }
    var kcal by remember { mutableStateOf(existing?.kcal100g?.let { trimNumber(it) }.orEmpty()) }
    var protein by remember { mutableStateOf(existing?.protein100g?.let { trimNumber(it) }.orEmpty()) }
    var fat by remember { mutableStateOf(existing?.fat100g?.let { trimNumber(it) }.orEmpty()) }
    var carbs by remember { mutableStateOf(existing?.carbs100g?.let { trimNumber(it) }.orEmpty()) }
    var liquid by remember { mutableStateOf(existing?.isLiquid ?: false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf(false) }

    // The energy of a food is 4/9/4 of its macros, so typing the macros and no kcal is a complete answer, not a
    // missing one. It comes from the same `MacroEnergy` the charts split bars with, so the number the form predicts
    // and the number the gauge later draws cannot come from two different sets of factors. A typed kcal always
    // wins: it is what the label states, and that is a measurement where this is an estimate (hard rule 4).
    val typedKcal = kcal.replace(',', '.').toDoubleOrNull()
    val proteinValue = protein.replace(',', '.').toDoubleOrNull()
    val fatValue = fat.replace(',', '.').toDoubleOrNull()
    val carbsValue = carbs.replace(',', '.').toDoubleOrNull()
    val derivedKcal = if (proteinValue == null && fatValue == null && carbsValue == null) {
        null
    } else {
        MacroEnergy.atwaterKcal(proteinValue ?: 0.0, fatValue ?: 0.0, carbsValue ?: 0.0).takeIf { it > 0.0 }
    }
    val effectiveKcal = typedKcal ?: derivedKcal

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WrappingRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        }

        Text(
            text = stringResource(
                if (existing == null) R.string.form_product_title else R.string.form_product_edit_title,
            ),
            style = MaterialTheme.typography.titleLarge,
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.field_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = nameRu,
            onValueChange = { nameRu = it },
            label = { Text(stringResource(R.string.field_name_ru)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        NumberField(R.string.field_kcal_100g, kcal) { kcal = it }
        if (typedKcal == null && derivedKcal != null) {
            Text(
                text = stringResource(R.string.form_kcal_from_macros, derivedKcal.roundToInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        NumberField(R.string.field_protein_100g, protein) { protein = it }
        NumberField(R.string.field_fat_100g, fat) { fat = it }
        NumberField(R.string.field_carbs_100g, carbs) { carbs = it }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Switch(checked = liquid, onCheckedChange = { liquid = it })
            Text(text = stringResource(R.string.field_liquid), style = MaterialTheme.typography.bodyMedium)
        }

        error?.let { Text(text = stringResource(it), color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = {
                error = when {
                    name.isBlank() -> R.string.error_name_required
                    effectiveKcal == null -> R.string.error_energy_or_macros
                    effectiveKcal < 0.0 || effectiveKcal > 900.0 -> R.string.error_kcal_range
                    else -> null
                }
                if (error != null) return@Button

                saving = true
                scope.launch {
                    val saved = container.products.saveUserProduct(
                        Product(
                            id = existing?.id ?: Product.NEW,
                            barcode = existing?.barcode ?: initialBarcode,
                            name = name.trim(),
                            nameRu = nameRu.trim().ifBlank { null },
                            brand = existing?.brand,
                            source = existing?.source ?: ProductSource.USER,
                            kcal100g = effectiveKcal,
                            protein100g = proteinValue,
                            fat100g = fatValue,
                            carbs100g = carbsValue,
                            fiber100g = existing?.fiber100g,
                            sugar100g = existing?.sugar100g,
                            salt100g = existing?.salt100g,
                            isLiquid = liquid,
                            servingGrams = existing?.servingGrams,
                            packGrams = existing?.packGrams,
                            imageUrl = existing?.imageUrl,
                            isOverride = existing?.isOverride ?: false,
                            // Typed energy is what the label says; computed energy is Atwater and says so everywhere
                            // the product is shown.
                            energyEstimated = typedKcal == null,
                        ),
                    )
                    onSaved(saved)
                }
            },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_save))
        }
    }
}

/** 539.0 → "539", 0.107 → "0.107": no trailing zeros in a field the user may edit. */