package com.opensmarthome.speaker.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Smart-speaker style voice orb showing pipeline state via pulsing color + audio level.
 *
 * Inspired by Ava's WakeRippleView and OpenClawSession's mic animation patterns
 * (referenced in docs/roadmap.md priority 1 — "feels like Alexa").
 *
 * States map to distinct colors + animation intensities:
 *   Idle              → dim neutral, no pulse
 *   WakeWordListening → green breathing pulse (standby)
 *   Listening         → blue active pulse with audio-level scaling
 *   Processing        → amber spin (still thinking)
 *   Thinking          → amber spin (alias)
 *   PreparingSpeech   → purple pre-speak pulse
 *   Speaking          → cyan gentle bob
 *   Error             → red solid
 */
@Composable
fun VoiceOrb(
    state: VoiceOrbState,
    audioLevel: Float = 0f,
    modifier: Modifier = Modifier,
    sizeDp: Int = 160
) {
    val color = colorForState(state)
    val animatedColor by animateColorAsState(color, tween(300), label = "orb-color")

    val breathing by rememberInfiniteTransition(label = "breath").animateFloat(
        initialValue = 1f,
        targetValue = when (state) {
            VoiceOrbState.WakeWordListening -> 1.08f
            VoiceOrbState.Speaking -> 1.06f
            VoiceOrbState.Idle, VoiceOrbState.Error -> 1.0f
            else -> 1.12f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = breathingDurationMs(state),
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb-breath"
    )

    // Audio-level overlay: when Listening, scale up to ~1.5 based on RMS.
    val normalizedLevel = ((audioLevel + 2f) / 10f).coerceIn(0f, 1f)
    val audioScale by animateFloatAsState(
        targetValue = if (state == VoiceOrbState.Listening) 1f + normalizedLevel * 0.4f else 1f,
        animationSpec = tween(80, easing = LinearEasing),
        label = "orb-audio"
    )

    val finalScale = maxOf(breathing, audioScale)

    Box(
        modifier = modifier.size(sizeDp.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(sizeDp.dp)) {
            val radius = (size.minDimension / 2f) * finalScale
            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
            // Soft outer glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animatedColor.copy(alpha = 0.35f),
                        animatedColor.copy(alpha = 0f)
                    ),
                    center = center,
                    radius = radius * 1.4f
                ),
                radius = radius * 1.4f,
                center = center
            )
            // Core
            drawCircle(
                color = animatedColor.copy(alpha = 0.9f),
                radius = radius,
                center = center
            )
        }
    }
}

enum class VoiceOrbState {
    Idle,
    WakeWordListening,
    Listening,
    Processing,
    Thinking,
    PreparingSpeech,
    Speaking,
    Error
}

private fun colorForState(state: VoiceOrbState): Color = when (state) {
    VoiceOrbState.Idle -> Color(0xFF37474F)
    VoiceOrbState.WakeWordListening -> Color(0xFF66BB6A)
    VoiceOrbState.Listening -> Color(0xFF42A5F5)
    VoiceOrbState.Processing, VoiceOrbState.Thinking -> Color(0xFFFFB300)
    VoiceOrbState.PreparingSpeech -> Color(0xFFAB47BC)
    VoiceOrbState.Speaking -> Color(0xFF26C6DA)
    VoiceOrbState.Error -> Color(0xFFE53935)
}

private fun breathingDurationMs(state: VoiceOrbState): Int = when (state) {
    VoiceOrbState.Idle -> 2400
    VoiceOrbState.WakeWordListening -> 1600
    VoiceOrbState.Listening -> 500
    VoiceOrbState.Processing, VoiceOrbState.Thinking -> 900
    VoiceOrbState.PreparingSpeech -> 700
    VoiceOrbState.Speaking -> 1200
    VoiceOrbState.Error -> 4000
}
