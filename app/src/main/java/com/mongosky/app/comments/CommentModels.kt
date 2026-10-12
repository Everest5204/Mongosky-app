package com.mongosky.app.comments

import com.mongosky.app.post.FeedAuthor
import java.time.Instant

data class PostComment(
    val id: String,
    val postId: String,
    val userId: String,
    val author: FeedAuthor,
    val text: String,
    val createdAt: Instant,
    val parentId: String? = null,
    val lovesCount: Long = 0,
    val repliesCount: Long = 0,
    val isLovedByMe: Boolean = false,
    val canModify: Boolean = false,
    val isDeleted: Boolean = false,
    val updatedAt: Instant = createdAt
)

data class CommentPage(val comments: List<PostComment>, val nextCursor: String? = null, val hasMore: Boolean = false)
data class CreatedComment(val comment: PostComment, val commentsCount: Long)
data class DeletedComment(val deleted: Boolean, val commentsCount: Long)
data class CommentLove(val loved: Boolean, val count: Long)

data class CommentThread(
    val comments: List<PostComment> = emptyList(),
    val cursor: String? = null,
    val seenCursors: Set<String> = emptySet(),
    val headIds: List<String> = emptyList(),
    val hasMore: Boolean = true,
    val initialized: Boolean = false,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null
)

data class CommentsState(
    val postKey: String,
    val postId: String,
    val root: CommentThread = CommentThread(),
    val replies: Map<String, CommentThread> = emptyMap(),
    val expandedIds: Set<String> = emptySet(),
    val draft: String = "",
    val editing: PostComment? = null,
    val replying: PostComment? = null,
    val inlineDraft: String = "",
    val sending: Boolean = false,
    val sendingInline: Boolean = false,
    val busyIds: Set<String> = emptySet(),
    val menuId: String? = null,
    val verifiedAuthors: Set<String> = emptySet(),
    val error: String? = null,
    val inlineError: String? = null,
    val unconfirmedCreate: Boolean = false
) {
    val inlineTargetId: String? get() = editing?.id ?: replying?.id
    fun find(id: String): PostComment? = root.comments.firstOrNull { it.id == id }
        ?: replies.values.firstNotNullOfOrNull { thread -> thread.comments.firstOrNull { it.id == id } }
}
