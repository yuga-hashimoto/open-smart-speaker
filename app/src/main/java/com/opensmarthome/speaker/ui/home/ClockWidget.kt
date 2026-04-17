package com.opensmarthome.speaker.ui.home

import android.text.format.DateFormat
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opensmarthome.speaker.ui.theme.SpeakerTextPrimary
import com.opensmarthome.speaker.ui.theme.SpeakerTextSecondary
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun ClockWidget(
    time: LocalDateTime,
    modifier: Modifier = Modifier,
    use24Hour: Boolean = DateFormat.is24HourFormat(LocalContext.current),
) {
    val infiniteTransition = rememberInfiniteTransition(label = "colon")
    val colonAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "colonAlpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = formatHourMinute(time, use24Hour),
                style = MaterialTheme.typography.displayLarge,
                color = SpeakerTextPrimary,
                fontWeight = FontWeight.Thin,
                modifier = Modifier.alpha(1f) // Keep alpha separation — future ":"-blink tweak
            )
            if (!use24Hour) {
                Text(
                    text = time.format(DateTimeFormatter.ofPattern("a")),
                    style = MaterialTheme.typography.titleMedium,
                    color = SpeakerTextSecondary,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier
                        .padding(start = 10.dp, bottom = 14.dp)
                        .alpha(colonAlpha.coerceAtLeast(0.8f))
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = time.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
            style = MaterialTheme.typography.titleLarge,
            color = SpeakerTextSecondary,
            fontWeight = FontWeight.Light
        )
    }
}

/**
 * Format the hour-minute portion respecting [use24Hour]. Hour is always
 * shown without a leading zero in 12-hour mode so "9:05 AM" reads more
 * naturally than "09:05"; 24-hour mode keeps the zero-padded "09:05"
 * look users expect from a digital clock.
 */
internal fun formatHourMinute(time: LocalDateTime, use24Hour: Boolean): String {
    val pattern = if (use24Hour) "HH:mm" else "h:mm"
    return time.format(DateTimeFormatter.ofPattern(pattern))
}
