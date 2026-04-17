package com.opensmarthome.speaker.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opensmarthome.speaker.permission.PermissionIntents
import com.opensmarthome.speaker.permission.PermissionRepository

/**
 * First-run walkthrough. Shows after model download completes but before the
 * main app appears. Users can review each permission with its rationale and
 * open settings to grant, or skip and grant later from Settings.
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.refresh() }

    // Edge-to-edge: keep the welcome heading below the status bar.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(24.dp)
    ) {
        Text(
            text = "Welcome",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Your on-device assistant needs a few permissions to be useful. You can grant them now or later from Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(state.rows, key = { it.id }) { row ->
                OnboardingRow(
                    row = row,
                    onOpenSettings = {
                        val action = when (row.kind) {
                            PermissionRepository.Row.Kind.RUNTIME -> null
                            PermissionRepository.Row.Kind.SPECIAL -> specialActionFor(row.id)
                        }
                        val intent = if (action != null) {
                            PermissionIntents.byAction(action)
                        } else {
                            PermissionIntents.appDetails(context)
                        }
                        context.startActivity(intent)
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { viewModel.markCompleted(); onDone() }) {
                Text("Skip")
            }
            Spacer(Modifier.size(8.dp))
            Button(onClick = { viewModel.markCompleted(); onDone() }) {
                Text("Get started")
            }
        }
    }
}

@Composable
private fun OnboardingRow(
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
                    text = if (row.granted) "Granted" else "Needs action",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (row.granted)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }
            Spacer(Modifier.size(4.dp))
            Text(row.rationale, style = MaterialTheme.typography.bodyMedium)
            if (!row.granted) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onOpenSettings) {
                        Text("Grant")
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
