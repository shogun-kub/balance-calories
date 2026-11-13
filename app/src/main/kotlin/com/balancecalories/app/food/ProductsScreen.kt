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
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.app.AppContainer
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.app.R
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.core.food.Product
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.core.food.ProductSource
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.core.food.RecentlyEaten
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.data.off.OffProduct
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.data.off.OffSearch
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The food list: a search over everything already on the phone, plus a deliberate search of
 * OpenFoodFacts.
 *
 * The local list is never blocked by the network (AGENTS.md, hard rule 11): it answers on every keystroke
 * from the starter pack and from whatever has been scanned before. The remote search is a **button**, not a
 * keystroke handler, for two reasons that both matter: OFF's quota is per client, and a name typed in
 * Cyrillic would otherwise fire a request per character.
 *
 * A remote result is not stored until it is tapped. The products table holds what was chosen, and
 * [com.balancecalories.data.repo.ProductRepository.importSearchHit] funnels the tap through the same
 * `saveFromOff` rules as a scan, so an override or a newer revision behaves identically.
 */
@Composable
fun ProductsScreen(
    container: AppContainer,
    profileId: Long,
    onPick: (Product) -> Unit,
    onAddManual: (String?) -> Unit,
    onScan: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Product>>(emptyList()) }
    var remote by remember { mutableStateOf<RemoteState>(RemoteState.Idle) }
    var recents by remember { mutableStateOf<List<RecentlyEaten>>(emptyList()) }
    val languageTag = LocalConfiguration.current.locales[0]?.toLanguageTag()
    val scope = rememberCoroutineScope()

    LaunchedEffect(query) {
        val asked = query.trim()
        results = if (asked.isEmpty()) {
            container.products.browse()
        } else {
            container.products.search(asked)
        }
        // Only meaningful on an empty query: once the user types, their words outrank their habits.
        if (asked.isEmpty()) {
            recents = runCatching { container.food.recentlyEaten(profileId) }.getOrDefault(emptyList())
        }
        // A new query makes the old answer look like an answer to the new question.
        remote = RemoteState.Idle

        // The fallback is automatic (owner's note, 2026-11-13): "if there are no local findings, search Open Food
        // Facts right away, don't wait for user action". The phone is asked first because it is free and instant,
        // and the network is asked only when it had nothing - after the typing settles, and only if the question
        // has not changed in the meantime.
        if (asked.length >= MIN_REMOTE_QUERY && results.isEmpty()) {
            delay(REMOTE_DEBOUNCE_MS)
            if (query.trim() == asked) {
                remote = RemoteState.Searching
                remote = searchOpenFoodFacts(container, asked)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // The title is the tab label above this screen now, so the header is only the search field's context.


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

        // The button survives the automatic fallback for the two cases the automatic rule cannot cover: the phone
        // did find something but not the right thing, and the network was unreachable and deserves a retry.
        val offersManualRemote = query.trim().length >= MIN_REMOTE_QUERY &&
            (results.isNotEmpty() || remote is RemoteState.Failed)
        if (offersManualRemote) {
            TextButton(
                enabled = remote !is RemoteState.Searching,
                onClick = {
                    val asked = query.trim()
                    remote = RemoteState.Searching
                    scope.launch {
                        remote = searchOpenFoodFacts(container, asked)
                    }
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

        LazyColumn(modifier = Modifier.weight(1f)) {
            if (query.isBlank() && recents.isNotEmpty()) {
                item("recent-header") {
                    Text(text = stringResource(R.string.food_recent), style = MaterialTheme.typography.titleSmall)
                }
                items(recents, key = { "recent-${it.product.id}" }) { recent ->
                    RecentRow(recent = recent, languageTag = languageTag, onClick = { onPick(recent.product) })
                }
                item("recent-divider") { HorizontalDivider() }
            }

            items(results, key = { "local-${it.id}" }) { product ->
                ProductRow(product = product, languageTag = languageTag, onClick = { onPick(product) })
            }

            when (val state = remote) {
                RemoteState.Idle, RemoteState.Searching -> Unit

                is RemoteState.Failed -> item("remote-failed") {
                    Text(
                        text = stringResource(R.string.food_search_off_failed, state.reason),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                is RemoteState.Found -> {
                    val found = state.search
                    if (found.isEmpty) {
                        item("remote-none-named") {
                            TextButton(onClick = { onAddManual(query.trim()) }) {
                                Text(stringResource(R.string.food_add_manual_named, query.trim()))
                            }
                        }
                        item("remote-none") {
                            Text(
                                text = stringResource(R.string.food_search_off_nothing),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    } else {
                        item("remote-header") {
                            Text(
                                text = stringResource(R.string.food_search_off_count, found.totalCount),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = stringResource(R.string.food_search_off_note),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        items(found.products, key = { "remote-${it.code}" }) { hit ->
                            RemoteProductRow(
                                hit = hit,
                                onClick = {
                                    scope.launch {
                                        val stored = runCatching {
                                            container.products.importSearchHit(hit)
                                        }.getOrNull()
                                        if (stored != null) onPick(stored)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        TextButton(onClick = onScan) { Text(stringResource(R.string.action_scan)) }
        TextButton(onClick = { onAddManual(null) }) { Text(stringResource(R.string.food_add_manual)) }
    }
}

@Composable
private fun ProductRow(product: Product, languageTag: String?, onClick: () -> Unit) {
    // A row that opens something is a button to a screen reader, and it has to be tall enough to hit: 48 dp is
    // the accessibility minimum, and `Text` alone lands a couple of dp short of it.
    Column(
        modifier = Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.a11y_open), onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = 8.dp),
    ) {
        Text(text = product.displayName(languageTag), style = MaterialTheme.typography.bodyLarge)
        WrappingRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

/**
 * One product from the log, with how often it was eaten.
 *
 * Tapping opens the product screen rather than logging straight away: the portion and the meal are choices,
 * and guessing them would silently put a number on the plate the user did not pick.
 */
@Composable
private fun RecentRow(recent: RecentlyEaten, languageTag: String?, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.a11y_open), onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = 8.dp),
    ) {
        Text(text = recent.product.displayName(languageTag), style = MaterialTheme.typography.bodyLarge)
        WrappingRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.kcal_decimal, recent.product.kcal100g ?: 0.0),
                style = MaterialTheme.typography.bodySmall,
            )
            if (recent.times > 1) {
                Text(
                    text = stringResource(R.string.food_recent_times, recent.times),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

/**
 * One OpenFoodFacts hit.
 *
 * A hit with no energy is shown but not clickable, with the reason in place of a number: hiding it would
 * look like the search failed, and logging it would put a zero-calorie product on the plate.
 */
@Composable
private fun RemoteProductRow(hit: OffProduct, onClick: () -> Unit) {
    val usable = hit.isUsable
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = usable, onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(text = hit.name ?: hit.code, style = MaterialTheme.typography.bodyLarge)
        WrappingRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (usable) {
                Text(
                    text = stringResource(R.string.kcal_decimal, hit.energy?.kcal ?: 0.0),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    text = stringResource(R.string.food_search_off_no_energy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(text = hit.brand ?: ProductSource.OFF.localized(), style = MaterialTheme.typography.bodySmall)
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