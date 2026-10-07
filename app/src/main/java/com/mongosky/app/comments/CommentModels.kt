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
    val isDeleted: Boolean = false
)

data class CommentPage(
    val comments: List<PostComment>, val nextCursor: String? = null, val hasMore: Boolean = false
)

data class CreatedComment(val comment: PostComment, val commentsCount: Long)
data class DeletedComment(val deleted: Boolean, val commentsCount: Long)
data class CommentLove(val loved: Boolean, val count: Long)

data class CommentThread(
    val parent: PostComment? = null,
    val comments: List<PostComment> = emptyList(),
    val cursor: String? = null,
    val seenCursors: Set<String> = emptySet(),
    val hasMore: Boolean = true,
    val initialized: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null
)

data class CommentsState(
    val postKey: String,
    val postId: String,
    val threads: List<CommentThread> = listOf(CommentThread()),
    val draft: String = "",
    val editing: PostComment? = null,
    val sending: Boolean = false,
    val busyIds: Set<String> = emptySet(),
    val error: String? = null
) {
    val current: CommentThread get() = threads.last()
}
