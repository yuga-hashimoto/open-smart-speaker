package com.opendash.app.ui.settings.multiroom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opendash.app.R
import com.opendash.app.multiroom.SpeakerGroup
import com.opendash.app.util.DiscoveredSpeaker

/**
 * Lists saved [SpeakerGroup]s, with a FAB to create a new one and a
 * per-group edit dialog that toggles discovered peers in/out of the
 * group membership. Deletes are one-tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSpeakerGroupsScreen(
    onBack: () -> Unit,
    viewModel: SpeakerGroupsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarMsg by viewModel.snackbar.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var showCreate by remember { mutableStateOf(false) }

    val resolvedSnackbar = snackbarMsg?.let {
        stringResource(it.resId, *it.args.toTypedArray())
    }
    resolvedSnackbar?.let { text ->
        LaunchedEffect(text) {
            snackbarHost.showSnackbar(text)
            viewModel.consumeSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.speaker_groups_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(R.string.speaker_groups_new)) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = { showCreate = true }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        if (state.loading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            GroupsList(
                groups = state.groups,
                discoveredPeers = state.discoveredPeers,
                onEdit = { viewModel.openEditor(it) },
                onDelete = { viewModel.deleteGroup(it.name) },
                padding = padding
            )
        }

        if (showCreate) {
            CreateGroupDialog(
                onDismiss = { showCreate = false },
                onConfirm = { name ->
                    viewModel.createGroup(name)
                    showCreate = false
                }
            )
        }

        state.editing?.let { editing ->
            EditMembersDialog(
                group = editing,
                discoveredPeers = state.discoveredPeers,
                onToggle = { svcName -> viewModel.toggleMember(editing, svcName) },
                onDismiss = { viewModel.closeEditor() }
            )
        }
    }
}

@Composable
private fun GroupsList(
    groups: List<SpeakerGroup>,
    discoveredPeers: List<DiscoveredSpeaker>,
    onEdit: (SpeakerGroup) -> Unit,
    onDelete: (SpeakerGroup) -> Unit,
    padding: PaddingValues
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Text(
                text = if (groups.isEmpty()) {
                    stringResource(R.string.speaker_groups_empty, discoveredPeers.size)
                } else {
                    stringResource(R.string.speaker_groups_count, groups.size, discoveredPeers.size)
                },
                style = MaterialTheme.typography.labelMedium
            )
        }
        items(groups, key = { it.name }) { group ->
            GroupRow(
                group = group,
                discoveredPeers = discoveredPeers,
                onEdit = { onEdit(group) },
                onDelete = { onDelete(group) }
            )
        }
    }
}

@Composable
private fun GroupRow(
    group: SpeakerGroup,
    discoveredPeers: List<DiscoveredSpeaker>,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val discoveredSet = discoveredPeers.map { it.serviceName }.toSet()
    val reachable = group.memberServiceNames.count { it in discoveredSet }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(group.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(4.dp))
            Text(
                text = stringResource(R.string.speaker_groups_members, group.memberServiceNames.size, reachable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (group.memberServiceNames.isNotEmpty()) {
                Spacer(Modifier.size(4.dp))
                group.memberServiceNames.take(4).forEach { name ->
                    Text(
                        "• $name",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                if (group.memberServiceNames.size > 4) {
                    Text(
                        stringResource(R.string.speaker_groups_more, group.memberServiceNames.size - 4),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.common_delete)) }
                TextButton(onClick = onEdit) { Text(stringResource(R.string.speaker_groups_edit)) }
            }
        }
    }
}

@Composable
private fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.speaker_groups_new_dialog_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.speaker_groups_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name) }
            ) { Text(stringResource(R.string.speaker_groups_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
private fun EditMembersDialog(
    group: SpeakerGroup,
    discoveredPeers: List<DiscoveredSpeaker>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Union of discovered + existing members so the user can remove a
    // group entry even when the peer is currently offline.
    val discoveredNames = discoveredPeers.map { it.serviceName }.toSet()
    val candidates = (group.memberServiceNames + discoveredNames).sorted()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.speaker_groups_members_dialog_title, group.name)) },
        text = {
            if (candidates.isEmpty()) {
                Text(stringResource(R.string.speaker_groups_no_speakers))
            } else {
                Column {
                    candidates.forEach { svcName ->
                        val checked = svcName in group.memberServiceNames
                        val online = svcName in discoveredNames
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { onToggle(svcName) }
                            )
                            Column(modifier = Modifier.padding(start = 4.dp)) {
                                Text(svcName, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = if (online) stringResource(R.string.speaker_groups_reachable_now)
                                    else stringResource(R.string.speaker_groups_not_seen),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) }
        }
    )
}
