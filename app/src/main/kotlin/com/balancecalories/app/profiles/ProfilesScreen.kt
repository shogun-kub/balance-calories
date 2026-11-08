package com.balancecalories.app.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.balancecalories.app.AppContainer
import com.balancecalories.app.R
import com.balancecalories.core.energy.Sex
import com.balancecalories.core.profile.Profile
import kotlinx.coroutines.launch

/**
 * Profiles for this phone: which one is shown, which one reads the band, and the two destructive or
 * structural actions — edit and delete.
 *
 * Tapping a card selects that profile as the current one, which is the "switch" the plan asks for.
 */
@Composable
fun ProfilesScreen(
    container: AppContainer,
    profiles: List<Profile>,
    activeProfileId: Long?,
    onAddProfile: () -> Unit,
    onEditProfile: (Profile) -> Unit,
    onSelectProfile: (Profile) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<Profile?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.profiles_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.profiles_note), style = MaterialTheme.typography.bodySmall)

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(profiles, key = { it.id }) { profile ->
                val isCurrent = profile.id == activeProfileId
                Card(
                    onClick = { onSelectProfile(profile) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(profile.name, style = MaterialTheme.typography.titleMedium)
                            if (isCurrent) {
                                Text(
                                    text = stringResource(R.string.current_badge),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                        Text(
                            text = stringResource(
                                when (profile.sex) {
                                    Sex.MALE -> R.string.sex_male
                                    Sex.FEMALE -> R.string.sex_female
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (profile.bandEnabled) {
                            Text(
                                text = stringResource(R.string.band_owner_badge),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onEditProfile(profile) }) {
                                Text(stringResource(R.string.action_edit))
                            }
                            TextButton(onClick = { pendingDelete = profile }) {
                                Text(stringResource(R.string.action_delete))
                            }
                        }
                    }
                }
            }
        }

        failure?.let { message ->
            Text(
                text = stringResource(R.string.error_save, message),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Button(onClick = onAddProfile, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_add_profile))
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_back))
        }
    }

    pendingDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_dialog_title)) },
            text = { Text(stringResource(R.string.delete_dialog_text, profile.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        scope.launch {
                            try {
                                container.profiles.delete(profile.id)
                            } catch (error: Exception) {
                                failure = error.message ?: error::class.java.simpleName
                            }
                        }
                    },
                ) { Text(stringResource(R.string.delete_dialog_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
