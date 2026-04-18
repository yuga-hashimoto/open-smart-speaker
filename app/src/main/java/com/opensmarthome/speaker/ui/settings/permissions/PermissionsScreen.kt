package com.opensmarthome.speaker.ui.settings.permissions

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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opensmarthome.speaker.R
import com.opensmarthome.speaker.permission.PermissionIntents
import com.opensmarthome.speaker.permission.PermissionRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    onBack: () -> Unit,
    viewModel: PermissionsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Re-check when user returns from Settings (LifecycleEventObserver could be cleaner, but
    // this is simpler: every composition triggers a refresh on non-first render is wasteful,
    // so we rely on explicit onResume via tap-through elsewhere).
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.permissions_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
                },
                actions = {
                    TextButton(onClick = {
                        context.startActivity(PermissionIntents.appDetails(context))
                    }) { Text(stringResource(R.string.permissions_app_info)) }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Text(
                    text = if (state.ungrantedCount == 0) stringResource(R.string.permissions_all_unlocked)
                    else stringResource(R.string.permissions_locked_count, state.ungrantedCount),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            items(state.rows, key = { it.id }) { row ->
                PermissionRow(
                    row = row,
                    onOpenSettings = {
                        when (row.kind) {
                            PermissionRepository.Row.Kind.RUNTIME ->
                                context.startActivity(PermissionIntents.appDetails(context))
                            PermissionRepository.Row.Kind.SPECIAL -> {
                                val action = specialActionFor(row.id)
                                if (action != null) {
                                    context.startActivity(PermissionIntents.byAction(action))
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    row: PermissionRepository.Row,
    onOpenSettings: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(row.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (row.granted) stringResource(R.string.permissions_status_granted)
                    else stringResource(R.string.permissions_status_denied),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (row.granted)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.size(4.dp))
            Text(row.rationale, style = MaterialTheme.typography.bodyMedium)
            if (row.unlocks.isNotEmpty()) {
                Spacer(Modifier.size(4.dp))
                Text(
                    text = stringResource(R.string.permissions_unlocks_prefix, row.unlocks.joinToString(", ")),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!row.granted) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onOpenSettings) {
                        Text(stringResource(R.string.permissions_open_settings))
                    }
                }
            }
        }
    }
}

private fun specialActionFor(id: String): String? = when (id) {
    "notification_listener" -> "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
    "accessibility" -> "android.settings.ACCESSIBILITY_SETTINGS"
    else -> null
}
