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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
