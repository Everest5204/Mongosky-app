package com.mongosky.app.ai

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.input.TextFieldValue
import java.time.Instant

internal enum class AiRole(val wire: String) { USER("user"), ASSISTANT("assistant") }
internal enum class AiSource(val wire: String) { USER("user"), KNOWLEDGE("knowledge"), GEMINI("gemini") }
internal enum class AiDelivery { SAVED, PENDING, STREAMING, INTERRUPTED }

@Immutable
internal data class AiChat(val id: String, val title: String, val createdAt: Instant, val updatedAt: Instant)

@Immutable
internal data class AiMessage(
    val id: String, val conversationId: String, val role: AiRole, val content: String,
    val source: AiSource, val createdAt: Instant, val key: String = id,
    val delivery: AiDelivery = AiDelivery.SAVED
)

internal sealed interface AiFrame {
    data class Conversation(val chat: AiChat) : AiFrame
    data class UserMessage(val message: AiMessage) : AiFrame
    data class Delta(val text: String) : AiFrame
    data class Done(val chat: AiChat, val message: AiMessage) : AiFrame
}
internal enum class AiFailure { AUTH, NETWORK, TIMEOUT, REJECTED, MISSING, RATE_LIMIT, SERVER, INVALID, INTERRUPTED }
internal class AiException(message: String, val failure: AiFailure, val status: Int = 0, cause: Throwable? = null) : Exception(message,cause)
internal enum class AiErrorAction { CHATS, MESSAGES, NONE }
internal data class AiError(val message: String, val action: AiErrorAction = AiErrorAction.NONE)

internal data class AiState(
    val chats: List<AiChat> = emptyList(), val activeChatId: String? = null,
    val messages: List<AiMessage> = emptyList(), val input: TextFieldValue = TextFieldValue(),
    val inputRevision: Long = 0, val threadVersion: Long = 0,
    val ready: Boolean = false, val expired: Boolean = false, val chatsLoaded: Boolean = false,
    val chatsLoading: Boolean = false, val messagesLoading: Boolean = false,
    val messagesRefreshing: Boolean = false, val sending: Boolean = false,
    val deletingId: String? = null, val clearing: Boolean = false, val error: AiError? = null
) {
    val draft get() = input.text
    val mutating get() = deletingId != null || clearing
    val canSend get() = ready && !expired && !sending && !messagesLoading && !messagesRefreshing && !mutating && AiPolicy.cleanDraft(draft) != null
}

internal enum class AiThemeChoice {
    SYSTEM, LIGHT, DARK;
    fun resolve(systemDark: Boolean) = when(this) { SYSTEM -> systemDark; LIGHT -> false; DARK -> true }
}
