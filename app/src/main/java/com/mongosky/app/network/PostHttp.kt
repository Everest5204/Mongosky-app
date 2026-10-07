package com.mongosky.app.network

import com.mongosky.app.comments.CommentLove
import com.mongosky.app.comments.CommentPage
import com.mongosky.app.comments.CreatedComment
import com.mongosky.app.comments.DeletedComment
import com.mongosky.app.comments.PostComment
import com.mongosky.app.post.FeedApiException
import com.mongosky.app.post.FeedApiFailure
import com.mongosky.app.post.FeedAuthor
import com.mongosky.app.reactions.PostReaction
import com.mongosky.app.reactions.ReactionPerson
import com.mongosky.app.reactions.ReactionSummary
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

internal interface PostTransport {
    suspend fun request(token: String, method: String, path: String, query: Map<String, String> = emptyMap(), body: JSONObject? = null): JSONObject
}

/** One cancellable client. Mutations are never automatically replayed. */
internal class PostHttp : PostTransport {
    override suspend fun request(token: String, method: String, path: String, query: Map<String, String>, body: JSONObject?): JSONObject =
        withContext(Dispatchers.IO) {
            ensureActive()
            if (token.isBlank() || token.any { it <= ' ' || it >= '\u007f' }) {
                throw FeedApiException("Please sign in again.", FeedApiFailure.SESSION_EXPIRED)
            }
            val url = ("https://api.mongosky.com$path").toHttpUrl().newBuilder()
            query.forEach { (key, value) -> url.addQueryParameter(key, value) }
            val request = Request.Builder().url(url.build()).header("Authorization", "Bearer $token")
                .header("Accept", "application/json").header("Cache-Control", "no-store")
                .method(method, body?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType())).build()
            try {
                val text = suspendCancellableCoroutine<String> { continuation ->
                    val call = client.newCall(request)
                    continuation.invokeOnCancellation { call.cancel() }
                    call.enqueue(object : Callback {
                        override fun onFailure(call: Call, error: IOException) {
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }
                        override fun onResponse(call: Call, response: Response) {
                            response.use {
                                try {
                                    if (!continuation.isActive) return
                                    if (!response.isSuccessful) throw httpError(response.code)
                                    val stream = response.body ?: throw invalidPostResponse()
                                    if (stream.contentLength() > MAX_BYTES) throw invalidPostResponse()
                                    val output = ByteArrayOutputStream()
                                    stream.byteStream().use { input ->
                                        val buffer = ByteArray(8192)
                                        while (continuation.isActive) {
                                            val n = input.read(buffer)
                                            if (n < 0) break
                                            if (output.size() + n > MAX_BYTES) throw invalidPostResponse()
                                            output.write(buffer, 0, n)
                                        }
                                    }
                                    if (continuation.isActive) continuation.resume(output.toString("UTF-8"))
                                } catch (error: Exception) {
                                    if (continuation.isActive) continuation.resumeWithException(error)
                                }
                            }
                        }
                    })
                }
                ensureActive()
                try { JSONObject(text) } catch (error: Exception) { throw invalidPostResponse(error) }
            } catch (error: FeedApiException) {
                throw error
            } catch (error: SocketTimeoutException) {
                throw FeedApiException("The request timed out. Please try again.", FeedApiFailure.TIMEOUT, cause = error)
            } catch (error: IOException) {
                ensureActive()
                throw FeedApiException("Could not connect. Check your connection and try again.", FeedApiFailure.NETWORK, cause = error)
            }
        }

    private companion object {
        const val MAX_BYTES = 1024 * 1024
        val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS).retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()
    }
}

internal fun postAuthor(raw: JSONObject) = FeedAuthor(
        id = postObjectId(raw.getString("id")), firstName = raw.optString("first_name"), lastName = raw.optString("last_name"),
        profileImageUrl = raw.optString("profile_image_url").takeIf { it.startsWith("https://") }
    )

internal fun postCount(raw: JSONObject, key: String) = raw.optLong(key, 0).coerceAtLeast(0)
internal fun postObjectId(value: String): String = value.trim().also { require(OBJECT_ID.matches(it)) { "Invalid post or comment ID." } }
private val OBJECT_ID = Regex("[a-fA-F0-9]{24}")
internal fun invalidPostResponse(cause: Throwable? = null) = FeedApiException(
    "Could not read the server response. Please try again.", FeedApiFailure.INVALID_RESPONSE, cause = cause
)

private fun httpError(status: Int) = when (status) {
    401 -> FeedApiException("Your session has expired. Please sign in again.", FeedApiFailure.SESSION_EXPIRED, status)
    403 -> FeedApiException("You do not have permission for this action.", FeedApiFailure.ACCESS_DENIED, status)
    404 -> FeedApiException("This post or comment is no longer available.", FeedApiFailure.REQUEST_REJECTED, status)
    429 -> FeedApiException("Too many requests. Please wait a moment.", FeedApiFailure.RATE_LIMITED, status)
    in 500..599 -> FeedApiException("The server is busy. Please try again.", FeedApiFailure.SERVER, status)
    else -> FeedApiException("Could not complete the request. Please try again.", FeedApiFailure.REQUEST_REJECTED, status)
}
