package com.opensmarthome.speaker.ui.voice

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opensmarthome.speaker.ui.theme.SpeakerBackground
import com.opensmarthome.speaker.ui.theme.SpeakerTextPrimary
import com.opensmarthome.speaker.ui.theme.SpeakerTextSecondary
import com.opensmarthome.speaker.ui.theme.SpeakerTextTertiary
import com.opensmarthome.speaker.ui.theme.VoiceError
import com.opensmarthome.speaker.voice.pipeline.VoicePipelineState

/**
 * Full-screen overlay shown during a voice interaction. During
 * [VoicePipelineState.Speaking] it renders [spokenText] (the sentence
 * currently being spoken by the TTS engine) in a large Alexa/Nest-Hub-style
 * headline that swaps with an [AnimatedContent] crossfade as each chunk
 * finishes — "karaoke" rolling. When the TTS provider does not stream
 * chunks, [spokenText] is empty and the full [responseText] is shown
 * instead so behaviour degrades gracefully.
 */
@Composable
fun VoiceOverlay(
    voiceState: VoicePipelineState,
    sttText: String,
    responseText: String,
    spokenText: String = "",
    modifier: Modifier = Modifier
) {
    val visible = voiceState !is VoicePipelineState.Idle &&
            voiceState !is VoicePipelineState.WakeWordListening

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 },
        exit = fadeOut(tween(500))
    ) {
        // Overlay is laid out inside ModeScaffold's inset-consuming root Box,
        // so this `systemBarsPadding()` is a safety net: if the overlay is
        // ever hoisted to MainActivity.setContent directly (tests, previews,
        // bug reproductions) the headline text still clears the status bar.
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(SpeakerBackground.copy(alpha = 0.94f))
                .systemBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                VoiceStateAnimation(state = voiceState)

                Spacer(Modifier.height(36.dp))

                // Show what user said
                if (sttText.isNotBlank()) {
                    Text(
                        text = "\"$sttText\"",
                        style = MaterialTheme.typography.titleLarge,
                        color = SpeakerTextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                }

                // During Speaking, roll through chunks karaoke-style. The
                // active chunk (spokenText) overrides the full response so
                // each sentence gets the whole headline real-estate. Outside
                // Speaking (thinking, error, etc.) we fall back to the full
                // response so users still see what the assistant said.
                val displayText = when {
                    voiceState is VoicePipelineState.Speaking && spokenText.isNotBlank() ->
                        spokenText
                    else -> responseText
                }

                if (displayText.isNotBlank()) {
                    AnimatedContent(
                        targetState = displayText,
                        transitionSpec = {
                            // Crossfade between chunks — no directional slide,
                            // keeps the "karaoke" feel centred like on a
                            // Nest Hub / Echo Show.
                            fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                        },
                        label = "karaoke-chunk"
                    ) { text ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (voiceState is VoicePipelineState.Error) VoiceError
                            else SpeakerTextPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .animateContentSize(tween(240))
                                .semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // State label — announced as a live region so TalkBack users
                // know when the pipeline transitions (listening → processing → speaking).
                Text(
                    text = when (voiceState) {
                        is VoicePipelineState.Listening -> "Listening..."
                        is VoicePipelineState.Processing -> "Processing..."
                        is VoicePipelineState.Thinking -> "Thinking..."
                        is VoicePipelineState.PreparingSpeech -> "Preparing..."
                        is VoicePipelineState.Speaking -> ""
                        is VoicePipelineState.Error -> ""
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = SpeakerTextTertiary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }
    }
}
