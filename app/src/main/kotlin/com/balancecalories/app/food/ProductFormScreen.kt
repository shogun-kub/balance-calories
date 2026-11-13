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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.balancecalories.core.food.Barcode
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
    /** A name to start from when creating: what the person typed into a search that then found nothing. */
    initialName: String? = null,
    onSaved: (Product) -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // A search that found nothing is an answer about the catalogue, not about the food: carrying the words
    // into this field saves retyping what is already on screen, which is the whole of the owner's ask.
    var name by rememberSaveable { mutableStateOf(existing?.name ?: initialName.orEmpty()) }
    var nameRu by rememberSaveable { mutableStateOf(existing?.nameRu.orEmpty()) }
    var kcal by rememberSaveable { mutableStateOf(existing?.kcal100g?.let { trimNumber(it) }.orEmpty()) }
    var protein by rememberSaveable { mutableStateOf(existing?.protein100g?.let { trimNumber(it) }.orEmpty()) }
    var fat by rememberSaveable { mutableStateOf(existing?.fat100g?.let { trimNumber(it) }.orEmpty()) }
    var carbs by rememberSaveable { mutableStateOf(existing?.carbs100g?.let { trimNumber(it) }.orEmpty()) }
    var barcode by rememberSaveable { mutableStateOf(existing?.barcode ?: initialBarcode.orEmpty()) }
    var liquid by rememberSaveable { mutableStateOf(existing?.isLiquid ?: false) }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }
    // Only one message takes a noun ("this barcode belongs to X"), so the argument rides beside the id.
    var errorArg by remember { mutableStateOf<String?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }

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
    // Spaces and dashes come off, so a code pasted from a pack or read aloud can be typed with them.
    val normalizedBarcode = Barcode.normalize(barcode)
    val checkDigitOk = normalizedBarcode?.let { Barcode.hasValidCheckDigit(it) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
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
        OutlinedTextField(
            value = barcode,
            onValueChange = { barcode = it },
            label = { Text(stringResource(R.string.field_barcode)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        // A store's internal code can be a legitimate identifier without a valid EAN, so a bad check digit is a
        // hint, not a refusal: the scanner stores those codes too (Barcode.hasValidCheckDigit).
        if (normalizedBarcode != null && checkDigitOk == false) {
            Text(
                text = stringResource(R.string.form_barcode_check_digit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
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

        error?.let { res ->
            val noun = errorArg
            Text(
                text = if (noun != null) stringResource(res, noun) else stringResource(res),
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = {
                error = when {
                    name.isBlank() -> R.string.error_name_required
                    barcode.isNotBlank() && normalizedBarcode == null -> R.string.error_barcode_invalid
                    effectiveKcal == null -> R.string.error_energy_or_macros
                    effectiveKcal < 0.0 || effectiveKcal > 900.0 -> R.string.error_kcal_range
                    else -> null
                }
                errorArg = null
                if (error != null) return@Button

                saving = true
                scope.launch {
                    // A barcode is unique in the table, and saving resolves one to an existing row on purpose, so
                    // a mistyped code would silently rewrite whichever product already owns it. Ask first.
                    val owner = normalizedBarcode?.let { container.products.barcodeOwner(it, existing?.id) }
                    if (owner != null) {
                        error = R.string.error_barcode_taken
                        errorArg = owner.nameRu ?: owner.name
                        saving = false
                        return@launch
                    }
                    val saved = container.products.saveUserProduct(
                        Product(
                            id = existing?.id ?: Product.NEW,
                            barcode = normalizedBarcode,
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