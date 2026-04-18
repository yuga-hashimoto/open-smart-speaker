package com.opendash.app.assistant.session

import com.opendash.app.assistant.model.AssistantMessage
import com.opendash.app.assistant.model.AssistantSession
import com.opendash.app.assistant.provider.AssistantProvider
import com.opendash.app.data.db.MessageDao
import com.opendash.app.data.db.MessageEntity
import com.opendash.app.data.db.SessionDao
import com.opendash.app.data.db.SessionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao
) {
    private val _currentSession = MutableStateFlow<AssistantSession?>(null)
    val currentSession: StateFlow<AssistantSession?> = _currentSession.asStateFlow()

    private val _messages = MutableStateFlow<List<AssistantMessage>>(emptyList())
    val messages: StateFlow<List<AssistantMessage>> = _messages.asStateFlow()

    suspend fun createSession(provider: AssistantProvider): AssistantSession {
        val session = provider.startSession()
        sessionDao.insert(
            SessionEntity(
                id = session.id,
                providerId = session.providerId,
                createdAt = session.createdAt
            )
        )
        _currentSession.value = session
        _messages.value = emptyList()
        Timber.d("Created session: ${session.id} with provider: ${session.providerId}")
        return session
    }

    suspend fun addMessage(message: AssistantMessage) {
        _messages.value = _messages.value + message
        val session = _currentSession.value ?: return
        messageDao.insert(
            MessageEntity(
                id = message.id,
                sessionId = session.id,
                role = message.toRole(),
                content = message.toContent(),
                timestamp = message.timestamp
            )
        )
    }

    suspend fun loadSession(sessionId: String): AssistantSession? {
        val entity = sessionDao.getById(sessionId) ?: return null
        val session = AssistantSession(
            id = entity.id,
            providerId = entity.providerId,
            createdAt = entity.createdAt
        )
        _currentSession.value = session
        val messageEntities = messageDao.getBySessionId(sessionId)
        _messages.value = messageEntities.map { it.toAssistantMessage() }
        return session
    }

    suspend fun endSession(provider: AssistantProvider) {
        val session = _currentSession.value ?: return
        provider.endSession(session)
        _currentSession.value = null
        _messages.value = emptyList()
    }
}

private fun AssistantMessage.toRole(): String = when (this) {
    is AssistantMessage.User -> "user"
    is AssistantMessage.Assistant -> "assistant"
    is AssistantMessage.System -> "system"
    is AssistantMessage.ToolCallResult -> "tool"
    is AssistantMessage.Delta -> "delta"
}

private fun AssistantMessage.toContent(): String = when (this) {
    is AssistantMessage.User -> content
    is AssistantMessage.Assistant -> content
    is AssistantMessage.System -> content
    is AssistantMessage.ToolCallResult -> result
    is AssistantMessage.Delta -> contentDelta
}

private fun MessageEntity.toAssistantMessage(): AssistantMessage = when (role) {
    "user" -> AssistantMessage.User(id = id, timestamp = timestamp, content = content)
    "assistant" -> AssistantMessage.Assistant(id = id, timestamp = timestamp, content = content)
    "system" -> AssistantMessage.System(id = id, timestamp = timestamp, content = content)
    "tool" -> AssistantMessage.ToolCallResult(id = id, timestamp = timestamp, callId = "", result = content)
    else -> AssistantMessage.System(id = id, timestamp = timestamp, content = content)
}
