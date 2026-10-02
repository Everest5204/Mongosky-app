package com.mongosky.app.domain.model.textpost

import com.mongosky.app.domain.model.FeedAuthor
import com.mongosky.app.domain.model.FeedTextBackground
import com.mongosky.app.domain.model.FeedTextStyle
import java.time.Instant

/**
 * A text post from /api/text-posts/feed.
 * Text content and styling stay separate from media post content.
 * User details reuse the shared author model.
 */
data class TextPost(
    val id: String,
    val userId: String,
    val author: FeedAuthor,
    val createdAt: Instant,
    val updatedAt: Instant,
    val text: String = "",
    val caption: String = "",
    val textBackground: FeedTextBackground = FeedTextBackground(),
    val textColor: String = "#111827",
    val textStyle: FeedTextStyle = FeedTextStyle.BOLD_CENTER,
    val likesCount: Long = 0L,
    val commentsCount: Long = 0L,
    val sharesCount: Long = 0L,
    val isSelf: Boolean = false,
    val isFollowing: Boolean = false
) {
    val displayText: String
        get() = if (text.isNotBlank()) text else caption
}

/**
 * One page of text posts. Its cursor belongs only to the text feed.
 * An absent or empty server cursor is represented as null.
 */
data class TextFeedPage(
    val posts: List<TextPost>,
    val nextCursor: String? = null,
    val hasMore: Boolean = false
)
