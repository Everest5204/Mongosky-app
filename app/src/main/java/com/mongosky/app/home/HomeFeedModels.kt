package com.mongosky.app.home

import androidx.compose.runtime.Immutable
import com.mongosky.app.mediapost.MediaPostType
import com.mongosky.app.post.FeedMedia
import com.mongosky.app.post.HomePost

@Immutable
internal data class HomeAuthorState(
    val verified: Boolean = false,
    val following: Boolean = false,
    val pending: Boolean = false,
    val confirmation: Boolean = false
)

internal enum class HomeMenuAction(val label: String) {
    DELETE("Delete post"), EDIT("Edit post"), PIN("Pin post"), SAVE("Save post"),
    COPY("Copy post link"), DOWNLOAD("Download post"), HIDE("Hide post"), REPORT("Report")
}

/** Ownership and editability follow the supplied web PostOptionsMenu. */
internal fun homeMenuActions(post: HomePost): List<HomeMenuAction> = buildList {
    if (post.isOwn) {
        add(HomeMenuAction.DELETE)
        if (post.editable) add(HomeMenuAction.EDIT)
        add(HomeMenuAction.PIN)
    }
    addAll(listOf(HomeMenuAction.SAVE, HomeMenuAction.COPY, HomeMenuAction.DOWNLOAD,
        HomeMenuAction.HIDE))
    if (!post.isOwn) add(HomeMenuAction.REPORT)
}

internal val HomePost.isOwn: Boolean get() = when (this) {
    is HomePost.Text -> post.isSelf
    is HomePost.Media -> post.isSelf
}
internal val HomePost.following: Boolean get() = when (this) {
    is HomePost.Text -> post.isFollowing
    is HomePost.Media -> post.isFollowing
}
internal val HomePost.editable: Boolean get() = isOwn && when (this) {
    is HomePost.Text -> true
    is HomePost.Media -> post.type == MediaPostType.IMAGE || post.type == MediaPostType.VIDEO
}
internal val HomePost.updateLabel: String get() = when (this) {
    is HomePost.Text -> "shared a post"
    is HomePost.Media -> when (post.type) {
        MediaPostType.PROFILE_PHOTO_UPDATE -> "updated profile picture"
        MediaPostType.COVER_PHOTO_UPDATE -> "updated cover photo"
        else -> "shared a post"
    }
}
internal val HomePost.content: String get() = when (this) {
    is HomePost.Text -> post.displayText
    is HomePost.Media -> if (post.type == MediaPostType.PROFILE_PHOTO_UPDATE ||
        post.type == MediaPostType.COVER_PHOTO_UPDATE) "" else post.caption
}
internal val HomePost.photos: List<FeedMedia> get() = (this as? HomePost.Media)?.post?.media.orEmpty()
internal val HomePost.updatedAt get() = when (this) {
    is HomePost.Text -> post.updatedAt
    is HomePost.Media -> post.updatedAt
}

internal typealias HomeEditState = com.mongosky.app.editpost.EditPostState
internal typealias HomeEditRequest = com.mongosky.app.editpost.EditPostRequest

internal data class HomeDeleteState(val post: HomePost, val busy: Boolean = false,
    val error: String? = null, val uncertain: Boolean = false)

internal interface HomeFeedActions {
    suspend fun follow(token: String, userId: String)
    suspend fun relationship(token: String, userId: String): Boolean? = null
    suspend fun verified(token: String, ids: List<String>): Set<String>
    suspend fun editable(token: String, post: HomePost): HomePost
    suspend fun edit(token: String, request: HomeEditRequest): HomePost
    suspend fun delete(token: String, postId: String)
}

internal object HomeFeedPolicy {
    const val BADGE_TTL_MILLIS = 60_000L
    const val POLL_MILLIS = 20_000L
    const val SETTLE_MILLIS = 300L
    const val FOLLOW_CONFIRM_MILLIS = 700L
    private val idPattern = Regex("[a-fA-F0-9]{24}")
    fun validId(value: String) = idPattern.matches(value) && value.any { it != '0' }
}
