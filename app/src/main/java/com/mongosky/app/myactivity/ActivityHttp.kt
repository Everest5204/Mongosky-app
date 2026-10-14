package com.mongosky.app.myactivity

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
import org.json.JSONObject

internal class ActivityHttp(private val client: OkHttpClient = shared, baseUrl: String = "https://api.mongosky.com/") : PostTransport {
    private val base = baseUrl.toHttpUrl()
    private val streamClient = client.newBuilder().callTimeout(0, TimeUnit.SECONDS).build()
    private fun request(token: String, path: String, query: Map<String, String>): Request {
        if (token.isBlank() || token.any { it <= ' ' || it >= '\u007f' }) throw FeedApiException("Please sign in again.", FeedApiFailure.SESSION_EXPIRED)
        val url = base.resolve(path)?.newBuilder() ?: throw invalidPostResponse()
        query.forEach { (k, v) -> url.addQueryParameter(k, v) }
        return Request.Builder().url(url.build()).header("Authorization", "Bearer $token")
            .header("Cache-Control", "no-store").header("Accept", "application/json").get().build()
    }
    override suspend fun request(token: String, method: String, path: String, query: Map<String, String>, body: JSONObject?): JSONObject = withContext(Dispatchers.IO) {
        require(method == "GET" && body == null)
        val call = client.newCall(request(token, path, query))
        val text = suspendCancellableCoroutine<String> { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(networkError(e)) }
                override fun onResponse(call: Call, response: Response) {
                    response.use { try {
                        if (!it.isSuccessful) throw activityError(it.code,path)
                        val body = it.body ?: throw invalidPostResponse()
                        if (body.contentLength() > MAX_BYTES) throw invalidPostResponse()
                        val source = body.source(); val buffer = okio.Buffer()
                        while (!call.isCanceled()) {
                            val n = source.read(buffer, 8192)
                            if (n < 0) break
                            if (buffer.size > MAX_BYTES) throw invalidPostResponse()
                        }
                        if (continuation.isActive) continuation.resume(buffer.readUtf8())
                    } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) } }
                }
            })
        }
        try { JSONObject(text) } catch (e: Exception) { throw invalidPostResponse(e) }
    }

    fun changes(token: String, owner: String): Flow<Unit> = callbackFlow {
        val call = streamClient.newCall(request(token, "/api/my-activity/stream", emptyMap()).newBuilder()
            .header("Accept", "text/event-stream").build())
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { close(networkError(e)) }
            override fun onResponse(call: Call, response: Response) {
                response.use { try {
                    if (!it.isSuccessful) throw activityError(it.code,"/api/my-activity/stream")
                    if (it.header("Content-Type")?.substringBefore(';')?.trim() != "text/event-stream") throw invalidPostResponse()
                    val source = it.body?.source() ?: throw invalidPostResponse()
                    var event = ""; var data = ""
                    while (!call.isCanceled() && !source.exhausted()) {
                        val line = source.readUtf8LineStrict(4096)
                        when {
                            line.isEmpty() -> {
                                if (event == "activity.changed" && data.isNotEmpty()) {
                                    if (JSONObject(data).optString("owner_id") != owner) throw invalidPostResponse()
                                    trySend(Unit)
                                }
                                event = ""; data = ""
                            }
                            line.startsWith("event:") -> event = line.substringAfter(':').trim()
                            line.startsWith("data:") -> { if (data.isNotEmpty()) throw invalidPostResponse(); data = line.substringAfter(':').trim() }
                        }
                    }
                    close()
                } catch (e: Exception) { close(e) } }
            }
        })
        awaitClose { call.cancel() }
    }.buffer(Channel.CONFLATED)

    private companion object {
        const val MAX_BYTES = 1_048_576L
        val shared = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS).retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()
        fun activityError(status: Int,path: String) = if (status == 404) FeedApiException(
            if (path.endsWith("/target")) "This activity is no longer available." else "My Activity is temporarily unavailable. Please try again later.",
            if (path.endsWith("/target")) FeedApiFailure.REQUEST_REJECTED else FeedApiFailure.SERVER,status
        ) else HomeHttp.error(status)
        fun networkError(e: IOException) = FeedApiException(if (e is SocketTimeoutException) "The request timed out. Please try again."
            else "Check your connection and try again.", if (e is SocketTimeoutException) FeedApiFailure.TIMEOUT else FeedApiFailure.NETWORK, cause = e)
    }
}
