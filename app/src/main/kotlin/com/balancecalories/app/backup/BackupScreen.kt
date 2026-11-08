package com.balancecalories.app.backup

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.balancecalories.app.AppContainer
import com.balancecalories.app.R
import com.balancecalories.core.energy.TargetCalculator
import com.balancecalories.core.profile.Profile
import com.balancecalories.data.backup.BackupReader
import com.balancecalories.data.backup.BackupWriter
import com.balancecalories.data.backup.DaysCsv
import com.balancecalories.data.backup.ImportOutcome
import com.balancecalories.data.db.BalanceCaloriesDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backup, restore and the spreadsheet report.
 *
 * Three rules shape this screen. The file goes **where the person chose** through the system document picker, never
 * to a fixed path and never automatically to a shared folder. A restore **asks first**, because it replaces
 * everything and there is no undo. And every outcome is **stated**: a backup that silently failed is worse than no
 * backup button, because it is a reason not to make another one.
 */
@Composable
fun BackupScreen(
    container: AppContainer,
    profile: Profile,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<BackupStatus?>(null) }
    var confirmRestore by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }


    val exportJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            status = runCatching {
                val json = withContext(Dispatchers.IO) {
                    container.backup.writeJson(appVersion = context.appVersionName())
                }
                writeText(context, uri, json)
                BackupStatus.JsonSaved(json.length)
            }.getOrElse { BackupStatus.Failed(it.readable()) }
            busy = false
        }
    }

    val exportCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            status = runCatching {
                val csv = withContext(Dispatchers.IO) { buildCsv(container, profile) }
                writeText(context, uri, csv)
                BackupStatus.CsvSaved
            }.getOrElse { BackupStatus.Failed(it.readable()) }
            busy = false
        }
    }

    val importJson = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) confirmRestore = uri
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.headlineMedium)

        Text(stringResource(R.string.backup_json_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.backup_json_note), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
            onClick = { exportJson.launch("balancecalories-${System.currentTimeMillis()}.json") },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.backup_export_json))
        }

        HorizontalDivider()

        Text(stringResource(R.string.backup_csv_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.backup_csv_note), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
            onClick = { exportCsv.launch("balancecalories-days-${System.currentTimeMillis()}.csv") },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.backup_export_csv))
        }

        HorizontalDivider()

        Text(stringResource(R.string.backup_restore_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.backup_restore_note), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
            onClick = { importJson.launch(arrayOf("application/json")) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.backup_import_json))
        }

        status?.let { result ->
            Text(
                text = result.message(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (result.isFailure) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }

        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_back))
        }
    }

    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = { Text(stringResource(R.string.backup_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = null
                        busy = true
                        scope.launch {
                            status = restore(context, container, uri)
                            busy = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.backup_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

private suspend fun restore(
    context: Context,
    container: AppContainer,
    uri: Uri,
): BackupStatus {
    val json = runCatching {
        withContext(Dispatchers.IO) { readText(context, uri) }
    }.getOrElse { return BackupStatus.Failed(it.readable()) }

    val outcome = withContext(Dispatchers.IO) { container.backup.restore(json) }

    return when (outcome) {
        is ImportOutcome.Restored -> {
            // The profile the app had open may not exist in the restored data. Rather than showing an empty Today
            // screen with no explanation, point the app at the first profile that does exist — or at nothing, which
            // sends it to the profile picker.
            val active = container.settings.activeProfileId()
            val exists = active != null && container.profiles.byId(active) != null
            if (!exists) {
                val first = container.profiles.observeActive().first().firstOrNull()
                if (first != null) {
                    container.settings.setActiveProfile(first.id)
                } else {
                    container.settings.clearActiveProfile()
                }
            }
            // Products travel in the backup, starter ingredients included. So the importer must be told which
            // world it is in: marking them present avoids adding a second copy of every ingredient, while a file
            // from before products travelled whole still needs the bundled pack imported.
            if (outcome.containsStarterProducts) {
                container.starterPack.markImported()
            } else {
                container.starterPack.ensureImported()
            }
            BackupStatus.Restored(outcome.rows, outcome.fromSchemaVersion, outcome.droppedColumns.size)
        }
        is ImportOutcome.Refused -> BackupStatus.Refused(outcome.reason)
    }
}

private suspend fun buildCsv(container: AppContainer, profile: Profile): String {
    // The report shows the same BMR and TDEE the screen shows, so the two cannot drift apart.
    val weight = container.body.latestWeightKg(profile.id) ?: profile.goalWeightKg ?: 70.0
    val seed = TargetCalculator.seed(
        profile = profile.toEnergyProfile(weight),
        goalRateKgPerWeek = profile.goalRateKgPerWeek,
        on = java.time.LocalDate.now(),
    )
    return container.backup.buildDaysCsv(
        profileId = profile.id,
        bmrKcal = seed.bmrKcal,
        palKcal = seed.tdeeKcal,
    )
}

private fun writeText(context: Context, uri: Uri, text: String) {
    val stream = context.contentResolver.openOutputStream(uri)
        ?: error("the chosen file could not be opened for writing")
    stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
}

private fun readText(context: Context, uri: Uri): String {
    val stream = context.contentResolver.openInputStream(uri)
        ?: error("the chosen file could not be opened for reading")
    return stream.use { it.readBytes().toString(Charsets.UTF_8) }
}

private fun Context.appVersionName(): String = runCatching {
    packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
}.getOrDefault("unknown")

private fun Throwable.readable(): String = message ?: this::class.java.simpleName

/** Every outcome the screen can report, mapped to a string so the mapping itself is visible in one place. */
private sealed interface BackupStatus {
    val isFailure: Boolean

    data class JsonSaved(val bytes: Int) : BackupStatus {
        override val isFailure = false
    }

    data object CsvSaved : BackupStatus {
        override val isFailure = false
    }

    data class Restored(val rows: Int, val fromSchemaVersion: Int, val droppedColumns: Int) : BackupStatus {
        override val isFailure = false
    }

    data class Refused(val reason: ImportOutcome.Refusal) : BackupStatus {
        override val isFailure = true
    }

    data class Failed(val why: String) : BackupStatus {
        override val isFailure = true
    }
}

@Composable
private fun BackupStatus.message(): String = when (this) {
    is BackupStatus.JsonSaved -> pluralStringResource(R.plurals.backup_saved, bytes, bytes)
    BackupStatus.CsvSaved -> stringResource(R.string.backup_csv_saved)
    is BackupStatus.Restored -> if (droppedColumns > 0) {
        pluralStringResource(R.plurals.backup_restored_partial, rows, rows, fromSchemaVersion, droppedColumns)
    } else {
        pluralStringResource(R.plurals.backup_restored, rows, rows, fromSchemaVersion)
    }
    is BackupStatus.Refused -> when (reason) {
        ImportOutcome.Refusal.NOT_A_BACKUP -> stringResource(R.string.backup_refused_not_a_backup)
        ImportOutcome.Refusal.NEWER_FORMAT -> stringResource(R.string.backup_refused_newer)
        ImportOutcome.Refusal.NEWER_SCHEMA -> stringResource(R.string.backup_refused_newer)
        ImportOutcome.Refusal.DAMAGED -> stringResource(R.string.backup_refused_damaged)
    }
    is BackupStatus.Failed -> stringResource(R.string.backup_failed, why)
}
