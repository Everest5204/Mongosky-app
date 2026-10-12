package com.mongosky.app.ai

import java.io.EOFException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject

internal interface AiEvents { fun frames(token: String,conversationId: String?,message: String): Flow<AiFrame> }

internal class AiStream(private val http: AiHttp = AiHttp()) : AiEvents {
    override fun frames(token: String,conversationId: String?,message: String): Flow<AiFrame> = flow {
        val protocol = AiProtocol(conversationId,AiPolicy.cleanDraft(message) ?: throw invalidAi())
        val sse = AiSseDecoder()
        http.stream(token,conversationId,message) { source ->
            var bytes = 0L
            while (!protocol.completed) {
                currentCoroutineContext().ensureActive()
                val line = try { source.readUtf8LineStrict(AiSseDecoder.MAX_LINE_BYTES.toLong()) }
                    catch (_: EOFException) {
                        if (source.buffer.size > AiSseDecoder.MAX_LINE_BYTES) throw invalidAi()
                        val tail = if (source.buffer.size > 0) source.readUtf8() else null
                        if (tail != null) sse.line(tail)?.let { block -> protocol.accept(block)?.let { emit(it) } }
                        sse.finish()?.let { block -> protocol.accept(block)?.let { emit(it) } }
                        break
                    }
                bytes += line.length + 1L
                if (bytes > 2L * 1024 * 1024) throw invalidAi()
                sse.line(line)?.let { block -> protocol.accept(block)?.let { emit(it) } }
            }
            if (!protocol.completed) throw AiException("The AI response ended unexpectedly. Reload the chat to check saved messages.",AiFailure.INTERRUPTED)
        }
    }.flowOn(Dispatchers.IO)
}

internal data class AiSseBlock(val event: String,val data: String)
internal class AiSseDecoder {
    private var event = "message"
    private val data = StringBuilder()
    private var hasData = false
    private var first = true
    fun line(raw: String): AiSseBlock? {
        val line = if (first) raw.removePrefix("\uFEFF").also { first=false } else raw
        if (line.length > MAX_LINE_BYTES) throw invalidAi()
        if (line.isEmpty()) return finish()
        if (line.startsWith(':')) return null
        val key = line.substringBefore(':'); val value = line.substringAfter(':',"").removePrefix(" ")
        when(key) {
            "event" -> { if(value.length>128) throw invalidAi(); event=value }
            "data" -> {
                if(hasData) data.append('\n')
                data.append(value); hasData=true
                if(data.length>MAX_LINE_BYTES) throw invalidAi()
            }
        }
        return null
    }
    fun finish(): AiSseBlock? {
        val result = if(hasData) AiSseBlock(event,data.toString()) else null
        event="message";data.setLength(0);hasData=false
        return result
    }
    companion object { const val MAX_LINE_BYTES = 256 * 1024 }
}

internal class AiProtocol(private val expectedChat: String?,private val expectedMessage: String) {
    private var chat: AiChat? = null
    private var user: AiMessage? = null
    private var responseChars = 0
    var completed = false; private set
    fun accept(block: AiSseBlock): AiFrame? {
        if(completed) throw invalidAi()
        if(block.event !in setOf("conversation","user_message","delta","done","error")) return null
        val raw = AiJson.objectValue(block.data)
        return when(block.event) {
            "conversation" -> {
                val incoming = AiJson.chat(AiJson.requiredObject(raw,"conversation"))
                if(chat!=null || user!=null || (expectedChat!=null && incoming.id!=expectedChat)) throw invalidAi()
                chat=incoming;AiFrame.Conversation(incoming)
            }
            "user_message" -> {
                val current = chat ?: throw invalidAi()
                val incoming = AiJson.message(AiJson.requiredObject(raw,"message"),current.id)
                if(user!=null || incoming.role!=AiRole.USER || incoming.content!=expectedMessage) throw invalidAi()
                user=incoming;AiFrame.UserMessage(incoming)
            }
            "delta" -> {
                if(user==null) throw invalidAi()
                val text = AiJson.text(raw,"text",AiPolicy.MAX_RESPONSE_CHARS)
                responseChars += text.length
                if(responseChars>AiPolicy.MAX_RESPONSE_CHARS) throw invalidAi()
                AiFrame.Delta(text).takeIf { text.isNotEmpty() }
            }
            "done" -> {
                val current = chat ?: throw invalidAi(); val savedUser = user ?: throw invalidAi()
                val incomingChat = AiJson.chat(AiJson.requiredObject(raw,"conversation"))
                val answer = AiJson.message(AiJson.requiredObject(raw,"message"),current.id)
                if(incomingChat.id!=current.id || incomingChat.updatedAt.isBefore(current.updatedAt) ||
                    answer.role!=AiRole.ASSISTANT || answer.id==savedUser.id) throw invalidAi()
                completed=true;AiFrame.Done(incomingChat,answer)
            }
            else -> throw AiException(AiJson.errorMessage(raw),AiFailure.SERVER)
        }
    }
}
