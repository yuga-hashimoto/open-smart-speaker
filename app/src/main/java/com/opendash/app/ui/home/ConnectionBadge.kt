package com.opendash.app.ui.home

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.opendash.app.ui.theme.SpeakerSurfaceElevated
import com.opendash.app.ui.theme.SpeakerTextSecondary

enum class ConnectionStatus {
    CONNECTED, CONNECTING, DISCONNECTED
}

@Composable
fun ConnectionBadge(
    status: ConnectionStatus,
    providerCount: Int,
    modifier: Modifier = Modifier
) {
    val dotColor = when (status) {
        ConnectionStatus.CONNECTED -> Color(0xFF4CAF50)
        ConnectionStatus.CONNECTING -> Color(0xFFFFA726)
        ConnectionStatus.DISCONNECTED -> Color(0xFF757575)
    }

    val transition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (status == ConnectionStatus.CONNECTING) 0.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(
        modifier = modifier
            .background(SpeakerSurfaceElevated, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .alpha(pulseAlpha)
                .background(dotColor, CircleShape)
        )
        Text(
            text = when (status) {
                ConnectionStatus.CONNECTED -> "$providerCount device${if (providerCount != 1) "s" else ""}"
                ConnectionStatus.CONNECTING -> "Connecting..."
                ConnectionStatus.DISCONNECTED -> "Offline"
            },
            style = MaterialTheme.typography.labelSmall,
            color = SpeakerTextSecondary
        )
    }
}
