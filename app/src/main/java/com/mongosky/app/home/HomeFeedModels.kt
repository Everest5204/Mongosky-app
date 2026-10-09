package com.mongosky.app.home

import androidx.compose.runtime.Immutable
import com.mongosky.app.mediapost.MediaPostType
import com.mongosky.app.mediapost.PreparedPostMedia
import com.mongosky.app.mediapost.SelectedPostMedia
import com.mongosky.app.post.FeedMedia
import com.mongosky.app.post.FeedTextBackground
import com.mongosky.app.post.FeedTextStyle
import com.mongosky.app.post.HomePost
import com.mongosky.app.textpost.TextPostLimits

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
        HomeMenuAction.HIDE, HomeMenuAction.REPORT))
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

internal data class HomeEditState(
    val post: HomePost,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val selecting: Boolean = false,
    val text: String = post.content,
    val background: FeedTextBackground = (post as? HomePost.Text)?.post?.textBackground ?: FeedTextBackground(),
    val textColor: String = (post as? HomePost.Text)?.post?.textColor ?: "#111827",
    val textStyle: FeedTextStyle = (post as? HomePost.Text)?.post?.textStyle ?: FeedTextStyle.BOLD_CENTER,
    val keptMedia: List<FeedMedia> = post.photos,
    val selectedMedia: List<SelectedPostMedia> = emptyList(),
    val error: String? = null,
    val needsReload: Boolean = false
) {
    val busy get() = loading || saving || selecting
    val length get() = TextPostLimits.length(text)
    val limit get() = if (post is HomePost.Text) 650 else 2_200
    val valid get() = !busy && !needsReload && length <= limit &&
        (if (post is HomePost.Text) text.isNotBlank() else keptMedia.size + selectedMedia.size in 1..10)
}

internal data class HomeDeleteState(val post: HomePost, val busy: Boolean = false,
    val error: String? = null, val uncertain: Boolean = false)

internal data class HomeEditRequest(
    val draft: HomeEditState,
    val newFiles: List<PreparedPostMedia> = emptyList()
)

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
