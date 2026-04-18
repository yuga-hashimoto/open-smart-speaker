package com.opendash.app.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.opendash.app.ui.theme.SpeakerTextSecondary
import java.time.LocalDateTime
import java.util.Locale

/**
 * Single-line "Good morning" / "おはようございます" greeting rendered above
 * the clock on the Home dashboard. The time-of-day bucket is derived
 * purely from [time].hour so the Composable stays trivial to preview
 * and does not depend on `Clock.systemDefaultZone()`.
 */
@Composable
fun GreetingLine(
    time: LocalDateTime,
    modifier: Modifier = Modifier,
    locale: Locale = Locale.getDefault(),
) {
    Text(
        text = GreetingResolver.greetingFor(time.hour, locale),
        style = MaterialTheme.typography.titleMedium,
        color = SpeakerTextSecondary,
        fontWeight = FontWeight.Light,
        textAlign = TextAlign.Start,
        modifier = modifier,
    )
}
