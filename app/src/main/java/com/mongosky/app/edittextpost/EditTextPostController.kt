package com.mongosky.app.edittextpost

import com.mongosky.app.editpost.*
import com.mongosky.app.post.*
import com.mongosky.app.textpost.TextPostPreset

/** Owns text/background/style edits and text-only save; never opens a media picker. */
internal class EditTextPostController(environment: EditPostEnvironment) : EditPostSessionController(environment) {
    override val state get() = super.state as? EditTextPostState
    override fun accepts(post: HomePost) = post is HomePost.Text
    fun text(value: String) = change { (it as EditTextPostState).copy(text = EditTextPostPolicy.limitText(value)) }
    fun preset(value: TextPostPreset) = change { (it as EditTextPostState).copy(background = value.background, textColor = value.textColor) }
    fun style(value: FeedTextStyle) = change { (it as EditTextPostState).copy(textStyle = value) }
    fun save() = submit()
}
