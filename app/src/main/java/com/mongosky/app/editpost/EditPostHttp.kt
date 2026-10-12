package com.mongosky.app.editpost

import com.mongosky.app.home.*
import com.mongosky.app.network.invalidPostResponse
import com.mongosky.app.post.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Common cancellable HTTP transport and ownership verification, without text/media edit logic. */
internal class EditPostHttp(
    private val client: OkHttpClient,
    baseUrl: String
) {
    private val base = baseUrl.toHttpUrl()
    private val decoder = FeedApi()

    suspend fun decodeOwned(raw: JSONObject, original: HomePost): FeedPost {
        val row = raw.optJSONObject("post") ?: throw invalidPostResponse()
        val ownerId = when (original) { is HomePost.Text -> original.post.userId; is HomePost.Media -> original.post.userId }
        val version = try { Instant.parse(row.optString("updated_at")) } catch (_: Exception) { throw invalidPostResponse() }
        if (version == Instant.EPOCH || row.optString("id") != original.id ||
            row.optString("user_id") != ownerId || ownerId != original.author.id ||
            row.optJSONObject("user")?.optString("id")?.takeIf(String::isNotBlank)?.let { it != ownerId } == true) {
            throw invalidPostResponse()
        }
        // The protected editor verifies ownership; its DTO omits feed relation flags.
        row.put("is_self", true).put("is_following", false)
        val page = decoder.parsePage(JSONObject().put("posts", JSONArray().put(row))
            .put("has_more", false).toString(), original.source, null)
        val post = page.posts.singleOrNull() ?: throw invalidPostResponse()
        return post
    }

    fun url(vararg parts: String) = base.newBuilder().apply { parts.forEach(::addPathSegment) }.build()
    fun requireId(id: String) { require(HomeFeedPolicy.validId(id)) { "This post or profile is unavailable." } }
    fun jsonBody(json: JSONObject) = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

    suspend fun request(token: String, method: String, url: HttpUrl, body: RequestBody? = null): JSONObject {
        if (token.isBlank() || token.any { it <= ' ' || it >= '\u007f' }) {
            throw FeedApiException("Please sign in again.", FeedApiFailure.SESSION_EXPIRED)
        }
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token")
            .header("Accept", "application/json").header("Cache-Control", "no-store")
            .method(method, body).build()
        try {
            val text = suspendCancellableCoroutine<String> { continuation ->
                val call = client.newCall(request)
                if (body is MultipartBody) call.timeout().timeout(120, TimeUnit.SECONDS)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val text = response.use {
                                if (!it.isSuccessful) throw HomeHttp.error(it.code)
                                val body = it.body ?: throw invalidPostResponse()
                                if (body.contentLength() > HomeHttp.MAX_JSON_BYTES) throw invalidPostResponse()
                                body.byteStream().use { input ->
                                    val output = ByteArrayOutputStream(8_192)
                                    val buffer = ByteArray(8_192)
                                    while (true) {
                                        if (call.isCanceled()) throw IOException("Request cancelled")
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        if (output.size() + count > HomeHttp.MAX_JSON_BYTES) throw invalidPostResponse()
                                        output.write(buffer, 0, count)
                                    }
                                    output.toString("UTF-8")
                                }
                            }
                            if (continuation.isActive) continuation.resume(text)
                        } catch (error: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }
                    }
                })
            }
            currentCoroutineContext().ensureActive()
            return try { JSONObject(text) } catch (error: Exception) { throw invalidPostResponse(error) }
        } catch (error: FeedApiException) { throw error }
        catch (error: SocketTimeoutException) {
            throw FeedApiException("The request timed out. Please try again.", FeedApiFailure.TIMEOUT, cause = error)
        } catch (error: IOException) {
            currentCoroutineContext().ensureActive()
            throw FeedApiException("Please check your connection and try again.", FeedApiFailure.NETWORK, cause = error)
        }
    }
}
