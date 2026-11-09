package com.balancecalories.app.settings

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.balancecalories.app.AppContainer
import com.balancecalories.app.R
import com.balancecalories.app.food.trimNumber
import com.balancecalories.app.ui.NumberField
import com.balancecalories.app.ui.WrappingRow
import com.balancecalories.core.food.MacroNorm
import com.balancecalories.core.profile.Profile
import com.balancecalories.core.profile.UnitSystem
import kotlinx.coroutines.launch

/**
 * Settings that exist today: units for data entry, the day-start hour, and the app language.
 *
 * Units and day start are profile columns, so they are written immediately through the repository rather
 * than held in a form. Language is handled by Android itself: applying a per-app locale recreates the
 * activity, so the new strings come from the system rather than from a hand-rolled resource override.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    profile: Profile,
    onBack: () -> Unit,
    onOpenDebug: () -> Unit,
    onOpenData: () -> Unit,
    onOpenProfiles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var dayStartMenuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)

        Text(stringResource(R.string.settings_units), style = MaterialTheme.typography.titleMedium)
        WrappingRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = profile.unitSystem == UnitSystem.METRIC,
                onClick = {
                    scope.launch { container.profiles.update(profile.copy(unitSystem = UnitSystem.METRIC)) }
                },
                label = { Text(stringResource(R.string.units_metric)) },
            )
            FilterChip(
                selected = profile.unitSystem == UnitSystem.IMPERIAL,
                onClick = {
                    scope.launch { container.profiles.update(profile.copy(unitSystem = UnitSystem.IMPERIAL)) }
                },
                label = { Text(stringResource(R.string.units_imperial)) },
            )
        }
        Text(stringResource(R.string.settings_units_note), style = MaterialTheme.typography.bodySmall)

        Text(stringResource(R.string.settings_day_start), style = MaterialTheme.typography.titleMedium)
        Row {
            OutlinedButton(onClick = { dayStartMenuOpen = true }) {
                Text(stringResource(R.string.hour_pattern, profile.dayStartHour))
            }
            DropdownMenu(expanded = dayStartMenuOpen, onDismissRequest = { dayStartMenuOpen = false }) {
                (0..23).forEach { hour ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.hour_pattern, hour)) },
                        onClick = {
                            dayStartMenuOpen = false
                            if (hour != profile.dayStartHour) {
                                scope.launch {
                                    container.profiles.update(profile.copy(dayStartHour = hour))
                                }
                            }
                        },
                    )
                }
            }
        }
        Text(stringResource(R.string.settings_day_start_note), style = MaterialTheme.typography.bodySmall)

        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
        LanguageSetting()

        HorizontalDivider()
        MacroNormSetting(container = container, profile = profile)

        // Profiles moved here when the four tabs took the Today screen's navigation column: switching
        // profile is a rare act on a phone that belongs to one person, so it earns neither a tab nor a
        // button on Today.
        TextButton(onClick = onOpenProfiles) { Text(stringResource(R.string.profiles_title)) }

        HorizontalDivider()
        Text(stringResource(R.string.settings_data), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.settings_data_note), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onOpenData, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.backup_title))
        }

        TextButton(onClick = onOpenDebug) { Text(stringResource(R.string.action_debug)) }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_back))
        }
    }
}

/**
 * The daily protein/fat/carb allowance the Today list compares portions against.
 *
 * Empty means "follow the target": the norms are then 25/30/45 % of the calorie target in grams, recomputed
 * whenever the target moves. Typing numbers stores them for this profile for good, because a person who wrote
 * their own norms has said something the derivation cannot know (ADR-035).
 */
@Composable
private fun MacroNormSetting(container: AppContainer, profile: Profile) {
    val scope = rememberCoroutineScope()
    var targetKcal by remember(profile.id) { mutableStateOf<Int?>(null) }
    var storedNorm by remember(profile.id) { mutableStateOf<MacroNorm?>(null) }
    var protein by remember(profile.id) { mutableStateOf("") }
    var fat by remember(profile.id) { mutableStateOf("") }
    var carbs by remember(profile.id) { mutableStateOf("") }
    var message by remember(profile.id) { mutableStateOf<Int?>(null) }

    LaunchedEffect(profile.id) {
        targetKcal = container.targets.latest(profile.id)?.kcal
        val stored = container.settings.macroNorm(profile.id)
        storedNorm = stored
        protein = stored?.let { trimNumber(it.proteinG) } ?: ""
        fat = stored?.let { trimNumber(it.fatG) } ?: ""
        carbs = stored?.let { trimNumber(it.carbsG) } ?: ""
    }

    val derived = targetKcal?.takeIf { it > 0 }?.let { MacroNorm.derived(it) }

    Text(stringResource(R.string.macro_norm_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.macro_norm_note), style = MaterialTheme.typography.bodySmall)
    Text(
        text = if (storedNorm != null) {
            stringResource(R.string.macro_norm_own)
        } else if (derived != null) {
            stringResource(R.string.macro_norm_derived, targetKcal!!)
        } else {
            stringResource(R.string.macro_norm_own)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary,
    )

    NumberField(R.string.macro_norm_protein, protein) { protein = it }
    NumberField(R.string.macro_norm_fat, fat) { fat = it }
    NumberField(R.string.macro_norm_carbs, carbs) { carbs = it }

    WrappingRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                val values = listOf(protein, fat, carbs).map { it.replace(',', '.').toDoubleOrNull() }
                val norm = values.takeIf { list -> list.all { v -> v != null && v > 0.0 } }
                    ?.let { MacroNorm(it[0]!!, it[1]!!, it[2]!!) }
                if (norm == null) {
                    message = R.string.error_macro_norm_value
                    return@Button
                }
                scope.launch {
                    container.settings.setMacroNorm(profile.id, norm)
                    storedNorm = norm
                    message = R.string.macro_norm_saved
                }
            },
        ) {
            Text(stringResource(R.string.action_save))
        }
        OutlinedButton(
            onClick = {
                val target = derived ?: return@OutlinedButton
                protein = trimNumber(target.proteinG)
                fat = trimNumber(target.fatG)
                carbs = trimNumber(target.carbsG)
                scope.launch {
                    // Both halves of the gesture: the fields show the derived numbers, and the stored ones stop
                    // overriding them, so the norms follow the target again.
                    container.settings.clearMacroNorm(profile.id)
                    storedNorm = null
                    message = R.string.macro_norm_saved
                }
            },
            enabled = derived != null,
        ) {
            Text(stringResource(R.string.action_macro_norm_reset))
        }
    }
    message?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
}

/**
 * Android 13 gained per-app locales. Below that the platform has no supported way to override the app
 * language without shipping AppCompat's resource machinery, so the setting simply says so instead of
 * offering a control that would do nothing.
 */
@Composable
private fun LanguageSetting() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Text(stringResource(R.string.settings_language_unsupported), style = MaterialTheme.typography.bodySmall)
        return
    }

    val context = LocalContext.current
    val localeManager = remember(context) { context.getSystemService(LocaleManager::class.java) }
    var currentTags by remember { mutableStateOf(localeManager.applicationLocales.toLanguageTags()) }

    fun apply(tags: String) {
        // An empty LocaleList is how the platform means "follow the system". Building one from "" is not
        // the same thing and is silently ignored, which looks exactly like a button that does nothing.
        localeManager.applicationLocales =
            if (tags.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tags)
        currentTags = tags
    }

    WrappingRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = currentTags.isEmpty(),
            onClick = { apply("") },
            label = { Text(stringResource(R.string.language_system)) },
        )
        FilterChip(
            selected = currentTags.startsWith("en"),
            onClick = { apply("en") },
            label = { Text(stringResource(R.string.language_english)) },
        )
        FilterChip(
            selected = currentTags.startsWith("ru"),
            onClick = { apply("ru") },
            label = { Text(stringResource(R.string.language_russian)) },
        )
    }
}