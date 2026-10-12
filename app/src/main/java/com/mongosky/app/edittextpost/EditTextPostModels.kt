package com.mongosky.app.edittextpost

import com.mongosky.app.editpost.EditPostState
import com.mongosky.app.home.editable
import com.mongosky.app.post.*
import com.mongosky.app.textpost.TextPostLimits
import java.time.Instant

/** Text-only baseline and draft. Media upload state belongs to EditMediaPostState. */
internal data class EditTextPostState(
    override val post: HomePost.Text,
    override val loading: Boolean = true,
    override val saving: Boolean = false,
    override val selecting: Boolean = false,
    override val text: String = post.post.displayText,
    override val background: FeedTextBackground = post.post.textBackground,
    override val textColor: String = post.post.textColor,
    override val textStyle: FeedTextStyle = post.post.textStyle,
    override val error: String? = null,
    override val needsReload: Boolean = false,
    override val discardRequested: Boolean = false,
    override val reloadRequested: Boolean = false,
    override val verified: Boolean = false
) : EditPostState {
    override val limit get() = TextPostLimits.MAX_CHARACTERS
    override val dirty get() = text.trim() != post.post.displayText.trim() ||
        background != post.post.textBackground || textColor != post.post.textColor || textStyle != post.post.textStyle
    override val valid get() = !busy && !needsReload && !discardRequested && post.editable && dirty &&
        post.post.updatedAt != Instant.EPOCH && TextPostLimits.length(text.trim()) <= limit && text.isNotBlank()
}

internal data class EditTextPostRequest(val draft: EditTextPostState)

internal object EditTextPostPolicy {
    fun limitText(text: String, max: Int = TextPostLimits.MAX_CHARACTERS): String = if (TextPostLimits.length(text) <= max) text
        else text.substring(0, text.offsetByCodePoints(0, max))
}
