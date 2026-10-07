package com.mongosky.app.post

import java.time.Instant

enum class FeedSource {
    MEDIA,
    TEXT
}

enum class FeedPostType {
    TEXT,
    IMAGE,
    VIDEO,
    PROFILE_PHOTO_UPDATE,
    COVER_PHOTO_UPDATE,
    UNKNOWN
}

enum class FeedMediaType {
    IMAGE,
    VIDEO,
    UNKNOWN
}

enum class FeedTextBackgroundKind {
    PLAIN,
    SOLID,
    GRADIENT,
    IMAGE
}

enum class FeedTextStyle {
    BOLD_CENTER,
    NORMAL_CENTER,
    BOLD_LEFT,
    NORMAL_LEFT
}

data class FeedAuthor(
    val id: String,
    val firstName: String = "",
    val lastName: String = "",
    val profileImageUrl: String? = null
) {
    val displayName: String
        get() = "${firstName.trim()} ${lastName.trim()}".trim()
}

data class FeedMedia(
    val url: String,
    val type: FeedMediaType,
    val id: String? = null,
    val order: Int = 0
)

data class FeedTextBackground(
    val kind: FeedTextBackgroundKind = FeedTextBackgroundKind.PLAIN,
    val value: String = "#ffffff"
)

/**
 * A normalized post shared by the media and text feed APIs.
 * The API layer converts JSON field names and legacy media into this model.
 */
data class FeedPost(
    val id: String,
    val userId: String,
    val author: FeedAuthor,
    val source: FeedSource,
    val type: FeedPostType,
    val createdAt: Instant,
    val updatedAt: Instant,
    val caption: String = "",
    val text: String = "",
    val media: List<FeedMedia> = emptyList(),
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
        get() = if (type == FeedPostType.TEXT && text.isNotBlank()) {
            text
        } else {
            caption
        }
}

/**
 * One page from one feed source. Media and text keep separate cursors.
 * The API layer converts an empty next_cursor to null.
 */
data class FeedPage(
    val posts: List<FeedPost>,
    val nextCursor: String? = null,
    val hasMore: Boolean = false
)
