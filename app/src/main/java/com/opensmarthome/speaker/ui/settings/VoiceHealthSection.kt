package com.opensmarthome.speaker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.content.Intent
import com.opensmarthome.speaker.R
import com.opensmarthome.speaker.service.VoiceService
import com.opensmarthome.speaker.ui.common.StatusIndicator
import com.opensmarthome.speaker.ui.common.StatusIndicatorState
import com.opensmarthome.speaker.voice.diagnostics.VoiceDiagnostics

@Composable
fun VoiceHealthSection() {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<VoiceDiagnostics.DiagnosticItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        items = VoiceDiagnostics.run(context)
    }

    Text(
        text = stringResource(R.string.voice_health_title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    if (items.isEmpty()) {
        Text(
            text = stringResource(R.string.voice_health_running),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Column {
        for (item in items) {
            DiagnosticCard(item) {
                items = VoiceDiagnostics.run(context)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        OutlinedButton(
            onClick = { items = VoiceDiagnostics.run(context) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.voice_health_refresh), color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedButton(
            onClick = {
                val intent = Intent(context, VoiceService::class.java).apply {
                    action = VoiceService.ACTION_START_LISTENING
                }
                try {
                    context.startService(intent)
                } catch (_: Exception) { /* startForegroundService policy may refuse */ }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.voice_health_test_mic), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun DiagnosticCard(item: VoiceDiagnostics.DiagnosticItem, onAction: () -> Unit) {
    val context = LocalContext.current
    val statusState = when (item.severity) {
        VoiceDiagnostics.Severity.OK -> StatusIndicatorState.Online
        VoiceDiagnostics.Severity.WARNING -> StatusIndicatorState.Connecting
        VoiceDiagnostics.Severity.ERROR -> StatusIndicatorState.Error
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusIndicator(state = statusState, dotSize = 12)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = item.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, top = 2.dp)
            )
            if (item.actionLabel != null && item.actionIntent != null) {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        try {
                            context.startActivity(item.actionIntent)
                            onAction()
                        } catch (_: Exception) { /* ignore */ }
                    },
                    modifier = Modifier.padding(start = 16.dp)
                ) {
                    Text(item.actionLabel, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
