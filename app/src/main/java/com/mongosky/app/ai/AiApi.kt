package com.mongosky.app.ai

import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal interface AiActions {
    suspend fun chats(token: String): List<AiChat>
    suspend fun messages(token: String,conversationId: String): List<AiMessage>
    suspend fun delete(token: String,conversationId: String)
    suspend fun clear(token: String)
}
internal interface AiJsonTransport {
    suspend fun request(token: String,method: String,path: String): JSONObject
}
internal class AiApi(private val http: AiJsonTransport = AiHttp()) : AiActions {
    override suspend fun chats(token: String) = withContext(Dispatchers.IO) { AiJson.chats(http.request(token,"GET","/api/ai/chats")) }
    override suspend fun messages(token: String,conversationId: String): List<AiMessage> {
        requireId(conversationId)
        return withContext(Dispatchers.IO) { AiJson.messages(http.request(token,"GET","/api/ai/chats/$conversationId/messages"),conversationId) }
    }
    override suspend fun delete(token: String,conversationId: String) {
        requireId(conversationId); AiJson.deleted(http.request(token,"DELETE","/api/ai/chats/$conversationId"))
    }
    override suspend fun clear(token: String) { AiJson.deleted(http.request(token,"DELETE","/api/ai/chats")) }
    private fun requireId(id: String) { if (!AiPolicy.validId(id)) throw AiException("This chat is unavailable.",AiFailure.MISSING) }
}
