package com.mongosky.app.saveditems

import com.mongosky.app.home.HomeHttp
import com.mongosky.app.network.PostTransport
import com.mongosky.app.network.invalidPostResponse
import com.mongosky.app.post.FeedApiException
import com.mongosky.app.post.FeedApiFailure
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal class SavedHttp(private val client: OkHttpClient = shared,baseUrl: String = "https://api.mongosky.com/") : PostTransport {
    private val base = baseUrl.toHttpUrl()
    private val streams = client.newBuilder().callTimeout(0,TimeUnit.SECONDS).build()
    private fun build(token: String,method: String,path: String,query: Map<String,String>,body: JSONObject?): Request {
        if (token.isBlank() || token.any { it <= ' ' || it >= '\u007f' }) throw FeedApiException("Please sign in again.",FeedApiFailure.SESSION_EXPIRED)
        require(method == "GET" || method == "PUT" || method == "POST")
        val url = base.resolve(path)?.newBuilder() ?: throw invalidPostResponse();query.forEach { (k,v) -> url.addQueryParameter(k,v) }
        return Request.Builder().url(url.build()).header("Authorization","Bearer $token").header("Accept","application/json")
            .header("Cache-Control","no-store").method(method,body?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType())).build()
    }
    override suspend fun request(token: String,method: String,path: String,query: Map<String,String>,body: JSONObject?): JSONObject = withContext(Dispatchers.IO) {
        val call = client.newCall(build(token,method,path,query,body))
        val text = suspendCancellableCoroutine<String> { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call,e: IOException) { if (continuation.isActive) continuation.resumeWithException(network(e)) }
                override fun onResponse(call: Call,response: Response) { response.use { try {
                    if (!it.isSuccessful) throw error(it.code,path)
                    val body = it.body ?: throw invalidPostResponse();if (body.contentLength() > MAX_BYTES) throw invalidPostResponse()
                    val source = body.source();val buffer = okio.Buffer()
                    while (!call.isCanceled()) { val n = source.read(buffer,8192);if (n < 0) break;if (buffer.size > MAX_BYTES) throw invalidPostResponse() }
                    if (continuation.isActive) continuation.resume(buffer.readUtf8())
                } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) } } }
            })
        }
        try { JSONObject(text) } catch (e: Exception) { throw invalidPostResponse(e) }
    }
    fun changes(token: String,owner: String): Flow<Long> = callbackFlow {
        val call = streams.newCall(build(token,"GET","/api/saved-items/stream",emptyMap(),null).newBuilder().header("Accept","text/event-stream").build())
        call.enqueue(object : Callback {
            override fun onFailure(call: Call,e: IOException) { close(network(e)) }
            override fun onResponse(call: Call,response: Response) { response.use { try {
                if (!it.isSuccessful) throw error(it.code,"/api/saved-items/stream")
                if (it.header("Content-Type")?.substringBefore(';')?.trim() != "text/event-stream") throw invalidPostResponse()
                val source = it.body?.source() ?: throw invalidPostResponse();var event = "";var data = ""
                while (!call.isCanceled() && !source.exhausted()) {
                    val line = source.readUtf8LineStrict(4096)
                    when {
                        line.isEmpty() -> { if (event == "saved.changed" && data.isNotEmpty()) {
                            if (JSONObject(data).optString("owner_id") != owner) throw invalidPostResponse();val revision = JSONObject(data).getLong("revision");require(revision >= 0);trySend(revision)
                        };event = "";data = "" }
                        line.startsWith("event:") -> event = line.substringAfter(':').trim()
                        line.startsWith("data:") -> { if (data.isNotEmpty()) throw invalidPostResponse();data = line.substringAfter(':').trim() }
                    }
                };close()
            } catch (e: Exception) { close(e) } } }
        });awaitClose { call.cancel() }
    }.buffer(Channel.CONFLATED)
    private companion object {
        const val MAX_BYTES = 1_048_576L
        val shared = OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(25,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS)
            .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()
        fun network(e: IOException) = FeedApiException(if (e is SocketTimeoutException) "The request timed out. Please try again." else "Check your connection and try again.",
            if (e is SocketTimeoutException) FeedApiFailure.TIMEOUT else FeedApiFailure.NETWORK,cause = e)
        fun error(status: Int,path: String) = when {
            status == 404 -> FeedApiException(if (path.endsWith("/post") || Regex("/api/saved-items/[a-f0-9]{24}").matches(path)) "This saved post is no longer available." else "Saved Items is temporarily unavailable. Please try again later.",
                if (path.endsWith("/post") || Regex("/api/saved-items/[a-f0-9]{24}").matches(path)) FeedApiFailure.REQUEST_REJECTED else FeedApiFailure.SERVER,status)
            status == 409 -> FeedApiException("Saved status changed. Refresh and try again.",FeedApiFailure.REQUEST_REJECTED,status)
            else -> HomeHttp.error(status)
        }
    }
}
