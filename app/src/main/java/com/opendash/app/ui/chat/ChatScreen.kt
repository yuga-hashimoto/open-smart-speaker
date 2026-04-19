package com.opendash.app.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opendash.app.assistant.model.ConversationState

@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val state by viewModel.conversationState.collectAsState()
    val streaming by viewModel.streamingContent.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(top = 8.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                ChatMessageItem(message)
            }

            if (streaming.isNotBlank()) {
                item {
                    Surface(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = streaming,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }

        if (state is ConversationState.Thinking || state is ConversationState.Listening) {
            LinearProgressIndicator(modifier = Modifier.padding(horizontal = 16.dp))
        }

        if (state is ConversationState.Error) {
            val errorMsg = (state as ConversationState.Error).message
            val isNoProvider = errorMsg.contains("No available") || errorMsg.contains("not found")
            Text(
                text = if (isNoProvider) {
                    "AI provider not configured. Go to Settings to set up On-Device LLM, OpenClaw, or external LLM endpoint."
                } else {
                    errorMsg
                },
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (messages.isEmpty() && state is ConversationState.Idle) {
            Text(
                text = "Say \"Dash\" or tap the mic to start talking.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        ChatInputBar(
            onSend = { viewModel.sendMessage(it) },
            onMicClick = { viewModel.startVoiceInput() }
        )
    }
}
