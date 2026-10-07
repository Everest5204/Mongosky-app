package com.mongosky.app.mediapost

import com.mongosky.app.post.FeedAuthor
import com.mongosky.app.post.FeedMedia
import java.time.Instant

object MediaPostUploadLimits {
    const val MAX_IMAGES = 10
    const val MAX_VIDEOS = 1
    const val MAX_IMAGE_BYTES = 15L * 1024L * 1024L
    const val MAX_VIDEO_BYTES = 200L * 1024L * 1024L
}

enum class PostMediaKind {
    IMAGE,
    VIDEO;

    val maxFileBytes: Long
        get() = when (this) {
            IMAGE -> MediaPostUploadLimits.MAX_IMAGE_BYTES
            VIDEO -> MediaPostUploadLimits.MAX_VIDEO_BYTES
        }
}

/** Metadata only. File contents stay outside the UI state. */
data class SelectedPostMedia(
    val id: String,
    val uri: String,
    val name: String,
    val mimeType: String,
    val kind: PostMediaKind,
    val sizeBytes: Long? = null
)

/** A file copied into app-owned storage for the upload. */
data class PreparedPostMedia(
    val id: String,
    val localPath: String,
    val name: String,
    val mimeType: String,
    val kind: PostMediaKind,
    val sizeBytes: Long
)

data class MediaPostDraft(
    val id: String,
    val caption: String,
    val media: List<SelectedPostMedia>
) {
    fun validationError(): String? {
        if (media.isEmpty()) {
            return "Select a photo or video first."
        }

        val kind = media.first().kind

        if (media.any { it.kind != kind }) {
            return "Photos and videos cannot be posted together."
        }

        if (kind == PostMediaKind.IMAGE &&
            media.size > MediaPostUploadLimits.MAX_IMAGES
        ) {
            return "You can select up to 10 photos."
        }

        if (kind == PostMediaKind.VIDEO &&
            media.size > MediaPostUploadLimits.MAX_VIDEOS
        ) {
            return "You can select only one video."
        }

        for (item in media) {
            if (item.uri.isBlank()) {
                return "This media is unavailable. Please select it again."
            }

            val size = item.sizeBytes ?: continue

            if (size <= 0L) {
                return "This file is empty. Please select another file."
            }

            if (size > item.kind.maxFileBytes) {
                return when (item.kind) {
                    PostMediaKind.IMAGE ->
                        "Each photo must be 15 MB or smaller."

                    PostMediaKind.VIDEO ->
                        "The video must be 200 MB or smaller."
                }
            }
        }

        return null
    }
}

/** Account ownership is checked before sending or retrying. */
data class PreparedMediaPost(
    val draftId: String,
    val ownerUserId: String,
    val caption: String,
    val media: List<PreparedPostMedia>
)

/** Measures request bytes sent, not server-side post completion. */
data class MediaPostUploadProgress(
    val bytesSent: Long = 0L,
    val totalBytes: Long = 0L
) {
    val fraction: Float?
        get() {
            if (totalBytes <= 0L) return null

            val sent = bytesSent.coerceIn(0L, totalBytes)

            return (
                    sent.toDouble() / totalBytes.toDouble()
                    ).toFloat()
        }

    val percent: Int?
        get() = fraction?.let {
            (it * 100f).toInt().coerceIn(0, 100)
        }
}

sealed interface MediaPostUploadState {
    val draft: MediaPostDraft?

    val isActive: Boolean
        get() = this is Preparing ||
                this is Uploading ||
                this is Publishing

    object Idle : MediaPostUploadState {
        override val draft: MediaPostDraft? = null
    }

    data class Preparing(
        override val draft: MediaPostDraft
    ) : MediaPostUploadState

    data class Uploading(
        override val draft: MediaPostDraft,
        val progress: MediaPostUploadProgress =
            MediaPostUploadProgress()
    ) : MediaPostUploadState

    data class Publishing(
        override val draft: MediaPostDraft
    ) : MediaPostUploadState

    data class Failed(
        override val draft: MediaPostDraft,
        val message: String,
        val canRetry: Boolean,
        val requiresSignIn: Boolean = false,
        // A lost response can leave the server result unknown.
        val outcomeUnknown: Boolean = false
    ) : MediaPostUploadState

    data class Succeeded(
        override val draft: MediaPostDraft,
        val post: MediaPost
    ) : MediaPostUploadState
}

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
