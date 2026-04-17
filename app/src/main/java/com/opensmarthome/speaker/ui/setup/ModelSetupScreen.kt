package com.opensmarthome.speaker.ui.setup

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opensmarthome.speaker.assistant.provider.embedded.AvailableModel
import com.opensmarthome.speaker.assistant.provider.embedded.ModelDownloadState
import com.opensmarthome.speaker.ui.theme.SpeakerBackground
import com.opensmarthome.speaker.ui.theme.SpeakerPrimary
import com.opensmarthome.speaker.ui.theme.SpeakerSurface
import com.opensmarthome.speaker.ui.theme.SpeakerTextPrimary
import com.opensmarthome.speaker.ui.theme.SpeakerTextSecondary
import com.opensmarthome.speaker.ui.theme.VoiceError
import kotlinx.coroutines.flow.StateFlow

@Composable
fun ModelSetupScreen(
    downloadState: StateFlow<ModelDownloadState>,
    selectedModel: AvailableModel?,
    availableModels: List<AvailableModel>,
    onSelectModel: (AvailableModel) -> Unit,
    onStartDownload: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by downloadState.collectAsState()

    val transition = rememberInfiniteTransition(label = "pulse")
    val iconAlpha by transition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "iconAlpha"
    )

    // Setup runs before the main shell so it has no ModeScaffold parent —
    // apply inset padding directly to avoid the progress text sliding under
    // the status bar on edge-to-edge Android.
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SpeakerBackground)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp).fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Filled.Psychology,
                contentDescription = null,
                tint = SpeakerPrimary,
                modifier = Modifier.size(56.dp).alpha(
                    if (state is ModelDownloadState.Downloading) iconAlpha else 1f
                )
            )

            Spacer(Modifier.height(16.dp))

            when (val s = state) {
                is ModelDownloadState.NotStarted -> {
                    Text(
                        text = "Select AI Model",
                        style = MaterialTheme.typography.headlineSmall,
                        color = SpeakerTextPrimary
                    )
                    Spacer(Modifier.height(4.dp))

                    if (availableModels.isEmpty()) {
                        Text("Loading models from HuggingFace...", color = SpeakerTextSecondary)
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(0.4f).height(4.dp),
                            color = SpeakerPrimary, trackColor = SpeakerSurface
                        )
                    } else {
                        Text(
                            "${availableModels.size} models available",
                            style = MaterialTheme.typography.bodySmall,
                            color = SpeakerTextSecondary
                        )
                        Spacer(Modifier.height(12.dp))

                        LazyColumn(
                            modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(availableModels) { model ->
                                ModelCard(
                                    model = model,
                                    isSelected = model.id == selectedModel?.id,
                                    onClick = { onSelectModel(model) }
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        if (selectedModel != null) {
                            Button(
                                onClick = onStartDownload,
                                colors = ButtonDefaults.buttonColors(containerColor = SpeakerPrimary),
                                modifier = Modifier.fillMaxWidth(0.7f).height(48.dp)
                            ) {
                                Text("Download (${selectedModel.sizeMb}MB)")
                            }
                        }
                    }
                }

                is ModelDownloadState.Checking -> {
                    Text("Checking...", style = MaterialTheme.typography.headlineSmall, color = SpeakerTextPrimary)
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(0.4f).height(4.dp),
                        color = SpeakerPrimary, trackColor = SpeakerSurface
                    )
                }

                is ModelDownloadState.Downloading -> {
                    Text("Downloading", style = MaterialTheme.typography.headlineSmall, color = SpeakerTextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${selectedModel?.displayName ?: "Model"} — ${s.downloadedMb}MB / ${s.totalMb}MB",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SpeakerTextSecondary
                    )
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { s.progress },
                        modifier = Modifier.fillMaxWidth(0.7f).height(6.dp),
                        color = SpeakerPrimary, trackColor = SpeakerSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("${(s.progress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = SpeakerPrimary)
                    Spacer(Modifier.height(16.dp))
                    Text("Keep the app open", style = MaterialTheme.typography.bodySmall, color = SpeakerTextSecondary)
                }

                is ModelDownloadState.Error -> {
                    Text("Failed", style = MaterialTheme.typography.headlineSmall, color = VoiceError)
                    Spacer(Modifier.height(8.dp))
                    Text(s.message, style = MaterialTheme.typography.bodyMedium, color = VoiceError, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = SpeakerPrimary)) {
                        Text("Try Again")
                    }
                }

                is ModelDownloadState.Ready -> {
                    Text("Ready!", style = MaterialTheme.typography.headlineSmall, color = SpeakerPrimary)
                }
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: AvailableModel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) SpeakerPrimary else SpeakerSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(10.dp))
            .background(SpeakerSurface)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .border(2.dp, if (isSelected) SpeakerPrimary else SpeakerTextSecondary, CircleShape)
                .padding(3.dp)
                .then(if (isSelected) Modifier.background(SpeakerPrimary, CircleShape) else Modifier)
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = model.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = SpeakerTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${model.sizeMb}MB",
                style = MaterialTheme.typography.bodySmall,
                color = SpeakerTextSecondary
            )
        }
    }
}
