package com.mongosky.app.domain.model.feed

import com.mongosky.app.domain.model.FeedAuthor
import java.time.Instant

enum class PostReaction(val wireName: String, val label: String, val emoji: String) {
    LOVE("love", "Love", "❤️"), HAHA("haha", "Haha", "😆"),
    WOW("wow", "Wow", "😮"), SAD("sad", "Sad", "😢"), ANGRY("angry", "Angry", "😡");

    companion object {
        fun fromWire(value: String?): PostReaction? = entries.firstOrNull { it.wireName == value }
    }
}

data class ReactionSummary(
    val total: Long = 0,
    val counts: Map<PostReaction, Long> = emptyMap(),
    val topReactions: List<PostReaction> = emptyList(),
    val currentReaction: PostReaction? = null,
    val firstReactor: FeedAuthor? = null
) {
    fun withReaction(next: PostReaction?): ReactionSummary {
        val updated = counts.toMutableMap()
        currentReaction?.let { updated[it] = ((updated[it] ?: 0) - 1).coerceAtLeast(0) }
        next?.let { updated[it] = (updated[it] ?: 0) + 1 }
        val delta = (if (next == null) 0 else 1) - (if (currentReaction == null) 0 else 1)
        return copy(
            total = (total + delta).coerceAtLeast(0), counts = updated,
            currentReaction = next,
            topReactions = PostReaction.entries.filter { (updated[it] ?: 0) > 0 }
                .sortedByDescending { updated[it] ?: 0 }.take(2),
            firstReactor = null
        )
    }
}

data class ReactionPerson(val id: String, val reaction: PostReaction, val author: FeedAuthor)

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
