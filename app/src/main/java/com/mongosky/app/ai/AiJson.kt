package com.mongosky.app.ai

import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

internal object AiJson {
    fun objectValue(text: String): JSONObject = parse { JSONObject(text) }
    fun chat(raw: JSONObject): AiChat = parse {
        val created = time(raw,"created_at"); val updated = time(raw,"updated_at")
        require(!updated.isBefore(created))
        AiChat(id(raw,"id"),text(raw,"title",512).also { require(it.isNotBlank()) },created,updated)
    }
    fun message(raw: JSONObject, conversationId: String? = null): AiMessage = parse {
        val role = AiRole.entries.singleOrNull { it.wire == text(raw,"role",20) } ?: error("role")
        val source = AiSource.entries.singleOrNull { it.wire == text(raw,"source",20) } ?: error("source")
        require((role == AiRole.USER && source == AiSource.USER) || (role == AiRole.ASSISTANT && source != AiSource.USER))
        val conversation = id(raw,"conversation_id")
        require(conversationId == null || conversation == conversationId)
        AiMessage(id(raw,"id"),conversation,role,text(raw,"content",AiPolicy.MAX_RESPONSE_CHARS).also { require(it.isNotBlank()) },source,time(raw,"created_at"))
    }
    fun chats(raw: JSONObject): List<AiChat> = parse {
        val rows = array(raw,"chats",AiPolicy.MAX_CHATS).let { list -> (0 until list.length()).map { chat(list.getJSONObject(it)) } }
        require(rows.map { it.id }.distinct().size == rows.size)
        AiPolicy.sortedChats(rows)
    }
    fun messages(raw: JSONObject, conversationId: String): List<AiMessage> = parse {
        require(AiPolicy.validId(conversationId))
        val rows = array(raw,"messages",AiPolicy.MAX_HISTORY).let { list -> (0 until list.length()).map { message(list.getJSONObject(it),conversationId) } }
        require(rows.map { it.id }.distinct().size == rows.size)
        require(rows.sumOf { it.content.length.toLong() } <= 2L * 1024 * 1024)
        rows.sortedWith(compareBy<AiMessage> { it.createdAt }.thenBy { it.id })
    }
    fun deleted(raw: JSONObject) = parse { require(raw.get("deleted") is Boolean && raw.getBoolean("deleted")); Unit }
    fun requiredObject(raw: JSONObject,key: String): JSONObject = parse { raw.getJSONObject(key) }
    fun text(raw: JSONObject,key: String,max: Int): String = parse {
        (raw.get(key) as? String ?: error("text")).also { require(it.length <= max && AiPolicy.validUnicode(it)) }
    }
    fun errorMessage(raw: JSONObject) = parse { text(raw,"error",1000).trim().also { require(it.isNotEmpty()) } }
    private fun id(raw: JSONObject,key: String) = text(raw,key,24).also { require(AiPolicy.validId(it)) }
    private fun time(raw: JSONObject,key: String) = Instant.parse(text(raw,key,64))
    private fun array(raw: JSONObject,key: String,max: Int): JSONArray = raw.getJSONArray(key).also { require(it.length() <= max) }
    private inline fun <T> parse(block: () -> T): T = try { block() }
        catch (error: AiException) { throw error }
        catch (error: Exception) { throw AiException("Could not read the AI response. Please reload the chat.",AiFailure.INVALID,cause=error) }
}
