package com.opendash.app.ui.voice

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.opendash.app.ui.theme.VoiceListening
import com.opendash.app.ui.theme.VoiceSpeaking
import com.opendash.app.voice.pipeline.VoicePipelineState
import kotlin.math.sin

@Composable
fun VoiceStateAnimation(state: VoicePipelineState, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(160.dp), contentAlignment = Alignment.Center) {
        when (state) {
            is VoicePipelineState.Listening -> ListeningAnimation()
            is VoicePipelineState.Processing, is VoicePipelineState.Thinking -> ThinkingDotsAnimation()
            is VoicePipelineState.PreparingSpeech, is VoicePipelineState.Speaking -> SpeakingWaveformAnimation()
            is VoicePipelineState.Error -> ErrorPulse()
            else -> {}
        }
    }
}

@Composable
private fun ListeningAnimation(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "listen")

    val ring1Scale by transition.animateFloat(1f, 1.6f,
        infiniteRepeatable(tween(1500, easing = EaseInOutSine), RepeatMode.Restart), label = "r1")
    val ring1Alpha by transition.animateFloat(0.5f, 0f,
        infiniteRepeatable(tween(1500, easing = EaseInOutSine), RepeatMode.Restart), label = "a1")
    val ring2Scale by transition.animateFloat(1f, 1.4f,
        infiniteRepeatable(tween(1500, 200, easing = EaseInOutSine), RepeatMode.Restart), label = "r2")
    val ring2Alpha by transition.animateFloat(0.3f, 0f,
        infiniteRepeatable(tween(1500, 200, easing = EaseInOutSine), RepeatMode.Restart), label = "a2")
    val coreScale by transition.animateFloat(0.95f, 1.05f,
        infiniteRepeatable(tween(800, easing = EaseInOutSine), RepeatMode.Reverse), label = "core")

    Canvas(modifier = modifier.size(140.dp)) {
        val center = this.center
        val baseRadius = size.minDimension / 4

        drawCircle(VoiceListening.copy(alpha = ring1Alpha), radius = baseRadius * ring1Scale,
            center = center, style = Stroke(width = 3.dp.toPx()))
        drawCircle(VoiceListening.copy(alpha = ring2Alpha), radius = baseRadius * ring2Scale,
            center = center, style = Stroke(width = 2.dp.toPx()))
        drawCircle(VoiceListening.copy(alpha = 0.8f), radius = baseRadius * coreScale, center = center)
    }
}

@Composable
private fun ThinkingDotsAnimation(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "dots")

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) { index ->
            val offsetY by transition.animateFloat(
                initialValue = 0f,
                targetValue = -14f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, delayMillis = index * 150, easing = EaseInOutSine),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .offset(y = offsetY.dp)
                    .background(VoiceListening, CircleShape)
            )
        }
    }
}

@Composable
private fun SpeakingWaveformAnimation(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "wave")

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(5) { index ->
            val height by transition.animateFloat(
                initialValue = 12f,
                targetValue = 40f + (index * 8f),
                animationSpec = infiniteRepeatable(
                    animation = tween(400 + index * 100, easing = EaseInOutSine),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar$index"
            )
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(height.dp)
                    .background(VoiceSpeaking, RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
private fun ErrorPulse(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "error")
    val scale by transition.animateFloat(1f, 1.2f,
        infiniteRepeatable(tween(300), RepeatMode.Reverse), label = "errScale")

    Box(
        modifier = modifier
            .size(60.dp)
            .scale(scale)
            .background(com.opendash.app.ui.theme.VoiceError, CircleShape)
    )
}
