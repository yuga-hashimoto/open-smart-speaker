package com.opensmarthome.speaker.assistant.model

sealed class ConversationState {
    data object Idle : ConversationState()
    data object Listening : ConversationState()
    data object Thinking : ConversationState()
    data object Speaking : ConversationState()
    data class Error(val message: String) : ConversationState()
}
