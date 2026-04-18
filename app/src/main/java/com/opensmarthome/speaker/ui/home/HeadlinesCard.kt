package com.opensmarthome.speaker.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opensmarthome.speaker.R
import com.opensmarthome.speaker.tool.info.NewsItem
import com.opensmarthome.speaker.ui.theme.SpeakerSurfaceVariant
import com.opensmarthome.speaker.ui.theme.SpeakerTextPrimary
import com.opensmarthome.speaker.ui.theme.SpeakerTextSecondary

/**
 * Horizontal news tile strip shown under the clock on a tablet dashboard.
 * Each tile is a fixed-width card so the row keeps a consistent rhythm
 * independent of title length. Summaries are truncated to two lines.
 *
 * Empty success list (feed fetched fine but returned 0 items) still
 * renders nothing — this is the legitimate "no news" state. Loading
 * and Error states are modelled via [HeadlinesCardLoading] /
 * [HeadlinesCardError]; the caller chooses which composable to render
 * based on [BriefingState].
 */
@Composable
fun HeadlinesCard(
    headlines: List<NewsItem>,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.briefing_headlines_title),
) {
    if (headlines.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Article,
                contentDescription = null,
                tint = SpeakerTextSecondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = SpeakerTextSecondary,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(headlines, key = { it.link.ifBlank { it.title } }) { item ->
                HeadlineTile(item)
            }
        }
    }
}

/**
 * Loading skeleton for the headlines strip. Same header row as
 * [HeadlinesCard] so the transition to the populated state is calm.
 */
@Composable
fun HeadlinesCardLoading(
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.briefing_headlines_title),
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Article,
                contentDescription = null,
                tint = SpeakerTextSecondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = SpeakerTextSecondary,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.briefing_headlines_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = SpeakerTextSecondary,
        )
    }
}

/**
 * Error variant for the headlines strip. Tells the user explicitly that
 * fetch failed — no more silent disappearance of the tile.
 */
@Composable
fun HeadlinesCardError(
    kind: BriefingState.Error.Kind,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.briefing_headlines_title),
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Article,
                contentDescription = null,
                tint = SpeakerTextSecondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = SpeakerTextSecondary,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .background(SpeakerSurfaceVariant, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = errorIcon(kind),
                contentDescription = null,
                tint = SpeakerTextSecondary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.size(10.dp))
            Column {
                Text(
                    text = stringResource(R.string.briefing_headlines_error),
                    style = MaterialTheme.typography.titleSmall,
                    color = SpeakerTextPrimary,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(errorDetailStringRes(kind)),
                    style = MaterialTheme.typography.bodySmall,
                    color = SpeakerTextSecondary,
                )
                if (onRetry != null) {
                    TextButton(onClick = onRetry) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(text = stringResource(R.string.briefing_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun HeadlineTile(item: NewsItem) {
    Column(
        modifier = Modifier
            .width(280.dp)
            .background(SpeakerSurfaceVariant, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            color = SpeakerTextPrimary,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.summary.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.summary,
                style = MaterialTheme.typography.bodySmall,
                color = SpeakerTextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun errorIcon(kind: BriefingState.Error.Kind): ImageVector = when (kind) {
    BriefingState.Error.Kind.Network -> Icons.Filled.CloudOff
    BriefingState.Error.Kind.Parse,
    BriefingState.Error.Kind.Unknown -> Icons.Filled.ErrorOutline
}

private fun errorDetailStringRes(kind: BriefingState.Error.Kind): Int = when (kind) {
    BriefingState.Error.Kind.Network -> R.string.briefing_error_network
    BriefingState.Error.Kind.Parse -> R.string.briefing_error_parse
    BriefingState.Error.Kind.Unknown -> R.string.briefing_error_unknown
}
