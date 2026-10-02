package com.mongosky.app.data.remote.feed

import com.mongosky.app.data.remote.FeedApiException
import com.mongosky.app.data.remote.FeedApiFailure
import com.mongosky.app.domain.model.FeedAuthor
import com.mongosky.app.domain.model.feed.CommentLove
import com.mongosky.app.domain.model.feed.CommentPage
import com.mongosky.app.domain.model.feed.CreatedComment
import com.mongosky.app.domain.model.feed.DeletedComment
import com.mongosky.app.domain.model.feed.PostComment
import com.mongosky.app.domain.model.feed.PostReaction
import com.mongosky.app.domain.model.feed.ReactionPerson
import com.mongosky.app.domain.model.feed.ReactionSummary
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
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface PostActions {
    suspend fun summary(token: String, postId: String): ReactionSummary
    suspend fun react(token: String, postId: String, reaction: PostReaction?): ReactionSummary
    suspend fun reactionPeople(token: String, postId: String): List<ReactionPerson>
    suspend fun comments(token: String, postId: String, parentId: String? = null, cursor: String? = null): CommentPage
    suspend fun createComment(token: String, postId: String, text: String, parentId: String? = null): CreatedComment
    suspend fun editComment(token: String, commentId: String, text: String): PostComment
    suspend fun deleteComment(token: String, commentId: String): DeletedComment
    suspend fun loveComment(token: String, commentId: String): CommentLove
}

internal interface PostTransport {
    suspend fun request(token: String, method: String, path: String, query: Map<String, String> = emptyMap(), body: JSONObject? = null): JSONObject
}

class PostActionsApi internal constructor(private val transport: PostTransport) : PostActions {
    constructor() : this(PostHttp())

    override suspend fun summary(token: String, postId: String): ReactionSummary =
        parseSummary(transport.request(token, "GET", "/api/reactions/post", mapOf("post_id" to id(postId))))

    override suspend fun react(token: String, postId: String, reaction: PostReaction?): ReactionSummary {
        val post = id(postId)
        val result = if (reaction == null) {
            transport.request(token, "DELETE", "/api/reactions", mapOf("post_id" to post))
        } else {
            transport.request(token, "POST", "/api/reactions", body = JSONObject()
                .put("post_id", post).put("reaction_type", reaction.wireName))
        }
        return parseSummary(result.getJSONObject("summary"))
    }

    override suspend fun reactionPeople(token: String, postId: String): List<ReactionPerson> {
        val result = transport.request(token, "GET", "/api/reactions/users", mapOf("post_id" to id(postId), "type" to "all"))
        val rows = result.getJSONArray("items")
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            ReactionPerson(row.getString("id"), PostReaction.fromWire(row.getString("reaction_type"))
                ?: throw invalidResponse(), author(row.getJSONObject("user")))
        }
    }

    override suspend fun comments(token: String, postId: String, parentId: String?, cursor: String?): CommentPage {
        val path = if (parentId == null) "/api/comments" else "/api/comments/replies"
        val query = mutableMapOf("limit" to "10")
        if (parentId == null) query["post_id"] = id(postId) else query["parent_id"] = id(parentId)
        if (!cursor.isNullOrBlank()) {
            require(cursor.length <= 2048)
            query["cursor"] = cursor
        }
        val result = transport.request(token, "GET", path, query)
        val rows = result.getJSONArray("comments")
        require(rows.length() <= 30)
        val comments = (0 until rows.length()).map { parseComment(rows.getJSONObject(it)) }
        if (comments.any { it.postId != postId || it.parentId != parentId }) throw invalidResponse()
        val hasMore = result.getBoolean("has_more")
        val next = result.optString("next_cursor").takeIf { it.isNotBlank() && it != "null" }
        if (hasMore && (next == null || next == cursor || next.length > 2048)) throw invalidResponse()
        return CommentPage(comments, if (hasMore) next else null, hasMore)
    }

    override suspend fun createComment(token: String, postId: String, text: String, parentId: String?): CreatedComment {
        val body = JSONObject().put("post_id", id(postId)).put("text", commentText(text))
        parentId?.let { body.put("parent_id", id(it)) }
        val result = transport.request(token, "POST", "/api/comments", body = body)
        val comment = parseComment(result.getJSONObject("comment"))
        if (comment.postId != postId || comment.parentId != parentId) throw invalidResponse()
        return CreatedComment(comment, count(result, "comments_count"))
    }

    override suspend fun editComment(token: String, commentId: String, text: String): PostComment =
        parseComment(transport.request(token, "PATCH", "/api/comments/${id(commentId)}",
            body = JSONObject().put("text", commentText(text))).getJSONObject("comment"))

    override suspend fun deleteComment(token: String, commentId: String): DeletedComment {
        val result = transport.request(token, "DELETE", "/api/comments/${id(commentId)}")
        return DeletedComment(result.getBoolean("deleted"), count(result, "comments_count"))
    }

    override suspend fun loveComment(token: String, commentId: String): CommentLove {
        val result = transport.request(token, "POST", "/api/comments/${id(commentId)}/love", body = JSONObject())
        return CommentLove(result.getBoolean("loved"), count(result, "loves_count"))
    }

    private fun parseSummary(raw: JSONObject): ReactionSummary {
        val counts = raw.getJSONObject("counts")
        val mapped = PostReaction.entries.associateWith { count(counts, it.wireName) }
        val top = raw.optJSONArray("top_reactions")
        return ReactionSummary(
            total = count(raw, "total"), counts = mapped,
            topReactions = if (top != null) (0 until top.length()).mapNotNull { PostReaction.fromWire(top.optString(it)) }.take(2)
                else PostReaction.entries.filter { (mapped[it] ?: 0) > 0 }.sortedByDescending { mapped[it] ?: 0 }.take(2),
            currentReaction = PostReaction.fromWire(raw.optString("current_user_reaction")),
            firstReactor = raw.optJSONObject("first_reactor")?.let(::author)
        )
    }

    private fun parseComment(raw: JSONObject): PostComment = PostComment(
        id = id(raw.getString("id")), postId = id(raw.getString("post_id")), userId = id(raw.getString("user_id")),
        author = author(raw.getJSONObject("user")), text = raw.getString("text"),
        createdAt = Instant.parse(raw.getString("created_at")),
        parentId = raw.optString("parent_id").takeIf { it.isNotBlank() && it != "null" }?.let(::id),
        lovesCount = count(raw, "loves_count"), repliesCount = count(raw, "replies_count"),
        isLovedByMe = raw.optBoolean("is_loved_by_me"), canModify = raw.optBoolean("can_modify"),
        isDeleted = raw.optBoolean("is_deleted")
    )

    private fun author(raw: JSONObject) = FeedAuthor(
        id = id(raw.getString("id")), firstName = raw.optString("first_name"), lastName = raw.optString("last_name"),
        profileImageUrl = raw.optString("profile_image_url").takeIf { it.startsWith("https://") }
    )

    private fun count(raw: JSONObject, key: String) = raw.optLong(key, 0).coerceAtLeast(0)
    private fun id(value: String): String = value.trim().also { require(OBJECT_ID.matches(it)) { "Invalid post or comment ID." } }
    private fun commentText(value: String): String = value.trim().also {
        require(it.isNotBlank() && it.codePointCount(0, it.length) <= 2000) { "Write a comment of up to 2,000 characters." }
    }

    private companion object { val OBJECT_ID = Regex("[a-fA-F0-9]{24}") }
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
                                    val stream = response.body ?: throw invalidResponse()
                                    if (stream.contentLength() > MAX_BYTES) throw invalidResponse()
                                    val output = ByteArrayOutputStream()
                                    stream.byteStream().use { input ->
                                        val buffer = ByteArray(8192)
                                        while (continuation.isActive) {
                                            val n = input.read(buffer)
                                            if (n < 0) break
                                            if (output.size() + n > MAX_BYTES) throw invalidResponse()
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
                try { JSONObject(text) } catch (error: Exception) { throw invalidResponse(error) }
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

private fun invalidResponse(cause: Throwable? = null) = FeedApiException(
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
