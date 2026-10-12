package com.mongosky.app.editpost

import com.mongosky.app.home.content
import com.mongosky.app.home.photos
import com.mongosky.app.edittextpost.EditTextPostState
import com.mongosky.app.edittextpost.EditTextPostPolicy
import com.mongosky.app.editmediapost.EditMediaPostState
import com.mongosky.app.editmediapost.EditMediaPostPolicy
import com.mongosky.app.mediapost.*
import com.mongosky.app.post.*
import com.mongosky.app.textpost.TextPostLimits

/** Read-only compatibility contract used by the existing Home/Profile entry points. */
internal interface EditPostState {
    val post: HomePost
    val loading: Boolean
    val saving: Boolean
    val selecting: Boolean
    val text: String
    val error: String?
    val needsReload: Boolean
    val discardRequested: Boolean
    val reloadRequested: Boolean
    val verified: Boolean
    val background: FeedTextBackground get() = FeedTextBackground()
    val textColor: String get() = "#111827"
    val textStyle: FeedTextStyle get() = FeedTextStyle.BOLD_CENTER
    val keptMedia: List<FeedMedia> get() = emptyList()
    val selectedMedia: List<SelectedPostMedia> get() = emptyList()
    val busy get() = loading || saving || selecting
    val editable get() = !saving && !selecting && !needsReload && !discardRequested
    val length get() = TextPostLimits.length(text)
    val limit: Int
    val mediaCount get() = keptMedia.size + selectedMedia.size
    val mediaKind: PostMediaKind? get() = null
    val canAddMedia get() = false
    val dirty: Boolean
    val valid: Boolean
}

/** Backward-compatible construction; feature state and validation live in their own sections. */
internal fun EditPostState(
    post: HomePost,
    loading: Boolean = true,
    saving: Boolean = false,
    selecting: Boolean = false,
    text: String = post.content,
    background: FeedTextBackground = (post as? HomePost.Text)?.post?.textBackground ?: FeedTextBackground(),
    textColor: String = (post as? HomePost.Text)?.post?.textColor ?: "#111827",
    textStyle: FeedTextStyle = (post as? HomePost.Text)?.post?.textStyle ?: FeedTextStyle.BOLD_CENTER,
    keptMedia: List<FeedMedia> = post.photos,
    selectedMedia: List<SelectedPostMedia> = emptyList(),
    error: String? = null,
    needsReload: Boolean = false,
    discardRequested: Boolean = false,
    reloadRequested: Boolean = false,
    verified: Boolean = false
): EditPostState = when (post) {
    is HomePost.Text -> EditTextPostState(post, loading, saving, selecting, text, background, textColor, textStyle,
        error, needsReload, discardRequested, reloadRequested, verified)
    is HomePost.Media -> EditMediaPostState(post, loading, saving, selecting, text, keptMedia, selectedMedia,
        error, needsReload, discardRequested, reloadRequested, verified)
}

internal fun EditPostState.copy(
    post: HomePost = this.post,
    loading: Boolean = this.loading,
    saving: Boolean = this.saving,
    selecting: Boolean = this.selecting,
    text: String = this.text,
    background: FeedTextBackground = this.background,
    textColor: String = this.textColor,
    textStyle: FeedTextStyle = this.textStyle,
    keptMedia: List<FeedMedia> = this.keptMedia,
    selectedMedia: List<SelectedPostMedia> = this.selectedMedia,
    error: String? = this.error,
    needsReload: Boolean = this.needsReload,
    discardRequested: Boolean = this.discardRequested,
    reloadRequested: Boolean = this.reloadRequested,
    verified: Boolean = this.verified
): EditPostState = EditPostState(post, loading, saving, selecting, text, background, textColor, textStyle, keptMedia, selectedMedia, error, needsReload, discardRequested, reloadRequested, verified)

internal data class EditPostRequest(val draft: EditPostState, val newFiles: List<PreparedPostMedia> = emptyList())

/** Compatibility delegates only; each section owns its validation. */
internal object EditPostPolicy {
    const val MAX_CAPTION = EditMediaPostPolicy.MAX_CAPTION
    fun mediaKey(media: FeedMedia) = EditMediaPostPolicy.mediaKey(media)
    fun limitText(text: String, max: Int) = EditTextPostPolicy.limitText(text, max)
    fun mediaError(kept: List<FeedMedia>, selected: List<SelectedPostMedia>) = EditMediaPostPolicy.mediaError(kept, selected)
}
