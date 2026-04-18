package com.opendash.app.ui.home

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendash.app.ui.theme.SpeakerTextPrimary
import com.opendash.app.ui.theme.SpeakerTextSecondary
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Big digital clock rendered on the Home dashboard.
 *
 * [largeMode] promotes the HH:mm block to a roughly-doubled size —
 * matches the Echo Show / Nest Hub ambient clock where the time is the
 * single most prominent widget on the screen. Off by default so older
 * callers (night clock overlay, skeleton previews) keep their existing
 * `displayLarge` sizing.
 */
@Composable
fun ClockWidget(
    time: LocalDateTime,
    modifier: Modifier = Modifier,
    use24Hour: Boolean = DateFormat.is24HourFormat(LocalContext.current),
    largeMode: Boolean = false,
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

    // Base styles. `largeMode` overrides the font size while keeping the
    // theme's typography lineage intact so Material 3 tonal / colour
    // roles still apply.
    val timeStyle: TextStyle = if (largeMode) {
        MaterialTheme.typography.displayLarge.copy(fontSize = 180.sp)
    } else {
        MaterialTheme.typography.displayLarge
    }
    val dateStyle: TextStyle = if (largeMode) {
        MaterialTheme.typography.headlineMedium
    } else {
        MaterialTheme.typography.titleLarge
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = formatHourMinute(time, use24Hour),
                style = timeStyle,
                color = SpeakerTextPrimary,
                fontWeight = FontWeight.Thin,
                modifier = Modifier.alpha(1f) // Keep alpha separation — future ":"-blink tweak
            )
            if (!use24Hour) {
                val ampmStyle = if (largeMode) {
                    MaterialTheme.typography.titleLarge
                } else {
                    MaterialTheme.typography.titleMedium
                }
                Text(
                    text = time.format(DateTimeFormatter.ofPattern("a")),
                    style = ampmStyle,
                    color = SpeakerTextSecondary,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier
                        .padding(start = 10.dp, bottom = if (largeMode) 28.dp else 14.dp)
                        .alpha(colonAlpha.coerceAtLeast(0.8f))
                )
            }
        }
        Spacer(modifier = Modifier.height(if (largeMode) 12.dp else 8.dp))
        Text(
            text = time.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
            style = dateStyle,
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
