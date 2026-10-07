package com.mongosky.app.comments

import com.mongosky.app.network.PostHttp
import com.mongosky.app.network.PostTransport
import com.mongosky.app.network.invalidPostResponse
import com.mongosky.app.network.postAuthor
import com.mongosky.app.network.postCount
import com.mongosky.app.network.postObjectId
import java.time.Instant
import org.json.JSONObject

interface CommentActions {
    suspend fun comments(token: String, postId: String, parentId: String? = null, cursor: String? = null): CommentPage
    suspend fun createComment(token: String, postId: String, text: String, parentId: String? = null): CreatedComment
    suspend fun editComment(token: String, commentId: String, text: String): PostComment
    suspend fun deleteComment(token: String, commentId: String): DeletedComment
    suspend fun loveComment(token: String, commentId: String): CommentLove
}

class CommentApi internal constructor(private val transport: PostTransport) : CommentActions {
    constructor() : this(PostHttp())

    override suspend fun comments(token: String, postId: String, parentId: String?, cursor: String?): CommentPage {
        val path = if (parentId == null) "/api/comments" else "/api/comments/replies"
        val query = mutableMapOf("limit" to "10")
        if (parentId == null) query["post_id"] = postObjectId(postId) else query["parent_id"] = postObjectId(parentId)
        if (!cursor.isNullOrBlank()) {
            require(cursor.length <= 2048)
            query["cursor"] = cursor
        }
        val result = transport.request(token, "GET", path, query)
        val rows = result.getJSONArray("comments")
        require(rows.length() <= 30)
        val comments = (0 until rows.length()).map { parseComment(rows.getJSONObject(it)) }
        if (comments.any { it.postId != postId || it.parentId != parentId }) throw invalidPostResponse()
        val hasMore = result.getBoolean("has_more")
        val next = result.optString("next_cursor").takeIf { it.isNotBlank() && it != "null" }
        if (hasMore && (next == null || next == cursor || next.length > 2048)) throw invalidPostResponse()
        return CommentPage(comments, if (hasMore) next else null, hasMore)
    }

    override suspend fun createComment(token: String, postId: String, text: String, parentId: String?): CreatedComment {
        val body = JSONObject().put("post_id", postObjectId(postId)).put("text", commentText(text))
        parentId?.let { body.put("parent_id", postObjectId(it)) }
        val result = transport.request(token, "POST", "/api/comments", body = body)
        val comment = parseComment(result.getJSONObject("comment"))
        if (comment.postId != postId || comment.parentId != parentId) throw invalidPostResponse()
        return CreatedComment(comment, postCount(result, "comments_count"))
    }

    override suspend fun editComment(token: String, commentId: String, text: String): PostComment =
        parseComment(transport.request(token, "PATCH", "/api/comments/${postObjectId(commentId)}",
            body = JSONObject().put("text", commentText(text))).getJSONObject("comment"))

    override suspend fun deleteComment(token: String, commentId: String): DeletedComment {
        val result = transport.request(token, "DELETE", "/api/comments/${postObjectId(commentId)}")
        return DeletedComment(result.getBoolean("deleted"), postCount(result, "comments_count"))
    }

    override suspend fun loveComment(token: String, commentId: String): CommentLove {
        val result = transport.request(token, "POST", "/api/comments/${postObjectId(commentId)}/love", body = JSONObject())
        return CommentLove(result.getBoolean("loved"), postCount(result, "loves_count"))
    }

    private fun parseComment(raw: JSONObject): PostComment = PostComment(
        id = postObjectId(raw.getString("id")), postId = postObjectId(raw.getString("post_id")), userId = postObjectId(raw.getString("user_id")),
        author = postAuthor(raw.getJSONObject("user")), text = raw.getString("text"),
        createdAt = Instant.parse(raw.getString("created_at")),
        parentId = raw.optString("parent_id").takeIf { it.isNotBlank() && it != "null" }?.let(::postObjectId),
        lovesCount = postCount(raw, "loves_count"), repliesCount = postCount(raw, "replies_count"),
        isLovedByMe = raw.optBoolean("is_loved_by_me"), canModify = raw.optBoolean("can_modify"),
        isDeleted = raw.optBoolean("is_deleted")
    )

    private fun commentText(value: String): String = value.trim().also {
        require(it.isNotBlank() && it.codePointCount(0, it.length) <= 2000) { "Write a comment of up to 2,000 characters." }
    }

}
