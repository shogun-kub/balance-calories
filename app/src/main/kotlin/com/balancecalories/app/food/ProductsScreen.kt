package com.balancecalories.app.food

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.balancecalories.app.AppContainer
import com.balancecalories.app.R
import com.balancecalories.core.food.Product
import com.balancecalories.core.food.ProductSource
import com.balancecalories.core.food.Barcode

/**
 * The food list: a search over everything already on the phone.
 *
 * No network happens here. The starter pack is local, and a scanned product is already stored, so the list
 * works in a basement with no signal (AGENTS.md, hard rule 11).
 */
@Composable
fun ProductsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onPick: (Product) -> Unit,
    onAddManual: () -> Unit,
    onScan: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Product>>(emptyList()) }
    val languageTag = LocalConfiguration.current.locales[0]?.toLanguageTag()

    LaunchedEffect(query) {
        results = if (query.isBlank()) {
            container.products.browse()
        } else {
            container.products.search(query)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            Text(text = stringResource(R.string.food_title), style = MaterialTheme.typography.titleLarge)
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.food_search_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (results.isEmpty()) {
            Text(text = stringResource(R.string.food_empty), style = MaterialTheme.typography.titleSmall)
            Text(text = stringResource(R.string.food_empty_note), style = MaterialTheme.typography.bodySmall)
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(results, key = { it.id }) { product ->
                ProductRow(product = product, languageTag = languageTag, onClick = { onPick(product) })
            }
        }

        TextButton(onClick = onScan) { Text(stringResource(R.string.action_scan)) }
        TextButton(onClick = onAddManual) { Text(stringResource(R.string.food_add_manual)) }
    }
}

@Composable
private fun ProductRow(product: Product, languageTag: String?, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Text(text = product.displayName(languageTag), style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.kcal_decimal, product.kcal100g ?: 0.0),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(text = product.source.localized(), style = MaterialTheme.typography.bodySmall)
            if (product.energyEstimated) {
                Text(
                    text = stringResource(R.string.product_estimated),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
fun ProductSource.localized(): String = stringResource(
    when (this) {
        ProductSource.STARTER -> R.string.food_source_starter
        ProductSource.OFF -> R.string.food_source_off
        ProductSource.USER -> R.string.food_source_user
    },
)
