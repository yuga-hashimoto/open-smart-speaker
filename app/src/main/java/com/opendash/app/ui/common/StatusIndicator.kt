package com.opendash.app.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class StatusIndicatorState { Online, Connecting, Offline, Error }

/**
 * Animated status dot with optional label.
 * Reference: OpenClaw Assistant StatusIndicator.
 *
 * - Online: solid green
 * - Connecting: pulsing amber
 * - Offline: solid grey
 * - Error: solid red
 */
@Composable
fun StatusIndicator(
    state: StatusIndicatorState,
    modifier: Modifier = Modifier,
    label: String? = null,
    dotSize: Int = 10
) {
    val color = when (state) {
        StatusIndicatorState.Online -> Color(0xFF4CAF50)
        StatusIndicatorState.Connecting -> Color(0xFFFFA726)
        StatusIndicatorState.Offline -> Color(0xFF9E9E9E)
        StatusIndicatorState.Error -> Color(0xFFE53935)
    }

    val alpha: Float = if (state == StatusIndicatorState.Connecting) {
        val transition = rememberInfiniteTransition(label = "statusPulse")
        val anim = transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )
        anim.value
    } else {
        1.0f
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Canvas(modifier = Modifier.size(dotSize.dp)) {
            drawCircle(color = color.copy(alpha = alpha))
        }
        if (label != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
