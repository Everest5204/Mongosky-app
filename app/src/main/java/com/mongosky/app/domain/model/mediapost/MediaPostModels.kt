package com.mongosky.app.domain.model.mediapost

import com.mongosky.app.domain.model.FeedAuthor
import com.mongosky.app.domain.model.FeedMedia
import java.time.Instant

enum class MediaPostType {
    IMAGE,
    VIDEO,
    PROFILE_PHOTO_UPDATE,
    COVER_PHOTO_UPDATE,
    UNKNOWN
}

/**
 * Media post content, separate from the text post model.
 * The API normalizes legacy single-media fields into the media list.
 * Home's media feed currently excludes videos; the model also supports
 * video posts for future profile and Reels integration.
 */
data class MediaPost(
    val id: String,
    val userId: String,
    val author: FeedAuthor,
    val createdAt: Instant,
    val updatedAt: Instant,
    val type: MediaPostType = MediaPostType.IMAGE,
    val caption: String = "",
    val media: List<FeedMedia> = emptyList(),
    val likesCount: Long = 0L,
    val commentsCount: Long = 0L,
    val sharesCount: Long = 0L,
    val isSelf: Boolean = false,
    val isFollowing: Boolean = false
)

/**
 * One page from /api/posts/feed.
 * Its cursor belongs only to the media feed.
 */
data class MediaFeedPage(
    val posts: List<MediaPost>,
    val nextCursor: String? = null,
    val hasMore: Boolean = false
)
