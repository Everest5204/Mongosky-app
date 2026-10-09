package com.mongosky.app.home

import com.mongosky.app.mediapost.MediaPost
import com.mongosky.app.mediapost.MediaPostType
import com.mongosky.app.network.invalidPostResponse
import com.mongosky.app.post.*
import com.mongosky.app.textpost.TextPost
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
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
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Real web endpoints only. Parsing, multipart file reads and HTTP work run off Main. */
internal class HomeFeedApi(
    private val client: OkHttpClient = HomeHttp.client,
    baseUrl: String = "https://api.mongosky.com/"
) : HomeFeedActions {
    private val base = baseUrl.toHttpUrl()
    private val decoder = FeedApi()

    override suspend fun follow(token: String, userId: String) = withContext(Dispatchers.IO) {
        requireId(userId)
        val raw = request(token, "PUT", url("api", "users", userId, "follow"), jsonBody(JSONObject()))
        if (raw.opt("is_following") != true) throw invalidPostResponse()
        Unit
    }

    override suspend fun relationship(token: String, userId: String): Boolean = withContext(Dispatchers.IO) {
        requireId(userId)
        val profile = request(token, "GET", url("api", "users", userId, "profile"))
            .optJSONObject("profile") ?: throw invalidPostResponse()
        if (profile.optString("id") != userId) throw invalidPostResponse()
        (profile.opt("is_following") as? Boolean) ?: throw invalidPostResponse()
    }

    override suspend fun verified(token: String, ids: List<String>): Set<String> = withContext(Dispatchers.IO) {
        val valid = ids.distinct().filter(HomeFeedPolicy::validId).take(200)
        if (valid.isEmpty()) return@withContext emptySet()
        val raw = request(token, "POST", url("api", "verified-badges", "resolve"),
            jsonBody(JSONObject().put("user_ids", JSONArray(valid))))
        val rows = raw.optJSONArray("badges") ?: throw invalidPostResponse()
        buildSet {
            for (i in 0 until rows.length()) {
                ensureActive()
                val badge = rows.optJSONObject(i) ?: continue
                val id = badge.optString("user_id")
                if (id in valid && badge.optString("badge_type") == "verified") add(id)
            }
        }
    }

    override suspend fun editable(token: String, post: HomePost): HomePost = withContext(Dispatchers.IO) {
        require(post.editable)
        decodeOwned(request(token, "GET", editUrl(post)), post)
    }

    override suspend fun edit(token: String, request: HomeEditRequest): HomePost = withContext(Dispatchers.IO) {
        val draft = request.draft
        require(draft.post.editable && draft.length <= draft.limit)
        val body = if (draft.post is HomePost.Text) {
            require(draft.text.isNotBlank())
            jsonBody(JSONObject().put("text", draft.text.trim()).put("caption", draft.text.trim())
                .put("text_background", JSONObject().put("kind", draft.background.kind.name.lowercase())
                    .put("value", draft.background.value))
                .put("text_color", draft.textColor).put("text_style", draft.textStyle.name.lowercase())
                .put("expected_updated_at", draft.post.updatedAt.toString()))
        } else {
            require(draft.keptMedia.size + request.newFiles.size in 1..10)
            MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("caption", draft.text.trim())
                .addFormDataPart("expected_updated_at", draft.post.updatedAt.toString())
                .apply {
                    draft.keptMedia.forEach { addFormDataPart("keep_media_key", it.id?.takeIf(String::isNotBlank) ?: it.url) }
                    request.newFiles.forEach { file ->
                        val source = File(file.localPath)
                        require(source.isFile && source.length() == file.sizeBytes)
                        addFormDataPart("media", file.name.replace('"', '_').replace('\n', '_').replace('\r', '_'),
                            source.asRequestBody(file.mimeType.toMediaType()))
                    }
                }.build()
        }
        decodeOwned(request(token, "PATCH", editUrl(draft.post), body), draft.post)
    }

    override suspend fun delete(token: String, postId: String) = withContext(Dispatchers.IO) {
        requireId(postId)
        val raw = request(token, "DELETE", url("api", "posts", "delete", postId))
        if (raw.opt("deleted") != true || raw.optString("post_id") != postId) throw invalidPostResponse()
        Unit
    }

    private suspend fun decodeOwned(raw: JSONObject, original: HomePost): HomePost {
        val row = raw.optJSONObject("post") ?: throw invalidPostResponse()
        if (row.optString("id") != original.id || row.optString("user_id") != original.author.id) {
            throw invalidPostResponse()
        }
        // The protected editor verifies ownership; its DTO omits feed relation flags.
        row.put("is_self", true).put("is_following", false)
        val page = decoder.parsePage(JSONObject().put("posts", JSONArray().put(row))
            .put("has_more", false).toString(), original.source, null)
        val post = page.posts.singleOrNull() ?: throw invalidPostResponse()
        return if (post.source == FeedSource.TEXT) {
            if (post.type != FeedPostType.TEXT) throw invalidPostResponse()
            HomePost.Text(TextPost(post.id, post.userId, post.author, post.createdAt, post.updatedAt,
                post.text, post.caption, post.textBackground, post.textColor, post.textStyle,
                post.likesCount, post.commentsCount, post.sharesCount, isSelf = true))
        } else {
            val type = when (post.type) {
                FeedPostType.IMAGE -> MediaPostType.IMAGE
                FeedPostType.VIDEO -> MediaPostType.VIDEO
                else -> throw invalidPostResponse()
            }
            HomePost.Media(MediaPost(post.id, post.userId, post.author, post.createdAt, post.updatedAt,
                type, post.caption, post.media, post.likesCount, post.commentsCount, post.sharesCount, isSelf = true))
        }
    }

    private fun editUrl(post: HomePost): HttpUrl {
        requireId(post.id)
        return if (post is HomePost.Text) url("api", "text-posts", "edit", post.id)
        else url("api", "posts", "media", "edit", post.id)
    }
    private fun url(vararg parts: String) = base.newBuilder().apply { parts.forEach(::addPathSegment) }.build()
    private fun requireId(id: String) { require(HomeFeedPolicy.validId(id)) { "This post or profile is unavailable." } }
    private fun jsonBody(json: JSONObject) = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

    private suspend fun request(token: String, method: String, url: HttpUrl, body: RequestBody? = null): JSONObject {
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

internal object HomeHttp {
    const val MAX_JSON_BYTES = 1_048_576
    val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS).retryOnConnectionFailure(false)
        .followRedirects(false).followSslRedirects(false).build()
    fun error(status: Int) = when (status) {
        401 -> FeedApiException("Your session has expired. Please sign in again.", FeedApiFailure.SESSION_EXPIRED, status)
        403 -> FeedApiException("You do not have permission for this action.", FeedApiFailure.ACCESS_DENIED, status)
        404 -> FeedApiException("This post is no longer available.", FeedApiFailure.REQUEST_REJECTED, status)
        409 -> FeedApiException("This post changed in another session. Reload it before saving.", FeedApiFailure.REQUEST_REJECTED, status)
        429 -> FeedApiException("Please wait a moment before trying again.", FeedApiFailure.RATE_LIMITED, status)
        in 500..599 -> FeedApiException("Could not complete the request. Please try again.", FeedApiFailure.SERVER, status)
        else -> FeedApiException("Could not complete the request. Please try again.", FeedApiFailure.REQUEST_REJECTED, status)
    }
}
