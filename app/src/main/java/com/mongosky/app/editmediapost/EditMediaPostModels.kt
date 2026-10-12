package com.mongosky.app.editmediapost

import com.mongosky.app.editpost.EditPostState
import com.mongosky.app.home.content
import com.mongosky.app.home.editable
import com.mongosky.app.mediapost.*
import com.mongosky.app.post.*
import com.mongosky.app.textpost.TextPostLimits
import java.time.Instant

/** Caption and retained/selected media metadata, scoped to one media post. */
internal data class EditMediaPostState(
    override val post: HomePost.Media,
    override val loading: Boolean = true,
    override val saving: Boolean = false,
    override val selecting: Boolean = false,
    override val text: String = post.content,
    override val keptMedia: List<FeedMedia> = post.post.media,
    override val selectedMedia: List<SelectedPostMedia> = emptyList(),
    override val error: String? = null,
    override val needsReload: Boolean = false,
    override val discardRequested: Boolean = false,
    override val reloadRequested: Boolean = false,
    override val verified: Boolean = false
) : EditPostState {
    override val limit get() = EditMediaPostPolicy.MAX_CAPTION
    override val mediaKind get() = keptMedia.firstOrNull()?.let {
        when (it.type) { FeedMediaType.IMAGE -> PostMediaKind.IMAGE; FeedMediaType.VIDEO -> PostMediaKind.VIDEO; else -> null }
    } ?: selectedMedia.firstOrNull()?.kind
    override val canAddMedia get() = editable &&
        (mediaCount == 0 || mediaKind == PostMediaKind.IMAGE && mediaCount < MediaPostUploadLimits.MAX_IMAGES)
    override val dirty get() = text.trim() != post.content.trim() || selectedMedia.isNotEmpty() ||
        keptMedia.map(EditMediaPostPolicy::mediaKey) != post.post.media.map(EditMediaPostPolicy::mediaKey)
    override val valid get() = !busy && !needsReload && !discardRequested && post.editable && dirty &&
        post.post.updatedAt != Instant.EPOCH && TextPostLimits.length(text.trim()) <= limit &&
        EditMediaPostPolicy.mediaError(keptMedia, selectedMedia) == null
}

internal data class EditMediaPostRequest(val draft: EditMediaPostState, val newFiles: List<PreparedPostMedia> = emptyList())

internal object EditMediaPostPolicy {
    const val MAX_CAPTION = 2_200
    private val imageMimes = setOf("image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif")
    private val videoMimes = setOf("video/mp4", "video/quicktime", "video/webm", "video/x-matroska")
    fun mediaKey(media: FeedMedia) = media.id?.trim()?.takeIf(String::isNotEmpty) ?: media.url.trim()
    fun limitCaption(text: String, max: Int = MAX_CAPTION): String = if (TextPostLimits.length(text) <= max) text
        else text.substring(0, text.offsetByCodePoints(0, max))

    fun mediaError(kept: List<FeedMedia>, selected: List<SelectedPostMedia>): String? {
        if (kept.isEmpty() && selected.isEmpty()) return "A media post needs at least one image or video."
        if (kept.any { it.url.isBlank() || it.type !in setOf(FeedMediaType.IMAGE, FeedMediaType.VIDEO) })
            return "This media is unavailable. Reload the post."
        if (kept.map(::mediaKey).distinct().size != kept.size || selected.map { it.id }.distinct().size != selected.size)
            return "The same media cannot be added twice."
        val kinds = kept.map { if (it.type == FeedMediaType.VIDEO) PostMediaKind.VIDEO else PostMediaKind.IMAGE } + selected.map { it.kind }
        if (kinds.distinct().size != 1) return "Remove the current media before changing its type."
        if (kinds.first() == PostMediaKind.VIDEO && kinds.size > MediaPostUploadLimits.MAX_VIDEOS)
            return "A video post can contain only one video."
        if (kinds.size > MediaPostUploadLimits.MAX_IMAGES) return "A post can contain up to 10 images."
        for (item in selected) {
            val allowed = if (item.kind == PostMediaKind.IMAGE) imageMimes else videoMimes
            if (item.mimeType.lowercase() !in allowed) return "Only JPG, PNG, WEBP, GIF, MP4, MOV, WEBM or MKV files are allowed."
            if (item.uri.isBlank()) return "This media is unavailable. Select it again."
            if (item.sizeBytes != null && item.sizeBytes <= 0L) return "Media file is empty."
            if (item.sizeBytes != null && item.sizeBytes > item.kind.maxFileBytes) return if (item.kind == PostMediaKind.IMAGE)
                "Each image must be 15 MB or smaller." else "Video must be 200 MB or smaller."
        }
        return null
    }
}
