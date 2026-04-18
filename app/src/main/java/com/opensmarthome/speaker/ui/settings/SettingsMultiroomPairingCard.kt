package com.opensmarthome.speaker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opensmarthome.speaker.R
import com.opensmarthome.speaker.multiroom.PairingFingerprint

/**
 * Card under the Multi-room section showing the 4-word pairing phrase
 * derived from the current shared secret. Users read the phrase aloud to
 * verify the same secret is entered on every speaker without sharing the
 * secret itself.
 *
 * If the secret is blank, shows `(no secret set)` instead of a fingerprint
 * of the empty string.
 */
@Composable
fun SettingsMultiroomPairingCard(
    secret: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val phrase = remember(secret) {
        if (secret.isBlank()) emptyList()
        else runCatching { PairingFingerprint.forSecret(secret, context) }.getOrDefault(emptyList())
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.pairing_phrase_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (secret.isBlank() || phrase.isEmpty()) {
                Text(
                    text = stringResource(R.string.pairing_phrase_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = phrase.joinToString(separator = "  "),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.pairing_phrase_read_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
