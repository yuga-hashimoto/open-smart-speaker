package com.opendash.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opendash.app.assistant.model.AssistantMessage

@Composable
fun ChatMessageItem(message: AssistantMessage, modifier: Modifier = Modifier) {
    val isUser = message is AssistantMessage.User
    val horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = horizontalArrangement
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = when (message) {
                        is AssistantMessage.User -> message.content
                        is AssistantMessage.Assistant -> message.content
                        is AssistantMessage.System -> message.content
                        is AssistantMessage.ToolCallResult -> if (message.isError) "Error: ${message.result}" else message.result
                        is AssistantMessage.Delta -> message.contentDelta
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
