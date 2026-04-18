package com.opendash.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opendash.app.tool.system.CalendarEvent
import com.opendash.app.ui.theme.SpeakerSurfaceVariant
import com.opendash.app.ui.theme.SpeakerTextPrimary
import com.opendash.app.ui.theme.SpeakerTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shows the single next calendar event at a glance — the Alexa morning-
 * briefing line ("Next: 9:00 AM Standup"). When there's no event the
 * composable renders nothing so Home doesn't keep a permanent empty tile.
 *
 * Time formatting follows the device locale: 24h for JP / most non-US
 * locales, 12h with AM/PM for en-US. No date is shown because the card
 * is only ever populated by the next-24-hours lookahead.
 */
@Composable
fun NextEventCard(
    event: CalendarEvent?,
    modifier: Modifier = Modifier,
    nowMs: Long = System.currentTimeMillis(),
    locale: Locale = Locale.getDefault(),
) {
    if (event == null) return

    val label = formatStartTime(event.startMs, locale)
    val running = event.startMs <= nowMs

    Row(
        modifier = modifier
            .background(SpeakerSurfaceVariant, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Filled.Event,
            contentDescription = null,
            tint = SpeakerTextSecondary,
            modifier = Modifier.size(20.dp),
        )
        Column {
            Text(
                text = if (running) nowLabel(locale) else label,
                style = MaterialTheme.typography.labelMedium,
                color = SpeakerTextSecondary,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.size(2.dp))
            Text(
                text = event.title.ifBlank { fallbackTitle(locale) },
                style = MaterialTheme.typography.titleSmall,
                color = SpeakerTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatStartTime(startMs: Long, locale: Locale): String {
    val pattern = if (locale.language == "en" && (locale.country == "US" || locale.country.isEmpty())) {
        "h:mm a"
    } else {
        "HH:mm"
    }
    return SimpleDateFormat(pattern, locale).format(Date(startMs))
}

private fun nowLabel(locale: Locale): String =
    if (locale.language == "ja") "いま" else "Now"

private fun fallbackTitle(locale: Locale): String =
    if (locale.language == "ja") "予定" else "Event"
