package com.opensmarthome.speaker.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opensmarthome.speaker.assistant.proactive.Suggestion

/**
 * Ambient card for a proactive suggestion. Tap "Yes" triggers the
 * suggested action via [onAccept]; tap "Not now" dismisses via [onDismiss].
 *
 * Kept stateless — SuggestionState in ViewModel drives which one to show.
 */
@Composable
fun SuggestionBubble(
    suggestion: Suggestion,
    onAccept: (Suggestion) -> Unit,
    onDismiss: (Suggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = suggestion.message,
                style = MaterialTheme.typography.bodyLarge
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { onDismiss(suggestion) }) {
                    Text("Not now")
                }
                if (suggestion.suggestedAction != null) {
                    TextButton(onClick = { onAccept(suggestion) }) {
                        Text("Yes")
                    }
                }
            }
        }
    }
}
