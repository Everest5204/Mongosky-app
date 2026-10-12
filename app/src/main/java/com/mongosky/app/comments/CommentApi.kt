package com.mongosky.app.comments

import com.mongosky.app.network.PostHttp
import com.mongosky.app.network.PostTransport
import com.mongosky.app.network.invalidPostResponse
import com.mongosky.app.post.FeedAuthor
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

interface CommentActions {
    suspend fun comments(token: String, postId: String, parentId: String? = null, cursor: String? = null): CommentPage
    suspend fun createComment(token: String, postId: String, text: String, parentId: String? = null): CreatedComment
    suspend fun editComment(token: String, commentId: String, text: String): PostComment
    suspend fun deleteComment(token: String, commentId: String): DeletedComment
    suspend fun loveComment(token: String, commentId: String): CommentLove
}

/** Same protected Go/MongoDB endpoints as the web; parsing runs off the UI thread. */
class CommentApi internal constructor(private val transport: PostTransport) : CommentActions {
    constructor() : this(PostHttp())

    override suspend fun comments(token: String, postId: String, parentId: String?, cursor: String?): CommentPage = withContext(Dispatchers.IO) {
        val post = checkedId(postId)
        val parent = parentId?.let(::checkedId)
        val query = mutableMapOf("limit" to "10")
        if (parent == null) query["post_id"] = post else query["parent_id"] = parent
        if (!cursor.isNullOrBlank()) { require(cursor.length <= 2048); query["cursor"] = cursor }
        val raw = transport.request(token, "GET", if (parent == null) "/api/comments" else "/api/comments/replies", query)
        read {
            val rows = raw.getJSONArray("comments")
            if (rows.length() > 30) throw invalidPostResponse()
            val comments = (0 until rows.length()).map { parseComment(rows.getJSONObject(it)) }
            if (comments.any { it.postId != post || it.parentId != parent }) throw invalidPostResponse()
            val more = raw.getBoolean("has_more")
            val next = raw.optString("next_cursor").takeIf { it.isNotBlank() && it != "null" }
            if (more && (next == null || next == cursor || next.length > 2048)) throw invalidPostResponse()
            CommentPage(comments.distinctBy { it.id }, if (more) next else null, more)
        }
    }

    override suspend fun createComment(token: String, postId: String, text: String, parentId: String?): CreatedComment = withContext(Dispatchers.IO) {
        val post = checkedId(postId); val parent = parentId?.let(::checkedId)
        val body = JSONObject().put("post_id", post).put("text", CommentPolicy.requestText(text))
        parent?.let { body.put("parent_id", it) }
        val raw = transport.request(token, "POST", "/api/comments", body = body)
        read {
            val comment = parseComment(raw.getJSONObject("comment"))
            if (comment.postId != post || comment.parentId != parent || comment.isDeleted) throw invalidPostResponse()
            CreatedComment(comment, count(raw, "comments_count"))
        }
    }

    override suspend fun editComment(token: String, commentId: String, text: String): PostComment = withContext(Dispatchers.IO) {
        val id = checkedId(commentId)
        val raw = transport.request(token, "PATCH", "/api/comments/$id", body = JSONObject().put("text", CommentPolicy.requestText(text)))
        read { parseComment(raw.getJSONObject("comment")).also { if (it.id != id || it.isDeleted) throw invalidPostResponse() } }
    }

    override suspend fun deleteComment(token: String, commentId: String): DeletedComment = withContext(Dispatchers.IO) {
        val raw = transport.request(token, "DELETE", "/api/comments/${checkedId(commentId)}")
        read { DeletedComment(raw.getBoolean("deleted"), count(raw, "comments_count")) }
    }

    override suspend fun loveComment(token: String, commentId: String): CommentLove = withContext(Dispatchers.IO) {
        val raw = transport.request(token, "POST", "/api/comments/${checkedId(commentId)}/love", body = JSONObject())
        read { CommentLove(raw.getBoolean("loved"), count(raw, "loves_count")) }
    }

    private fun parseComment(raw: JSONObject): PostComment {
        val user = raw.getJSONObject("user")
        val userId = id(raw.get("user_id"))
        val authorId = id(user.opt("id") ?: user.opt("_id") ?: userId)
        if (authorId != userId) throw invalidPostResponse()
        val created = Instant.parse(raw.getString("created_at"))
        return PostComment(id(raw.opt("id") ?: raw.get("_id")), id(raw.get("post_id")), userId,
            FeedAuthor(authorId, user.optString("first_name"), user.optString("last_name"),
                user.optString("profile_image_url").takeIf { it.startsWith("https://") }),
            raw.getString("text"), created,
            raw.opt("parent_id")?.takeUnless { it == JSONObject.NULL || it == "" }?.let(::id),
            count(raw, "loves_count"), count(raw, "replies_count"), raw.optBoolean("is_loved_by_me"),
            raw.optBoolean("can_modify"), raw.optBoolean("is_deleted"),
            raw.optString("updated_at").takeIf { it.isNotBlank() }?.let(Instant::parse) ?: created)
    }

    private fun checkedId(value: String) = value.trim().lowercase().also { require(CommentPolicy.validId(it)) { "This comment or post is unavailable." } }
    private fun id(value: Any): String = checkedId(if (value is JSONObject) value.optString("\$oid").ifBlank { value.optString("hex") } else value.toString())
    private fun count(raw: JSONObject, key: String): Long {
        val value = raw.get(key) as? Number ?: throw invalidPostResponse()
        val number = value.toLong()
        if (number < 0 || value.toDouble() != number.toDouble()) throw invalidPostResponse()
        return number
    }
    private inline fun <T> read(block: () -> T): T = try { block() }
        catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) { throw invalidPostResponse(error) }
}
