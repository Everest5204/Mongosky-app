package com.mongosky.app.ai

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import org.json.JSONObject

internal class AiHttp(
    private val baseUrl: HttpUrl = "https://api.mongosky.com/".toHttpUrl(),
    private val restClient: OkHttpClient = rest,
    private val streamClient: OkHttpClient = streaming
) : AiJsonTransport {
    override suspend fun request(token: String,method: String,path: String): JSONObject = withContext(Dispatchers.IO) {
        checked {
            val request = requestBuilder(token,path).header("Accept","application/json").method(method,null).build()
            consume(restClient.newCall(request)) { response ->
                requireSuccess(response)
                requireType(response,"application/json")
                AiJson.objectValue(readBody(response,MAX_JSON_BYTES))
            }
        }
    }
    // Called by the stream's IO flow; emit remains in the same coroutine context.
    suspend fun stream(token: String,conversationId: String?,message: String,readStream: suspend (BufferedSource) -> Unit) {
        checked {
            val clean = AiPolicy.cleanDraft(message) ?: throw AiException("Enter a message of up to 4,000 characters.",AiFailure.REJECTED)
            if (conversationId != null && !AiPolicy.validId(conversationId)) throw AiException("This chat is unavailable.",AiFailure.MISSING)
            val body = JSONObject().put("conversation_id",conversationId.orEmpty()).put("message",clean)
                .toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = requestBuilder(token,"/api/ai/messages").header("Accept","text/event-stream").post(body).build()
            consume(streamClient.newCall(request)) { response ->
                requireSuccess(response)
                requireType(response,"text/event-stream")
                val source = response.body?.source() ?: throw invalidAi()
                readStream(source)
            }
        }
    }
    private fun requestBuilder(token: String,path: String): Request.Builder {
        if (!AiPolicy.validToken(token)) throw AiException("Please sign in to use Mongosky AI.",AiFailure.AUTH,401)
        require(path.startsWith("/api/ai/"))
        return Request.Builder().url(baseUrl.resolve(path) ?: throw invalidAi())
            .header("Authorization","Bearer $token").header("Cache-Control","no-store")
    }
    private suspend fun <T> consume(call: Call,block: suspend (Response) -> T): T = coroutineScope {
        // Cancels the socket even while this IO coroutine is blocked reading a chunk.
        val cancellation = launch(start=CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { call.cancel() }
        }
        try { awaitResponse(call).use { response -> ensureActive(); block(response) } }
        finally { cancellation.cancel(); call.cancel() }
    }
    private suspend fun awaitResponse(call: Call): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call,e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call,response: Response) {
                if (!continuation.isActive) response.close()
                else continuation.resume(response) { _, abandoned, _ -> abandoned.close() }
            }
        })
    }
    private suspend fun readBody(response: Response,max: Int): String {
        val body = response.body ?: throw invalidAi()
        if (body.contentLength() > max) throw invalidAi()
        val output = ByteArrayOutputStream()
        body.byteStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                currentCoroutineContext().ensureActive()
                val n = input.read(buffer); if (n < 0) break
                if (output.size() + n > max) throw invalidAi()
                output.write(buffer,0,n)
            }
        }
        return output.toString("UTF-8")
    }
    private suspend fun requireSuccess(response: Response) {
        if (response.isSuccessful) return
        val failure = when(response.code) {
            401 -> AiFailure.AUTH; 404 -> AiFailure.MISSING; 429 -> AiFailure.RATE_LIMIT
            in 500..599 -> AiFailure.SERVER; else -> AiFailure.REJECTED
        }
        val fallback = when(failure) {
            AiFailure.AUTH -> "Please sign in again to use Mongosky AI."
            AiFailure.MISSING -> "This chat is no longer available."
            AiFailure.RATE_LIMIT -> "Too many requests. Please wait a moment."
            AiFailure.SERVER -> "Mongosky AI is temporarily unavailable."
            else -> "The AI request could not be completed."
        }
        val detail = try { AiJson.errorMessage(AiJson.objectValue(readBody(response,8192))) } catch (_: AiException) { null }
        throw AiException(if(failure==AiFailure.AUTH) fallback else detail ?: fallback,failure,response.code)
    }
    private fun requireType(response: Response,type: String) {
        if (response.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase() != type) throw invalidAi()
    }
    private suspend inline fun <T> checked(crossinline block: suspend () -> T): T = try { block() }
        catch (cancel: CancellationException) { throw cancel }
        catch (error: AiException) { throw error }
        catch (error: SocketTimeoutException) { currentCoroutineContext().ensureActive(); throw AiException("The AI response timed out. Reload the chat before sending again.",AiFailure.TIMEOUT,cause=error) }
        catch (error: IOException) { currentCoroutineContext().ensureActive(); throw AiException("Connection interrupted. Reload the chat to check saved messages.",AiFailure.NETWORK,cause=error) }
    companion object {
        private const val MAX_JSON_BYTES = 8 * 1024 * 1024
        private val streaming = OkHttpClient.Builder().connectTimeout(12,TimeUnit.SECONDS).writeTimeout(15,TimeUnit.SECONDS)
            .readTimeout(100,TimeUnit.SECONDS).callTimeout(130,TimeUnit.SECONDS).retryOnConnectionFailure(false)
            .followRedirects(false).followSslRedirects(false).cache(null).build()
        private val rest = streaming.newBuilder().readTimeout(25,TimeUnit.SECONDS).callTimeout(35,TimeUnit.SECONDS).build()
    }
}
internal fun invalidAi(cause: Throwable? = null) = AiException("Could not read the AI response. Please reload the chat.",AiFailure.INVALID,cause=cause)
