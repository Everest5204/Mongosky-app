package com.mongosky.app.reels

import com.mongosky.app.mediapost.MediaPost
import com.mongosky.app.mediapost.MediaPostType
import com.mongosky.app.post.FeedAuthor
import com.mongosky.app.post.FeedMedia
import com.mongosky.app.post.FeedMediaType
import com.mongosky.app.post.HomePost
import com.mongosky.app.reactions.PostReaction
import java.time.Instant

data class Reel(
    val id: String,
    val author: FeedAuthor,
    val videoUrl: String,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt,
    val caption: String = "",
    val posterUrl: String? = null,
    val username: String = "",
    val likesCount: Long = 0,
    val commentsCount: Long = 0,
    val reaction: PostReaction? = null
) {
    internal fun asPost() = HomePost.Media(MediaPost(
        id, author.id, author, createdAt, updatedAt, MediaPostType.VIDEO, caption,
        listOf(FeedMedia(videoUrl, FeedMediaType.VIDEO)), likesCount, commentsCount
    ))
}

data class ReelsPage(val reels: List<Reel>, val nextCursor: String? = null, val hasMore: Boolean = false)

data class ReelsState(
    val reels: List<Reel> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val syncing: Boolean = false,
    val initialized: Boolean = false,
    val hasMore: Boolean = false,
    val activeId: String? = null,
    val error: String? = null,
    val sessionExpired: Boolean = false,
    val verifiedAuthors: Set<String> = emptySet(),
    val pageVersion: Int = 0
)

internal data class ReelLoveState(
    val count: Long, val reaction: PostReaction?, val saving: Boolean = false,
    val uncertain: Boolean = false, val error: String? = null
)
