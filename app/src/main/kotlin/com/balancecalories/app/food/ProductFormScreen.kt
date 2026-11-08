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
import com.balancecalories.core.food.Product
import com.balancecalories.core.food.ProductSource
import kotlinx.coroutines.launch

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                val kcalValue = kcal.replace(',', '.').toDoubleOrNull()
                error = when {
                    name.isBlank() -> R.string.error_name_required
                    kcalValue == null -> R.string.error_kcal_required
                    kcalValue < 0.0 || kcalValue > 900.0 -> R.string.error_kcal_range
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
                            kcal100g = kcalValue,
                            protein100g = protein.toDoubleOrNull(),
                            fat100g = fat.toDoubleOrNull(),
                            carbs100g = carbs.toDoubleOrNull(),
                            fiber100g = existing?.fiber100g,
                            sugar100g = existing?.sugar100g,
                            salt100g = existing?.salt100g,
                            isLiquid = liquid,
                            servingGrams = existing?.servingGrams,
                            packGrams = existing?.packGrams,
                            imageUrl = existing?.imageUrl,
                            isOverride = existing?.isOverride ?: false,
                            energyEstimated = false,
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

@Composable
private fun NumberField(labelRes: Int, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 539.0 → "539", 0.107 → "0.107": no trailing zeros in a field the user may edit. */
